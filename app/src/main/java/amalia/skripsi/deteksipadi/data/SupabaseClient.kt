package amalia.skripsi.deteksipadi.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable

// 1. Setup Client (Ganti dengan URL & Key Project Supabase Anda)
val supabase = createSupabaseClient(
    supabaseUrl = "https://your-project-id.supabase.co",
    supabaseKey = "your-anon-key"
) {
    install(Postgrest)
    install(Storage)
}

// 2. Data Model untuk Laporan (Sesuai Tabel Database yang kita buat)
@Serializable
data class ReportDto(
    val image_url: String,
    val ai_label: String,
    val confidence: Float,
    val status: String = "pending",
    // PostGIS butuh format string "SRID=4326;POINT(LON LAT)"
    // Kita kirim string, nanti Postgres yang mengerti.
    val location: String
)

// 3. Fungsi Upload & Simpan
suspend fun submitReportToSupabase(
    photoBytes: ByteArray,
    label: String,
    conf: Float,
    lat: Double,
    lon: Double
): Result<String> {
    return try {
        // A. Upload Foto ke Bucket 'evidence_photos'
        val fileName = "report_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)

        // B. Ambil URL Publik
        val publicUrl = bucket.publicUrl(fileName)

        // C. Format Lokasi untuk PostGIS
        // PENTING: PostGIS urutannya Longitude dulu, baru Latitude!
        val locationString = "SRID=4326;POINT($lon $lat)"

        // D. Simpan ke Tabel Reports
        val report = ReportDto(
            image_url = publicUrl,
            ai_label = label,
            confidence = conf,
            location = locationString
        )
        supabase.from("reports").insert(report)

        Result.success("Laporan Berhasil: $label")
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    }
}