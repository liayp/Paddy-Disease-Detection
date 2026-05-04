package amalia.skripsi.deteksipadi.ui.screens.petani.report

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.LaporanUpdateDto
import amalia.skripsi.deteksipadi.data.fetchLaporanUpdates
import amalia.skripsi.deteksipadi.ui.components.ZoomableImageDialog
import amalia.skripsi.deteksipadi.ui.screens.popt.reports.DetailItemRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetaniReportDetailScreen(
    navController: NavController,
    reportData: LaporanDto?,
    reportId: String? = null
) {
    val context = LocalContext.current
    var isImageFullscreen by remember { mutableStateOf(false) }
    var updateHistory by remember { mutableStateOf<List<LaporanUpdateDto>>(emptyList()) }

    // Ambil data update terbaru
    LaunchedEffect(reportData?.id) {
        reportData?.id?.let { updateHistory = fetchLaporanUpdates(it) }
    }

    if (reportData == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Laporan tidak ditemukan") }
        return
    }

    if (isImageFullscreen) {
        ZoomableImageDialog(
            imageUrl = reportData.foto_url,
            onDismiss = { isImageFullscreen = false }
        )
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .clickable { isImageFullscreen = true }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(reportData.foto_url).crossfade(true).build(),
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
                    val acc = (reportData.confidence?.times(100))?.toInt()
                    AssistChip(
                        onClick = {},
                        label = { Text("Confidence Score: $acc%", color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2E7D32)),
                        border = null
                    )
                    reportData.label_ai?.let { Text(text = it, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White) }
                    Text(text = "Status: ${reportData.status.replace("_", " ").uppercase()}", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                }

                Icon(
                    Icons.Default.ZoomIn,
                    null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                )
            }

            Column(
                modifier = Modifier.offset(y = (-20).dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(24.dp)
            ) {
                Text("Informasi Laporan Anda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(20.dp))

                DetailItemRow(Icons.Default.BugReport, "Hasil Deteksi AI", reportData.label_ai, Color(0xFFD32F2F))
                Spacer(modifier = Modifier.height(12.dp))
                DetailItemRow(Icons.Default.LocationOn, "Alamat Temuan", reportData.alamat_lengkap ?: "Detail alamat tidak tersedia", Color(0xFF1976D2))
                Spacer(modifier = Modifier.height(12.dp))
                DetailItemRow(Icons.Default.Event, "Waktu Pelaporan", reportData.created_at.replace("T", " ").take(16), Color(0xFFF57C00))

                // Tambahkan di dalam Column setelah DetailItemRow
                if (reportData.instruksi_popt != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Tanggapan Petugas POPT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFF2E7D32))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            // Bagian Instruksi Teknis Penanganan
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.AssignmentTurnedIn, "Instruksi", tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Instruksi Penanganan:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B5E20))
                                    Text(reportData.instruksi_popt, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            // REVISI: Tombol Aksi Update Lahan (Hanya jika status mendukung)
                            if (reportData.status == "terverifikasi" || reportData.status == "perlu_kunjungan") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        // Navigasi ke kamera update dengan menyertakan koordinat target sawah (untuk validasi 15m)
                                        navController.navigate("update_lahan/${reportData.id}/${reportData.lat}/${reportData.lon}")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Kirim Bukti Foto Penanganan")
                                }
                            }
                        }
                    }
                }

                // REVISI: Tampilkan Riwayat Update jika sudah pernah mengirim foto pemulihan sebelumnya
                if (updateHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Riwayat Pemulihan Lahan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    updateHistory.forEach { update ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(0.5.dp, Color.LightGray)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = update.foto_update_url,
                                    contentDescription = null,
                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Hasil AI: ${update.label_ai_update}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(update.catatan, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = {
                        // Menggunakan reportData.lat dan reportData.lon langsung
                        val gmmIntentUri = "google.navigation:q=${reportData.lat},${reportData.lon}".toUri()
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        context.startActivity(mapIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Map, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buka Lokasi di Google Maps")
                }
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}
