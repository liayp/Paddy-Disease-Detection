@file:Suppress("DEPRECATION")
package amalia.skripsi.deteksipadi.data

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

suspend fun fetchLaporanStatusRiwayat(laporanId: String): List<LaporanTimelineEventDto> = try {
    val history = supabase.from("laporan_status_riwayat").select {
        filter { eq("laporan_id", laporanId) }
        order("created_at", Order.ASCENDING)
    }.decodeList<LaporanStatusRiwayatDto>()

    val actorIds = history.mapNotNull { it.actor_id }.distinct()
    val actorNames = if (actorIds.isEmpty()) {
        emptyMap()
    } else {
        supabase.from("profile_directory")
            .select(columns = Columns.raw("id, full_name")) {
                filter { isIn("id", actorIds) }
            }
            .decodeList<ProfileDirectoryDto>()
            .associate { it.id to it.full_name }
    }

    history.map { event ->
        LaporanTimelineEventDto(event, event.actor_id?.let { actorNames[it] })
    }
} catch (e: Exception) {
    Log.w("LAPORAN_TIMELINE", "Gagal memuat riwayat: ${e.message}")
    emptyList()
}
