@file:Suppress("DEPRECATION")
package amalia.skripsi.deteksipadi.data

import amalia.skripsi.deteksipadi.ml.DetectionResult
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double

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

const val LAPORAN_COLUMNS = "id, petani_id, foto_url, label_ai, confidence, status, prioritas, termasuk_cluster, alamat_lengkap, created_at, lat, lon, deskripsi_gejala, instruksi_popt, radius, jenis_pelaporan, kecamatan_id"

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
    val jenis_pelaporan: String = "ai",
    val kecamatan_id: String? = null
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

@Serializable
data class LaporanInsertDto(
    val petani_id: String,
    val foto_url: String,
    val label_ai: String?,
    val confidence: Float?,
    val lokasi: String,
    val alamat_lengkap: String,
    val kecamatan_id: String?,
    val deskripsi_gejala: String,
    val jenis_pelaporan: String,
    val prioritas: String,
    val status: String = "menunggu_verifikasi"
)

@Serializable
data class KecamatanDto(
    val id: String,
    val nama_kecamatan: String,
    val polygon_geojson: String? = null
)

@Serializable
data class InfoHamaDto(
    val id: String? = null,
    val kategori: String,
    val isi_informasi: String,
    val urutan: Int
)

@Serializable
data class MasterHamaDto(
    val id: String,
    val nama_hama: String,
    val deskripsi: String,
    val ciri_ciri: String,
    val informasi_hama: List<InfoHamaDto> = emptyList()
)

// REVISI: DTO Untuk Master Poktan
@Serializable
data class MasterPoktanDto(
    val id: String,
    val nama_poktan: String,
    val kecamatan: String,
    val desa: String
)

@Serializable
data class KecamatanSimpleDto(
    val nama_kecamatan: String
)

@Serializable
data class CoordinateParams(
    val p_lat: Double,
    val p_lon: Double
)

@Serializable
data class ProfileNameOnly(val full_name: String? = null)

@Serializable
data class PoptWilayahSimple(val popt_id: String? = null)

