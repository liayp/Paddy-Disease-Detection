package amalia.skripsi.deteksipadi.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable

// --- 1. SETUP CLIENT ---
// (Client didefinisikan di level atas agar bisa diakses fungsi lain di file ini)
val supabase = createSupabaseClient(
    supabaseUrl = "https://gyhvaxwqjubznzivmyqo.supabase.co",
    supabaseKey = "sb_publishable_nioGHUXmUEc_cu2NCRlP3g_YJKoNhIt" // Pastikan Key ini benar
) {
    install(Postgrest)
    install(Storage)
}

// --- 2. MODEL DATA (DTO) ---

// Model untuk Peta (Output dari SQL get_active_hotspots)
@Serializable
data class HotspotDto(
    val id: String,
    val lat: Double,
    val lon: Double,
    val ai_label: String,
    val image_url: String
)

// Model untuk Detail Deteksi (Disimpan sebagai JSON saat upload)
@Serializable
data class DetectionDetailDto(
    val label: String,
    val score: Float,
    val box: List<Float> // [left, top, right, bottom]
)

// Parameter untuk RPC submit_smart_report
@Serializable
data class SmartReportParams(
    val p_image_url: String,
    val p_ai_label: String,
    val p_confidence: Float,
    val p_lat: Double,
    val p_lon: Double,
    val p_details: List<DetectionDetailDto>
)

// --- 3. FUNGSI-FUNGSI API ---

// Fungsi A: Mengambil data titik hama untuk Peta


// Fungsi B: Mengirim Laporan (Upload Foto + RPC Logic)
suspend fun submitReportToSupabase(
    photoBytes: ByteArray,
    results: List<amalia.skripsi.deteksipadi.ml.DetectionResult>, // Pastikan import DetectionResult benar
    lat: Double,
    lon: Double
): Result<String> {
    return try {
        // 1. Cari Label Dominan (Score Tertinggi)
        val bestResult = results.maxByOrNull { it.score }
        val dominantLabel = bestResult?.label ?: "Tidak Terdeteksi"
        val dominantScore = bestResult?.score ?: 0f

        // 2. Konversi List TFLite ke List DTO (agar bisa jadi JSON)
        val detailsDto = results.map { res ->
            DetectionDetailDto(
                label = res.label,
                score = res.score,
                box = listOf(res.box.left, res.box.top, res.box.right, res.box.bottom)
            )
        }

        // 3. Upload Foto ke Storage
        val fileName = "report_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)
        val publicUrl = bucket.publicUrl(fileName)

        // 4. Panggil RPC SQL 'submit_smart_report'
        val params = SmartReportParams(
            p_image_url = publicUrl,
            p_ai_label = dominantLabel,
            p_confidence = dominantScore,
            p_lat = lat,
            p_lon = lon,
            p_details = detailsDto
        )

        val response = supabase.postgrest.rpc("submit_smart_report", params)

        Result.success("Sukses! Status: ${response.data}")
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    }
}