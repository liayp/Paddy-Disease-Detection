package amalia.skripsi.deteksipadi.ui.screens.petani.report

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.LaporanUpdateDto
import amalia.skripsi.deteksipadi.data.fetchLaporanById
import amalia.skripsi.deteksipadi.data.fetchLaporanUpdates
import amalia.skripsi.deteksipadi.data.fetchNamaPoptByKecamatan
import amalia.skripsi.deteksipadi.ui.components.ZoomableImageDialog
import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetaniReportDetailScreen(
    navController: NavController,
    reportData: LaporanDto?,
    reportId: String? = null
) {
    val context = LocalContext.current
    var isImageFullscreen by remember { mutableStateOf(false) }

    var currentReport by remember { mutableStateOf(reportData) }
    var updateHistory by remember { mutableStateOf<List<LaporanUpdateDto>>(emptyList()) }
    var namaPopt by remember { mutableStateOf("Memuat petugas...") }

    // Auto-update data saat masuk ke halaman
    LaunchedEffect(reportData?.id) {
        reportData?.id?.let { id ->
            updateHistory = fetchLaporanUpdates(id)
            val latest = fetchLaporanById(id)
            if (latest != null) {
                currentReport = latest
            }
            namaPopt = fetchNamaPoptByKecamatan(currentReport?.kecamatan_id)
        }
    }

    if (currentReport == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Laporan tidak ditemukan") }
        return
    }

    val displayStatus = currentReport!!.status.replace("_", " ").uppercase()
    val statusColor = when (currentReport!!.status) {
        "ditolak" -> Color(0xFFD32F2F)
        "selesai", "terverifikasi" -> Color(0xFF388E3C)
        "perlu_kunjungan" -> Color(0xFF7B1FA2)
        else -> Color(0xFFF57C00)
    }

    if (isImageFullscreen) {
        ZoomableImageDialog(imageUrl = currentReport!!.foto_url, onDismiss = { isImageFullscreen = false })
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // --- HEADER GAMBAR ---
            Box(modifier = Modifier.fillMaxWidth().height(350.dp).clickable { isImageFullscreen = true }) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(currentReport!!.foto_url).crossfade(true).build(),
                    contentDescription = "Foto Laporan",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))

                // Back Button Transparan (Consistent)
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.padding(top = 40.dp, start = 16.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    val acc = (currentReport!!.confidence?.times(100))?.toInt()
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
                        text = currentReport!!.label_ai ?: "Belum Teridentifikasi",
                        style = MaterialTheme.typography.headlineSmall,
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
                Spacer(modifier = Modifier.height(20.dp))

                DetailItemRow(Icons.Default.Info, "Status Laporan", displayStatus, statusColor)
                DetailItemRow(Icons.Default.BugReport, "Identifikasi Hama", currentReport!!.label_ai ?: "Belum Teridentifikasi", Color(0xFF1976D2))
                DetailItemRow(Icons.Default.LocationOn, "Alamat Temuan", currentReport!!.alamat_lengkap ?: "Detail alamat tidak tersedia", Color(0xFFD32F2F))
                DetailItemRow(Icons.Default.Event, "Waktu Pelaporan", currentReport!!.created_at.replace("T", " ").take(16), Color(0xFFF57C00))

                // --- TANGGAPAN POPT ---
                if (currentReport!!.status != "menunggu" && currentReport!!.status != "menunggu_verifikasi") {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Tanggapan Petugas POPT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                                Text("Ditinjau oleh: $namaPopt", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            Spacer(Modifier.height(12.dp))

                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.AssignmentTurnedIn, "Instruksi", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Instruksi Penanganan:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = currentReport!!.instruksi_popt.takeIf { !it.isNullOrBlank() } ?: "Tidak ada instruksi tertulis. Tunggu kunjungan petugas.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Justify
                                    )
                                }
                            }

                            // JIKA STATUS TERVERIFIKASI, PETANI MENGIRIM UPDATE
                            if (currentReport!!.status == "terverifikasi") {
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
                                    Text("Kirim Bukti Foto Pemulihan")
                                }
                            }
                            // JIKA PERLU KUNJUNGAN, PETANI HANYA MENUNGGU
                            else if (currentReport!!.status == "perlu_kunjungan") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFF7B1FA2).copy(0.1f), RoundedCornerShape(12.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DirectionsRun, null, tint = Color(0xFF7B1FA2))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Menunggu kunjungan langsung dari petugas POPT ke lahan Anda.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7B1FA2), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // --- RIWAYAT PEMULIHAN LAHAN ---
                if (updateHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Riwayat Pemulihan Lahan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(12.dp))

                    updateHistory.forEach { update ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = update.foto_update_url,
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Deteksi AI: ${update.label_ai_update}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.height(4.dp))
                                    Text(update.catatan, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = {
                        val gmmIntentUri = "google.navigation:q=${currentReport!!.lat},${currentReport!!.lon}".toUri()
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
                        context.startActivity(mapIntent)
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