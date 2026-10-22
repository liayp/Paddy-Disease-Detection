package amalia.skripsi.deteksipadi.ui.screens.general.report

import amalia.skripsi.deteksipadi.data.LaporanTimelineEventDto
import amalia.skripsi.deteksipadi.data.fetchLaporanById
import amalia.skripsi.deteksipadi.data.fetchLaporanStatusRiwayat
import amalia.skripsi.deteksipadi.data.fetchNamaPelapor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import amalia.skripsi.deteksipadi.util.formatWitaDateTime
import androidx.compose.ui.graphics.Color
import java.util.Locale

/** Status history remains independent. Only the separate stage-information card is accordion. */
@Composable
fun LaporanTimelineCard(
    laporanId: String,
    refreshKey: String = "",
    durationText: String? = null,
    onEventSelected: (LaporanTimelineEventDto?) -> Unit = {}
) {
    var events by remember(laporanId, refreshKey) { mutableStateOf<List<LaporanTimelineEventDto>>(emptyList()) }
    var isLoading by remember(laporanId, refreshKey) { mutableStateOf(true) }
    var selectedId by remember(laporanId) { mutableStateOf<String?>(null) }
    var reporterName by remember(laporanId, refreshKey) { mutableStateOf("Petani") }

    LaunchedEffect(laporanId, refreshKey) {
        isLoading = true
        val loaded = fetchLaporanStatusRiwayat(laporanId)
        val report = fetchLaporanById(laporanId)
        reporterName = report?.let { fetchNamaPelapor(it.petani_id) } ?: "Petani"
        events = loaded
        if (selectedId != null && loaded.none { it.history.id == selectedId }) selectedId = null
        if (selectedId == null && loaded.isNotEmpty()) {
            selectedId = loaded.last().history.id
            onEventSelected(loaded.last())
        }
        isLoading = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Linimasa & Riwayat Penanganan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text("Ketuk nama tahapan untuk menampilkan tahap yang dipilih pada kartu informasi di bawah.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            durationText?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("Total durasi proses: $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(14.dp))

            when {
                isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                events.isEmpty() -> Text("Riwayat belum tersedia.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> events.forEachIndexed { index, item ->
                    val event = item.history
                    val selected = event.id == selectedId
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.width(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.padding(top = 7.dp).size(8.dp).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                            if (index < events.lastIndex) Box(Modifier.width(1.5.dp).height(if (selected) 54.dp else 42.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).padding(bottom = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else Color.Transparent)
                                    .clickable {
                                        val next = if (selected) null else event.id
                                        selectedId = next
                                        onEventSelected(if (selected) null else item)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    timelineTitle(event.jenis_peristiwa, event.status_baru),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text("${actorRole(event.jenis_peristiwa)} • ${item.actorName ?: if (event.jenis_peristiwa.contains("petani")) reporterName else "Sistem"}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 1.dp))
                            Text(formatTimelineTimestamp(event.created_at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LaporanTahapInformasiCard(
    event: LaporanTimelineEventDto?,
    reporterName: String,
    initiallyExpanded: Boolean = true
) {
    var expanded by remember(event?.history?.id) { mutableStateOf(initiallyExpanded && event != null) }
    val item = event ?: return
    val history = item.history

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Informasi pada tahap ini",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        timelineTitle(history.jenis_peristiwa, history.status_baru),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Tutup informasi tahap" else "Buka informasi tahap",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine("Aktor", "${actorRole(history.jenis_peristiwa)} • ${item.actorName ?: reporterName}")
                    if (!history.status_sebelumnya.isNullOrBlank()) DetailLine("Perubahan status", "${prettyStatus(history.status_sebelumnya)} → ${prettyStatus(history.status_baru)}") else DetailLine("Status", prettyStatus(history.status_baru))
                    DetailLine("Nama pelapor", reporterName)
                    history.label_ai?.takeIf { it.isNotBlank() }?.let { DetailLine("Prediksi AI", it) }
                    history.label_koreksi_popt?.takeIf { it.isNotBlank() }?.let { DetailLine("Klasifikasi tervalidasi POPT", it) }
                    history.confidence?.let { val percent = if (it <= 1.0) it * 100 else it; DetailLine("Confidence", String.format(
                        Locale("id", "ID"), "%.1f%%", percent)) }
                    history.alamat_lengkap?.takeIf { it.isNotBlank() }?.let { DetailLine("Lokasi laporan", it) }
                    if (history.latitude != null && history.longitude != null) DetailLine("Lokasi bukti lapangan", "%.6f, %.6f".format(Locale.US, history.latitude, history.longitude))
                    history.deskripsi_gejala?.takeIf { it.isNotBlank() }?.let { DetailLine("Deskripsi/gejala dari petani", it) }
                    if (history.jenis_peristiwa == "laporan_rekomendasi_penanganan") {
                        history.instruksi_popt?.takeIf { it.isNotBlank() }?.let { DetailLine("Penanganan mandiri / instruksi POPT", it) }
                    }
                    history.alasan_kunjungan?.takeIf { it.isNotBlank() }?.let { DetailLine("Alasan perlu kunjungan", it) }
                    history.alasan_penolakan?.takeIf { it.isNotBlank() }?.let { DetailLine("Alasan penolakan", it) }
                    history.catatan?.takeIf { it.isNotBlank() }?.let { DetailLine("Catatan update", it) }
                    history.foto_url?.takeIf { it.isNotBlank() }?.let { AsyncImage(model = it, contentDescription = "Bukti pada tahap ini", modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun timelineTitle(type: String, status: String): String = when (type) {
    "laporan_diterima" -> "Laporan Diterima"
    "baseline_saat_migrasi" -> "Snapshot Riwayat Dibentuk"
    "laporan_rekomendasi_penanganan" -> "Penanganan Mandiri Direkomendasikan"
    "perlu_kunjungan" -> "Perlu Kunjungan Lapangan"
    "laporan_ditolak" -> "Laporan Ditolak"
    "laporan_selesai" -> "Penanganan Laporan Ditutup"
    "catatan_diperbarui" -> "Informasi POPT Diperbarui"
    "klasifikasi_dikoreksi" -> "Klasifikasi Hama Dikoreksi POPT"
    "update_lahan_petani" -> "Update Kondisi dari Petani"
    "update_lahan_popt" -> "Update Kondisi dari POPT"
    "jadwal_kunjungan_dibuat" -> "Jadwal Kunjungan Dibuat"
    "jadwal_waktu_diubah" -> "Jadwal Kunjungan Diubah"
    "jadwal_usulan_ulang" -> "Usulan Jadwal Ulang dari Petani"
    "jadwal_usulan_ditolak" -> "Usulan Jadwal Tidak Disetujui"
    "jadwal_dikonfirmasi" -> "Jadwal Kunjungan Dikonfirmasi"
    "jadwal_terlaksana" -> "Bukti Kunjungan Lapangan Tercatat"
    "jadwal_dibatalkan" -> "Jadwal Kunjungan Dibatalkan"
    else -> prettyStatus(status)
}

private fun actorRole(type: String): String = when {
    type == "laporan_diterima" || type.contains("petani") || type.contains("usulan_ulang") || type == "jadwal_dikonfirmasi" -> "Petani"
    type.contains("popt") || type.contains("rekomendasi") || type == "klasifikasi_dikoreksi" || type.contains("kunjungan") || type == "catatan_diperbarui" || type == "laporan_selesai" -> "POPT"
    else -> "Sistem"
}

private fun prettyStatus(value: String): String = value.replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun formatTimelineTimestamp(value: String): String = formatWitaDateTime(value)
