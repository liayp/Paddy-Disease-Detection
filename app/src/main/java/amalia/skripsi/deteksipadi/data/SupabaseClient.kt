package amalia.skripsi.deteksipadi.data

import amalia.skripsi.deteksipadi.ml.DetectionResult
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable

val supabase = createSupabaseClient(
    supabaseUrl = "https://gyhvaxwqjubznzivmyqo.supabase.co",
    supabaseKey = "sb_publishable_nioGHUXmUEc_cu2NCRlP3g_YJKoNhIt"
) {
    install(Postgrest)
    install(Storage)
    install(Auth)
    install(Realtime)
}

@Serializable
data class HotspotDto(
    val id: String,
    val image_url: String,
    val ai_label: String,
    val confidence: Double,
    val status: String,
    val created_at: String,
    val kecamatan: String,
    val kelurahan: String,
    val address_detail: String,
    val lat: Double,
    val lon: Double,
    val user_id: String? = null,
)

@Serializable
data class DetectionDetailDto(
    val label: String,
    val score: Float,
    val box: List<Float>
)

@Serializable
data class DetectionBoxDto(
    val label: String,
    val score: Float,
    val box: List<Float>
)

@Serializable
data class ReportDto(
    val image_url: String,
    val ai_label: String,
    val confidence: Float,
    val status: String = "active",
    val location: String,
    val lat: Double,
    val lon: Double,
    val kecamatan: String?,
    val kelurahan: String?,
    val address_detail: String?,
    val user_id: String,
    val detection_details: List<DetectionBoxDto>
)

suspend fun submitReportToSupabase(
    photoBytes: ByteArray,
    results: List<DetectionResult>,
    lat: Double,
    lon: Double,
    kecamatan: String,
    kelurahan: String,
    addressDetail: String,
    userId: String
): Result<String> {
    return try {
        val fileName = "report_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)
        val publicUrl = bucket.publicUrl(fileName)

        val bestResult = results.maxByOrNull { it.score }
        val dominantLabel = bestResult?.label ?: "Tidak Terdeteksi"
        val dominantScore = bestResult?.score ?: 0f

        val detailsList = results.map {
            DetectionBoxDto(
                label = it.label,
                score = it.score,
                // Simpan koordinat box biar POPT bisa lihat di dashboard nanti
                box = listOf(it.box.left, it.box.top, it.box.right, it.box.bottom)
            )
        }

        val locationString = "SRID=4326;POINT($lon $lat)"

        val report = ReportDto(
            image_url = publicUrl,
            ai_label = dominantLabel,
            confidence = dominantScore,
            location = locationString,
            lat = lat,
            lon = lon,
            kecamatan = kecamatan,
            kelurahan = kelurahan,
            address_detail = addressDetail,
            user_id = userId,
            detection_details = detailsList
        )
        supabase.from("reports").insert(report)

        Result.success("Berhasil")
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    }
}

@Serializable
data class SmartReportParams(
    val p_image_url: String,
    val p_ai_label: String,
    val p_confidence: Float,
    val p_lat: Double,
    val p_lon: Double,
    val p_details: List<DetectionDetailDto>,
    val p_district: String,
    val p_user_id: String? = null
)

// --- FUNGSI-FUNGSI API ---

// Fetch Data Peta
suspend fun fetchActiveHotspots(): List<HotspotDto> {
    return try {
        val result = supabase.postgrest.rpc("get_active_hotspots")
        result.decodeList<HotspotDto>()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// Kirim Laporan
suspend fun submitReportToSupabase(
    photoBytes: ByteArray,
    results: List<amalia.skripsi.deteksipadi.ml.DetectionResult>,
    lat: Double,
    lon: Double,
    districtName: String
): Result<String> {
    return try {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return Result.failure(Exception("Anda harus login untuk melapor!"))

        val bestResult = results.maxByOrNull { it.score }
        val dominantLabel = bestResult?.label ?: "Tidak Terdeteksi"
        val dominantScore = bestResult?.score ?: 0f

        val detailsDto = results.map { res ->
            DetectionDetailDto(
                label = res.label,
                score = res.score,
                box = listOf(res.box.left, res.box.top, res.box.right, res.box.bottom)
            )
        }

        val fileName = "report_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)
        val publicUrl = bucket.publicUrl(fileName)

        val params = SmartReportParams(
            p_image_url = publicUrl,
            p_ai_label = dominantLabel,
            p_confidence = dominantScore,
            p_lat = lat,
            p_lon = lon,
            p_details = detailsDto,
            p_district = districtName,
            p_user_id = userId
        )

        supabase.from("reports")

        Result.success("Berhasil")
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    }
}