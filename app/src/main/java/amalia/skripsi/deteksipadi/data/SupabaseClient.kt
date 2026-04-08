package amalia.skripsi.deteksipadi.data

import amalia.skripsi.deteksipadi.ml.DetectionResult
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable

val supabase = createSupabaseClient(
    supabaseUrl = "https://rhlixmoadeexgvrbxkmo.supabase.co",
    supabaseKey = "sb_publishable_dV8oRP92tnXhqDantHWkkg_RTgO3V4P"
) {
    install(Postgrest)
    install(Storage)
    install(Auth)
    install(Realtime)
}

// DTO Murni untuk membaca data dari database (Ditambah lat & lon dari Computed Field)
@Serializable
data class LaporanDto(
    val id: String,
    val petani_id: String? = null,
    val foto_url: String = "",
    val label_ai: String = "Unknown",
    val confidence: Float = 0f,
    val status: String = "menunggu",
    val prioritas: String? = null,
    val termasuk_cluster: Boolean = false,
    val alamat_lengkap: String? = null,
    val created_at: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

// DTO Khusus untuk Insert (PostgREST butuh WKT String untuk tipe PostGIS)
@Serializable
data class LaporanInsertDto(
    val petani_id: String,
    val foto_url: String,
    val label_ai: String,
    val confidence: Float,
    val lokasi: String, // Format WKT: SRID=4326;POINT(lon lat)
    val alamat_lengkap: String,
    val kecamatan_id: String?
)

suspend fun fetchActiveLaporan(): List<LaporanDto> {
    return try {
        supabase.from("laporan")
            .select(columns = Columns.raw("id, petani_id, foto_url, label_ai, confidence, status, prioritas, termasuk_cluster, alamat_lengkap, created_at, lat, lon")) {
                filter {
                    neq("status", "ditolak")
                }
            }.decodeList<LaporanDto>()
    } catch (e: Exception) {
        android.util.Log.e("PETA_DEBUG", "Gagal Fetch Peta: ${e.message}", e)
        emptyList()
    }
}

suspend fun getKecamatanIdByName(name: String): String? {
    return try {
        val cleanName = name.trim()
        val result = supabase.from("kecamatan")
            .select(columns = Columns.raw("id,nama")) {
                filter {
                    ilike("nama", "%$cleanName%")
                }
            }
            .decodeList<Map<String, String>>()
        result.firstOrNull()?.get("id")
    } catch (e: Exception) {
        android.util.Log.e("KEC_DEBUG", "Lookup kecamatan gagal: ${e.message}")
        null
    }
}

suspend fun submitReportToSupabase(
    photoBytes: ByteArray,
    results: List<DetectionResult>,
    lat: Double,
    lon: Double,
    alamatLengkap: String,
    namaKecamatanDariGps: String,
    userId: String
): Result<String> {
    return try {
        val kecId = getKecamatanIdByName(namaKecamatanDariGps)

        if (kecId == null) {
            return Result.failure(Exception("Kecamatan tidak ditemukan di database"))
        }

        val fileName = "laporan_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)
        val publicUrl = bucket.publicUrl(fileName)

        val bestResult = results.maxByOrNull { it.score }
        val locationString = "SRID=4326;POINT($lon $lat)"

        val laporan = LaporanInsertDto(
            petani_id = userId,
            foto_url = publicUrl,
            label_ai = bestResult?.label ?: "Unknown",
            confidence = bestResult?.score ?: 0f,
            lokasi = locationString,
            alamat_lengkap = alamatLengkap,
            kecamatan_id = kecId
        )

        supabase.from("laporan").insert(laporan)
        Result.success("Berhasil")
    } catch (e: Exception) {
        Result.failure(e)
    }
}