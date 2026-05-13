package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.LaporanUpdateDto
import amalia.skripsi.deteksipadi.data.fetchLaporanById
import amalia.skripsi.deteksipadi.data.fetchLaporanUpdates
import amalia.skripsi.deteksipadi.data.fetchNamaPelapor
import amalia.skripsi.deteksipadi.data.markLaporanSelesai
import amalia.skripsi.deteksipadi.data.updateLaporanStatus
import amalia.skripsi.deteksipadi.ui.components.ZoomableImageDialog
import amalia.skripsi.deteksipadi.ui.screens.petani.report.DetailItemRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.net.toUri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    navController: NavController,
    reportData: LaporanDto?,
    reportId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentReport by remember { mutableStateOf(reportData) }
    var namaPetani by remember { mutableStateOf("Memuat nama...") }

    var updateHistory by remember { mutableStateOf<List<LaporanUpdateDto>>(emptyList()) }
    var instruksiInput by remember { mutableStateOf("") }
    var radiusInput by remember { mutableDoubleStateOf(0.3) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isImageFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(reportData?.id) {
        reportData?.id?.let { id ->
            updateHistory = fetchLaporanUpdates(id)
            val latest = fetchLaporanById(id)
            if (latest != null) {
                currentReport = latest
                instruksiInput = latest.instruksi_popt ?: ""
                radiusInput = latest.radius
            }
            namaPetani = fetchNamaPelapor(currentReport?.petani_id ?: "")
        }
    }

    if (currentReport == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Laporan tidak ditemukan") }
        return
    }

    val statusColor = when(currentReport!!.status) {
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
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))

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
                Text("Validasi Laporan Lapangan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))

                DetailItemRow(Icons.Default.Info, "Status Saat Ini", currentReport!!.status.replace("_", " ").uppercase(), statusColor)
                DetailItemRow(Icons.Default.BugReport, "Hama Terdeteksi", currentReport!!.label_ai ?: "Belum Teridentifikasi", Color(0xFFD32F2F))
                DetailItemRow(Icons.Default.LocationOn, "Lokasi Lapangan", currentReport!!.alamat_lengkap ?: "-", Color(0xFF1976D2))
                DetailItemRow(Icons.Default.Person, "Nama Pelapor", namaPetani, Color(0xFF388E3C))
                DetailItemRow(Icons.Default.CalendarToday, "Waktu Lapor", currentReport!!.created_at.replace("T", " ").take(16), Color(0xFFF57C00))

                // --- RIWAYAT PEMULIHAN ---
                if (updateHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Riwayat Kondisi Terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(12.dp))
                    updateHistory.forEach { update ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = update.foto_update_url, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Deteksi AI: ${update.label_ai_update}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.height(4.dp))
                                    Text(update.catatan, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- PANEL INTERVENSI POPT ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Panel Intervensi Ahli", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (currentReport!!.status != "selesai" && currentReport!!.status != "ditolak") {

                            // Input hanya bisa diubah jika belum diselesaikan
                            OutlinedTextField(
                                value = instruksiInput,
                                onValueChange = { instruksiInput = it },
                                label = { Text("Instruksi Penanganan Mandiri", style = MaterialTheme.typography.bodyMedium) },
                                placeholder = { Text("Misal: Semprotkan pestisida dosis 2ml/L...", style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 3,
                                enabled = !isSubmitting
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Radius Peringatan EWS: ${(radiusInput * 1000).toInt()} Meter",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Slider(
                                value = radiusInput.toFloat(),
                                onValueChange = { radiusInput = it.toDouble() },
                                valueRange = 0.1f..2.0f,
                                steps = 19
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // --- LOGIKA TOMBOL BERDASARKAN STATUS ---
                            if (currentReport!!.status == "menunggu_verifikasi" || currentReport!!.status == "menunggu") {
                                // POPT Baru Melihat Laporan
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            isSubmitting = true
                                            scope.launch {
                                                val res = updateLaporanStatus(currentReport!!.id, "terverifikasi", instruksiInput, radiusInput)
                                                if (res.isSuccess) { Toast.makeText(context, "Laporan Terverifikasi", Toast.LENGTH_SHORT).show(); navController.popBackStack() }
                                                isSubmitting = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        enabled = !isSubmitting && instruksiInput.isNotBlank()
                                    ) { Text("Verifikasi", style = MaterialTheme.typography.labelLarge) }

                                    Button(
                                        onClick = {
                                            isSubmitting = true
                                            scope.launch {
                                                val res = updateLaporanStatus(currentReport!!.id, "perlu_kunjungan", instruksiInput, radiusInput)
                                                if (res.isSuccess) { Toast.makeText(context, "Status: Perlu Kunjungan", Toast.LENGTH_SHORT).show(); navController.popBackStack() }
                                                isSubmitting = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                        enabled = !isSubmitting
                                    ) { Text("Kunjungan", style = MaterialTheme.typography.labelLarge) }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            updateLaporanStatus(currentReport!!.id, "ditolak", "Laporan tidak valid/foto tidak jelas", 0.0)
                                            navController.popBackStack()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isSubmitting
                                ) { Text("Tolak Laporan", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold) }
                            }
                            else if (currentReport!!.status == "terverifikasi") {
                                // POPT Menunggu Update Petani, TAPI bisa merubah menjadi Perlu Kunjungan jika makin parah
                                Button(
                                    onClick = {
                                        isSubmitting = true
                                        scope.launch {
                                            updateLaporanStatus(currentReport!!.id, "perlu_kunjungan", instruksiInput, radiusInput)
                                            Toast.makeText(context, "Status diubah: Perlu Kunjungan", Toast.LENGTH_SHORT).show()
                                            navController.popBackStack()
                                            isSubmitting = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                    shape = RoundedCornerShape(50),
                                    enabled = !isSubmitting
                                ) { Text("Ubah ke Perlu Kunjungan", style = MaterialTheme.typography.labelLarge) }
                            }
                            else if (currentReport!!.status == "perlu_kunjungan") {
                                // POPT Harus Kunjungan dan Ambil Foto
                                Button(
                                    onClick = {
                                        navController.navigate("geofence_update/${currentReport!!.id}/${currentReport!!.lat}/${currentReport!!.lon}")
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(50),
                                    enabled = !isSubmitting
                                ) {
                                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ambil Foto Bukti Kunjungan", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            // TOMBOL SELESAI (Bisa ditekan kapan saja jika sudah ditangani)
                            if (currentReport!!.status == "terverifikasi" || currentReport!!.status == "perlu_kunjungan") {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isSubmitting = true
                                            if (updateHistory.isNotEmpty()) {
                                                markLaporanSelesai(currentReport!!.id)
                                                navController.popBackStack()
                                            } else {
                                                Toast.makeText(context, "Wajib ada riwayat foto update lahan sebelum diselesaikan!", Toast.LENGTH_LONG).show()
                                                isSubmitting = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                    enabled = !isSubmitting
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Tandai Lahan Pulih (Selesai)", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        } else {
                            Text("Laporan ini telah dinyatakan SELESAI atau DITOLAK.", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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