suspend fun fetchActiveLaporan(): List<LaporanDto> {
    return try {
        supabase.from("laporan")
            .select(columns = Columns.raw(LAPORAN_COLUMNS)) {
                filter { neq("status", "ditolak") }
            }.decodeList<LaporanDto>()
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun fetchLaporanUntukPOPT(poptId: String): List<LaporanDto> {
    return try {
        val wilayahList = supabase.from("popt_wilayah")
            .select(columns = Columns.raw("kecamatan_id")) {
                filter { eq("popt_id", poptId) }
            }.decodeList<PoptWilayahDto>()

        val kecIds = wilayahList.map { it.kecamatan_id }

        if (kecIds.isEmpty()) return emptyList()

        supabase.from("laporan")
            .select(columns = Columns.raw(LAPORAN_COLUMNS)) {
                filter { isIn("kecamatan_id", kecIds) }
            }.decodeList<LaporanDto>()
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun fetchLaporanById(id: String): LaporanDto? {
    return try {
        supabase.from("laporan")
            .select(columns = Columns.raw(LAPORAN_COLUMNS)) {
                filter { eq("id", id) }
            }
            .decodeList<LaporanDto>()
            .firstOrNull()
    } catch (e: Exception) {
        null
    }
}

suspend fun fetchNamaPelapor(petaniId: String): String {
    return try {
        val profile = supabase.from("profiles")
            .select(columns = Columns.raw("full_name")) {
                filter { eq("id", petaniId) }
            }
            .decodeSingleOrNull<ProfileNameOnly>()
        profile?.full_name ?: "Petani (Tidak diketahui)"
    } catch (e: Exception) {
        "Petani"
    }
}

suspend fun fetchNamaPoptByKecamatan(kecId: String?): String {
    if (kecId == null) return "Belum Ada Petugas"
    return try {
        val poptWilayah = supabase.from("popt_wilayah")
            .select(Columns.raw("popt_id")) { filter { eq("kecamatan_id", kecId) } }
            .decodeList<PoptWilayahSimple>()
            .firstOrNull()

        val pId = poptWilayah?.popt_id ?: return "Belum Ada Petugas"

        val profile = supabase.from("profiles")
            .select(Columns.raw("full_name")) { filter { eq("id", pId) } }
            .decodeSingleOrNull<ProfileNameOnly>()

        profile?.full_name ?: "Petugas POPT"
    } catch (e: Exception) {
        "Petugas POPT"
    }
}

suspend fun getKecamatanIdByCoordinate(lat: Double, lon: Double): String? {
    return try {
        val result = supabase.postgrest.rpc(
            "get_kecamatan_id_by_koordinat",
            CoordinateParams(p_lat = lat, p_lon = lon)
        ).decodeAsOrNull<String>()

        result?.takeIf { it.isNotBlank() && it != "null" }
    } catch (e: Exception) {
        null
    }
}

suspend fun submitReportToSupabase(
    photoBytes: ByteArray,
    results: List<DetectionResult>,
    lat: Double,
    lon: Double,
    alamatLengkap: String,
    userId: String,
    deskripsiGejala: String,
    jenisPelaporan: String = "AI",
    isManualMode: Boolean,
    manualPestName: String
): Result<String> {
    return try {
        val kecId = getKecamatanIdByCoordinate(lat, lon)

        if (kecId == null) throw Exception("OUT_OF_BOUNDS")

        val fileName = "laporan_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)
        val publicUrl = bucket.publicUrl(fileName)

        val bestResult = results.maxByOrNull { it.score }
        val locationString = "SRID=4326;POINT($lon $lat)"

        val finalLabel = if (isManualMode) {
            if (manualPestName == "Tidak Tahu" || manualPestName.isBlank()) null else manualPestName
        } else {
            bestResult?.label
        }

        val finalConfidence = if (isManualMode) null else bestResult?.score
        val finalTipe = if (isManualMode) "MANUAL" else "AI"
        val finalPrioritas = if (isManualMode) "rendah"
        else if (finalConfidence != null && finalConfidence > 0.5f) "tinggi" else "sedang"

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
    instruksi: String? = null,
    radius: Double? = null
): Result<Unit> {
    return try {
        supabase.from("laporan").update({
            set("status", status)
            if (instruksi != null) set("instruksi_popt", instruksi)
            if (radius != null) set("radius", radius)
        }) { filter { eq("id", laporanId) } }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun markLaporanSelesai(laporanId: String): Result<Unit> {
    return try {
        supabase.from("laporan").update({
            set("status", "selesai")
        }) { filter { eq("id", laporanId) } }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// REVISI: Fungsi Tarik Master Poktan
suspend fun fetchMasterPoktan(): List<MasterPoktanDto> {
    return try {
        supabase.from("master_poktan").select().decodeList<MasterPoktanDto>()
    } catch (e: Exception) {
        emptyList()
    }
}

@Serializable
data class LocationValidationDto(
    @SerialName("is_in_sawah") val isInSawah: Boolean,
    @SerialName("kecamatan_id") val kecId: String?,
    @SerialName("nama_kecamatan") val kecName: String?
)

suspend fun fetchSemuaKecamatan(): List<KecamatanDto> {
    return try {
        supabase.from("vw_kecamatan_geojson").select().decodeList<KecamatanDto>()
    } catch (e: Exception) {
        emptyList()
    }
}

fun parseGeoJsonToLatLng(geoJsonString: String?): List<List<LatLng>> {
    if (geoJsonString.isNullOrBlank()) return emptyList()
    val polygons = mutableListOf<List<LatLng>>()
    try {
        val geoJson = Json.parseToJsonElement(geoJsonString).jsonObject
        val type = geoJson["type"]?.jsonPrimitive?.content
        val coordinates = geoJson["coordinates"]?.jsonArray ?: return emptyList()

        when (type) {
            "MultiPolygon" -> {
                for (multiPolygonElement in coordinates) {
                    val polygonArray = multiPolygonElement.jsonArray
                    if (polygonArray.isNotEmpty()) {
                        val outerRing = polygonArray[0].jsonArray
                        val latLngs = outerRing.mapNotNull { coord ->
                            val pt = coord.jsonArray
                            if (pt.size >= 2) LatLng(pt[1].jsonPrimitive.double, pt[0].jsonPrimitive.double) else null
                        }
                        polygons.add(latLngs)
                    }
                }
            }
            "Polygon" -> {
                if (coordinates.isNotEmpty()) {
                    val outerRing = coordinates[0].jsonArray
                    val latLngs = outerRing.mapNotNull { coord ->
                        val pt = coord.jsonArray
                        if (pt.size >= 2) LatLng(pt[1].jsonPrimitive.double, pt[0].jsonPrimitive.double) else null
                    }
                    polygons.add(latLngs)
                }
            }
        }
    } catch (e: Exception) { }
    return polygons
}

suspend fun fetchMasterHama(): List<MasterHamaDto> {
    return try {
        supabase.from("master_hama")
            .select(columns = Columns.raw("*, informasi_hama(*)"))
            .decodeList<MasterHamaDto>()
    } catch (e: Exception) {
        emptyList()
    }
}