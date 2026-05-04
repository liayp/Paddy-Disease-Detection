package amalia.skripsi.deteksipadi.data

import amalia.skripsi.deteksipadi.ml.DetectionResult
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

val supabase = createSupabaseClient(
    supabaseUrl = "https://rhlixmoadeexgvrbxkmo.supabase.co",
    supabaseKey = "sb_publishable_dV8oRP92tnXhqDantHWkkg_RTgO3V4P"
) {
    httpEngine = OkHttp.create()

    install(Postgrest)
    install(Storage)
    install(Auth)
    install(Realtime)
}

// DTO Murni untuk membaca data dari database (Ditambah lat & lon dari Computed Field)
@Serializable
data class LaporanDto(
    val id: String,
    val petani_id: String,
    val foto_url: String = "",
    val label_ai: String? = null,
    val confidence: Float? = null,
    val status: String = "menunggu",
    val prioritas: String? = null,
    val termasuk_cluster: Boolean = false,
    val alamat_lengkap: String? = null,
    val created_at: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val deskripsi_gejala: String = "",
    val instruksi_popt: String? = null,
    val radius: Double = 0.3,
    val jenis_pelaporan: String = "ai"
)

@Serializable
data class NotificationItem(
    val id: String,
    val user_id: String,
    val laporan_id: String? = null,
    val jenis: String,
    val judul: String,
    val pesan: String,
    val sudah_dibaca: Boolean,
    val created_at: String,
    val foto_url_hama: String? = null,
    val laporan: LaporanDto? = null
)

@Serializable
data class LaporanUpdateDto(
    val id: String? = null,
    val laporan_id: String,
    val foto_update_url: String,
    val label_ai_update: String,
    val confidence_update: Float,
    val catatan: String
)

// DTO Khusus untuk Insert (PostgREST butuh WKT String untuk tipe PostGIS)
@Serializable
data class LaporanInsertDto(
    val petani_id: String,
    val foto_url: String,
    val label_ai: String?,
    val confidence: Float?,
    val lokasi: String, // Format WKT: SRID=4326;POINT(lon lat)
    val alamat_lengkap: String,
    val kecamatan_id: String?,
    val deskripsi_gejala: String,
    val jenis_pelaporan: String,
    val prioritas: String,
    val status: String = "menunggu_verifikasi"
)

@Serializable
data class MasterHamaDto(
    val id: String,
    val nama_hama: String,
    val deskripsi: String,
    val ciri_ciri: String,
    val pertolongan_pertama: String
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
        val cleanName = name.replace("Kecamatan", "", ignoreCase = true).trim()

        val result = supabase.from("kecamatan")
            .select(columns = Columns.raw("id, nama_kecamatan")) {
                filter {
                    ilike("nama_kecamatan", "%$cleanName%")
                }
            }
            .decodeList<Map<String, String>>()

        result.firstOrNull()?.get("id")
    } catch (e: Exception) {
        android.util.Log.e("KEC_DEBUG", "Lookup gagal: ${e.message}")
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
    userId: String,
    deskripsiGejala: String,
    jenisPelaporan: String = "AI", // Parameter default
    isManualMode: Boolean,
    manualPestName: String
): Result<String> {
    return try {
        val kecId = getKecamatanIdByName(namaKecamatanDariGps)
        if (kecId == null) return Result.failure(Exception("Kecamatan tidak ditemukan di database"))

        val fileName = "laporan_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)
        val publicUrl = bucket.publicUrl(fileName)

        val bestResult = results.maxByOrNull { it.score }
        val locationString = "SRID=4326;POINT($lon $lat)"

        // LOGIKA PENENTUAN DATA
        val finalLabel = if (isManualMode) {
            if (manualPestName == "Tidak Tahu") null else manualPestName
        } else {
            bestResult?.label
        }

        val finalConfidence = if (isManualMode) null else bestResult?.score
        val finalTipe = if (isManualMode) "MANUAL" else "AI"

        // Logika Prioritas yang aman

        val finalPrioritas = if (isManualMode) {
            "rendah" // Laporan manual langsung diset sedang agar diperiksa POPT
        } else {
            if (finalConfidence != null && finalConfidence > 0.5f) "tinggi" else "sedang"
        }

        val laporan = LaporanInsertDto(
            petani_id = userId,
            foto_url = publicUrl,
            label_ai = finalLabel,
            confidence = finalConfidence,
            lokasi = locationString,
            alamat_lengkap = alamatLengkap,
            kecamatan_id = kecId,
            deskripsi_gejala = deskripsiGejala,
            jenis_pelaporan = finalTipe,
            prioritas = finalPrioritas,
            status = "menunggu_verifikasi"

        )

        supabase.from("laporan").insert(laporan)
        Result.success("Berhasil")
    } catch (e: Exception) {
        android.util.Log.e("UPLOAD_ERROR", "Gagal upload: ${e.message}", e)
        Result.failure(e)
    }
}

suspend fun fetchLaporanUpdates(laporanId: String): List<LaporanUpdateDto> {
    return try {
        supabase.from("laporan_updates")
            .select { filter { eq("laporan_id", laporanId) } }
            .decodeList<LaporanUpdateDto>()
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun submitLaporanUpdate(
    laporanId: String,
    userId: String,
    photoBytes: ByteArray,
    labelAi: String,
    confidence: Float,
    catatan: String
): Result<Unit> {
    return try {
        val fileName = "update_${laporanId}_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)
        val publicUrl = bucket.publicUrl(fileName)

        val updateData = LaporanUpdateDto(
            laporan_id = laporanId,
            foto_update_url = publicUrl,
            label_ai_update = labelAi,
            confidence_update = confidence,
            catatan = catatan
        )

        supabase.from("laporan_updates").insert(updateData)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}


suspend fun updateLaporanStatus(
    laporanId: String,
    status: String,
    instruksi: String,
    radius: Double
): Result<Unit> {
    return try {
        supabase.from("laporan").update({
            set("status", status)
            set("instruksi_popt", instruksi)
            set("radius", radius)
        }) {
            filter { eq("id", laporanId) }
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun markLaporanSelesai(laporanId: String): Result<Unit> {
    return try {
        supabase.from("laporan").update({
            set("status", "selesai")
        }) {
            filter { eq("id", laporanId) }
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@Serializable
data class LocationValidationDto(
    @SerialName("is_in_sawah") val isInSawah: Boolean,
    @SerialName("kecamatan_id") val kecId: String?,
    @SerialName("nama_kecamatan") val kecName: String?
)

suspend fun checkLocationGeofence(lat: Double, lon: Double): LocationValidationDto? {
    return try {
        supabase.postgrest.rpc(
            function = "check_location_validity",
            parameters = buildJsonObject {
                put("user_lat", JsonPrimitive(lat))
                put("user_lon", JsonPrimitive(lon))
            }
        ).decodeSingle<LocationValidationDto>()
    } catch (e: Exception) {
        null
    }
}

suspend fun fetchMasterHama(): List<MasterHamaDto> {
    return try {
        supabase.from("master_hama").select().decodeList<MasterHamaDto>()
    } catch (e: Exception) {
        android.util.Log.e("MASTER_HAMA", "Gagal fetch: ${e.message}")
        emptyList()
    }
}