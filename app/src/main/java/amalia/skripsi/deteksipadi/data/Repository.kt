package amalia.skripsi.deteksipadi.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.Serializable

// Model data khusus untuk Peta (sesuai output SQL get_active_hotspots)
@Serializable
data class Hotspot(
    val id: String,
    val lat: Double,
    val lon: Double,
    val ai_label: String,
    val image_url: String
)

// Fungsi untuk mengambil data titik hama
suspend fun fetchActiveHotspots(): List<HotspotDto> {
    return try {
        // Memanggil fungsi SQL 'get_active_hotspots'
        val result = supabase.postgrest.rpc("get_active_hotspots")
        result.decodeList<HotspotDto>()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}