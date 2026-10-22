package amalia.skripsi.deteksipadi.ui.screens.petani.report

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.LaporanUpdateDto
import amalia.skripsi.deteksipadi.data.LaporanTimelineEventDto
import amalia.skripsi.deteksipadi.data.fetchLaporanById
import amalia.skripsi.deteksipadi.data.fetchLaporanUpdates
import amalia.skripsi.deteksipadi.data.fetchLaporanStatusRiwayat
import amalia.skripsi.deteksipadi.data.fetchNamaPelapor
import amalia.skripsi.deteksipadi.ui.components.ZoomableImageDialog
import amalia.skripsi.deteksipadi.ui.screens.general.report.JadwalKunjunganPanel
import amalia.skripsi.deteksipadi.ui.screens.general.report.LaporanTimelineCard
import amalia.skripsi.deteksipadi.ui.screens.general.report.LaporanTahapInformasiCard
import amalia.skripsi.deteksipadi.data.supabase
import io.github.jan.supabase.auth.auth
import amalia.skripsi.deteksipadi.ui.screens.general.report.TimelineNode
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetaniReportDetailScreen(
    navController: NavController,
    reportData: LaporanDto?,
    reportId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImageFullscreen by remember { mutableStateOf(false) }
    var selectedTimelineEvent by remember { mutableStateOf<LaporanTimelineEventDto?>(null) }
    var currentReport by remember { mutableStateOf(reportData) }
    var updateHistory by remember { mutableStateOf<List<LaporanUpdateDto>>(emptyList()) }
    var namaPetani by remember { mutableStateOf("Petani") }
    var namaPopt by remember { mutableStateOf("Memuat petugas...") }
    var timelineRefreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reportData?.id, reportId) {
        val targetId = reportData?.id ?: reportId
        if (targetId != null) {
            updateHistory = fetchLaporanUpdates(targetId)
            val latest = fetchLaporanById(targetId)
            if (latest != null){
                currentReport = latest
                namaPetani = fetchNamaPelapor(latest.petani_id)
            }
            val timeline = fetchLaporanStatusRiwayat(targetId)
            namaPopt = timeline.asReversed()
                .firstOrNull { it.history.jenis_peristiwa in setOf("laporan_rekomendasi_penanganan", "perlu_kunjungan", "laporan_selesai", "update_lahan_popt", "jadwal_terlaksana") }
                ?.actorName
                ?.takeIf { it.isNotBlank() }
                ?: "Petugas POPT"
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, reportData?.id, reportId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val targetId = reportData?.id ?: reportId
                if (targetId != null) {
                    scope.launch {
                        val latest = fetchLaporanById(targetId)
                        if (latest != null) currentReport = latest
                        updateHistory = fetchLaporanUpdates(targetId)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (currentReport == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val displayStatus = currentReport!!.status.replace("_", " ").uppercase()
    val statusColor = when (currentReport!!.status) {
        "ditolak" -> Color(0xFFD32F2F)
        "selesai", "penanganan_mandiri" -> Color(0xFF388E3C)
        "perlu_kunjungan" -> Color(0xFF7B1FA2)
        else -> Color(0xFFF57C00)
    }

    val durasiPenangananText = remember(currentReport, updateHistory) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val start = sdf.parse(currentReport!!.created_at.take(19)) ?: Date()
            val end = if (currentReport!!.status == "selesai" && updateHistory.isNotEmpty()) {
                sdf.parse(updateHistory.last().created_at.take(19)) ?: Date()
            } else {
                Date()
            }
            val diffMs = end.time - start.time
            val diffHours = diffMs / (1000 * 60 * 60)
            val diffDays = diffHours / 24
            when {
                diffDays > 0 -> "$diffDays Hari ${diffHours % 24} Jam"
                diffHours > 0 -> "$diffHours Jam"
                else -> "Kurang dari 1 Jam"
            }
        } catch (e: Exception) {
            "Dalam perhitungan"
        }
    }

    val selectedSnapshot = selectedTimelineEvent?.history
    val headerImageUrl = selectedSnapshot?.foto_url?.takeIf { it.isNotBlank() } ?: currentReport!!.foto_url
    val headerLabel = selectedSnapshot?.label_ai?.takeIf { it.isNotBlank() } ?: currentReport!!.label_ai
    val headerConfidence = selectedSnapshot?.confidence ?: currentReport!!.confidence

    if (isImageFullscreen) {
        ZoomableImageDialog(imageUrl = headerImageUrl, onDismiss = { isImageFullscreen = false })
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // --- HEADER GAMBAR ---
            Box(modifier = Modifier.fillMaxWidth().height(320.dp).clickable { isImageFullscreen = true }) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(headerImageUrl).crossfade(true).build(),
                    contentDescription = "Foto Laporan",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))

                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.padding(top = 40.dp, start = 16.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    val acc = (headerConfidence?.toDouble()?.times(100.0))?.toInt()
                    if (acc != null) {
                        AssistChip(
                            onClick = {},
                            label = { Text("AKURASI AI: $acc%", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary),
                            border = null,
                            shape = RoundedCornerShape(50)
                        )
                    }
                    Text(
                        text = headerLabel ?: "Belum Teridentifikasi",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Icon(Icons.Default.ZoomIn, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center).size(48.dp))
            }

            // --- PANEL INFORMASI ---
            Column(
                modifier = Modifier.offset(y = (-20).dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(24.dp)
            ) {
                Text("Informasi Laporan Anda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))

                LaporanTimelineCard(
                    laporanId = currentReport!!.id,
                    refreshKey = "${currentReport!!.status}|${currentReport!!.instruksi_popt}|${currentReport!!.alasan_kunjungan}|$timelineRefreshKey",
                    durationText = durasiPenangananText,
                    onEventSelected = { selectedTimelineEvent = it }
                )

                selectedTimelineEvent?.let { selected ->
                    Spacer(modifier = Modifier.height(12.dp))
                    LaporanTahapInformasiCard(
                        event = selected,
                        reporterName = namaPetani,
                        initiallyExpanded = true
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                DetailItemRow(Icons.Default.Info, "Status Laporan", displayStatus, statusColor)
                DetailItemRow(Icons.Default.BugReport, "Identifikasi Hama", currentReport!!.label_efektif ?: "Belum Teridentifikasi", Color(0xFF1976D2))
                DetailItemRow(Icons.Default.LocationOn, "Alamat Temuan", currentReport!!.alamat_lengkap ?: "-", Color(0xFFD32F2F))
                DetailItemRow(Icons.Default.Event, "Waktu Pelaporan", currentReport!!.created_at.replace("T", " ").take(16), Color(0xFFF57C00))

                if (currentReport!!.status == "perlu_kunjungan") {
                    Spacer(Modifier.height(16.dp))
                    JadwalKunjunganPanel(
                        laporanId = currentReport!!.id,
                        laporanStatus = currentReport!!.status,
                        userId = supabase.auth.currentUserOrNull()?.id.orEmpty(),
                        userRole = "petani",
                        onChanged = { timelineRefreshKey += 1; scope.launch { currentReport = fetchLaporanById(currentReport!!.id) } }
                    )
                }

                // --- TANGGAPAN POPT ---
                if (currentReport!!.status != "menunggu_verifikasi") {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Tanggapan Petugas POPT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Justify)
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Ditinjau oleh POPT: $namaPopt", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Justify)
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            Spacer(Modifier.height(12.dp))
                            if (currentReport!!.status == "ditolak") {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("Alasan Penolakan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD32F2F))
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            currentReport!!.alasan_penolakan ?: "Alasan penolakan belum tercatat.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.AssignmentTurnedIn, "Instruksi", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("Instruksi Penanganan:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = currentReport!!.instruksi_popt.takeIf { !it.isNullOrBlank() } ?: "Belum ada instruksi tertulis dari POPT.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (currentReport!!.status == "perlu_kunjungan" && !currentReport!!.alasan_kunjungan.isNullOrBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Alasan kunjungan: ${currentReport!!.alasan_kunjungan}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF7B1FA2),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (currentReport!!.status == "penanganan_mandiri") {
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = {
                                        navController.navigate("geofence_update/${currentReport!!.id}/${currentReport!!.lat}/${currentReport!!.lon}")
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Icon(Icons.Default.CameraAlt, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Kirim Foto Kondisi Lahan Terbaru")
                                }
                            } else if (currentReport!!.status == "perlu_kunjungan") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFF7B1FA2).copy(0.1f), RoundedCornerShape(12.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DirectionsRun, null, tint = Color(0xFF7B1FA2))
                                    Spacer(Modifier.width(8.dp))
                                    Text("POPT menilai pemeriksaan langsung diperlukan. Lihat bagian Jadwal Kunjungan untuk mengonfirmasi waktu atau mengusulkan jadwal ulang.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7B1FA2), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                if (currentReport!!.status == "selesai") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.08f))) {
                        Text(
                            "Penanganan laporan telah dinyatakan selesai oleh POPT. Status ini menandakan proses tindak lanjut laporan ditutup, bukan jaminan bahwa tanaman telah pulih sepenuhnya.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = {
                        val report = currentReport
                        if (report == null || !report.lat.isFinite() || !report.lon.isFinite() ||
                            (report.lat == 0.0 && report.lon == 0.0) || report.lat !in -90.0..90.0 || report.lon !in -180.0..180.0) {
                            Toast.makeText(context, "Koordinat laporan tidak valid atau belum tersedia.", Toast.LENGTH_LONG).show()
                        } else {
                            val mapsUri = "https://www.google.com/maps/dir/?api=1&destination=${report.lat},${report.lon}".toUri()
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, mapsUri))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Aplikasi peta tidak dapat dibuka.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    Text("Buka Lokasi di Google Maps", color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun DetailItemRow(icon: ImageVector, title: String, value: String?, iconTint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            modifier = Modifier.size(42.dp).background(iconTint.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            value?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
