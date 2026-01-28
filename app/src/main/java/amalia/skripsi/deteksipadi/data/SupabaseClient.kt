package amalia.skripsi.deteksipadi.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth // <--- PENTING: Import Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable

// --- 1. SETUP CLIENT ---
val supabase = createSupabaseClient(
    supabaseUrl = "https://gyhvaxwqjubznzivmyqo.supabase.co",
    supabaseKey = "sb_publishable_nioGHUXmUEc_cu2NCRlP3g_YJKoNhIt"
) {
    install(Postgrest)
    install(Storage)
    install(Auth)
}

// --- 2. MODEL DATA (DTO) ---

@Serializable
data class HotspotDto(
    val id: String,
    val lat: Double,
    val lon: Double,
    val ai_label: String,
    val image_url: String
)

@Serializable
data class DetectionDetailDto(
    val label: String,
    val score: Float,
    val box: List<Float>
)

@Serializable
data class SmartReportParams(
    val p_image_url: String,
    val p_ai_label: String,
    val p_confidence: Float,
    val p_lat: Double,
    val p_lon: Double,
    val p_details: List<DetectionDetailDto>,
    // Tambahkan user_id jika Anda sudah update SQL untuk menerima user_id
    // val p_user_id: String? = null
)

// --- 3. FUNGSI-FUNGSI API ---

// Fungsi A: Fetch Data Peta
suspend fun fetchActiveHotspots(): List<HotspotDto> {
    return try {
        val result = supabase.postgrest.rpc("get_active_hotspots")
        result.decodeList<HotspotDto>()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// Fungsi B: Kirim Laporan
suspend fun submitReportToSupabase(
    photoBytes: ByteArray,
    results: List<amalia.skripsi.deteksipadi.ml.DetectionResult>,
    lat: Double,
    lon: Double
): Result<String> {
    return try {
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
            p_details = detailsDto
        )

        val response = supabase.postgrest.rpc("submit_smart_report", params)

        // Return JSON Murni
        Result.success(response.data)
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    }
}