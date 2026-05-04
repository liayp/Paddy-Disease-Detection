package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.LaporanUpdateDto
import amalia.skripsi.deteksipadi.data.fetchLaporanUpdates
import amalia.skripsi.deteksipadi.data.markLaporanSelesai
import amalia.skripsi.deteksipadi.data.updateLaporanStatus
import amalia.skripsi.deteksipadi.ui.components.ZoomableImageDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.sp
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

    // REVISI: State untuk riwayat update
    var updateHistory by remember { mutableStateOf<List<LaporanUpdateDto>>(emptyList()) }
    var instruksiInput by remember { mutableStateOf(reportData?.instruksi_popt ?: "") }
    var radiusInput by remember { mutableDoubleStateOf(reportData?.radius ?: 0.3) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isImageFullscreen by remember { mutableStateOf(false) }

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
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.padding(top = 40.dp, start = 16.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    val acc = (reportData.confidence?.times(100))?.toInt()
                    if (acc != null) {
                        AssistChip(
                            onClick = {},
                            label = { Text("AKURASI AI: $acc%", color = Color.White) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = if(acc > 70) Color(0xFF2E7D32) else Color.Red),
                            border = null
                        )
                    }
                    reportData.label_ai?.let { Text(text = it, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White) }
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
                Text("Validasi Laporan Lapangan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                DetailItemRow(Icons.Default.Warning, "Hama Terdeteksi", reportData.label_ai, Color(0xFFD32F2F))
                Spacer(modifier = Modifier.height(12.dp))
                DetailItemRow(Icons.Default.LocationOn, "Lokasi Lapangan", reportData.alamat_lengkap ?: "-", Color(0xFF1976D2))
                Spacer(modifier = Modifier.height(12.dp))
                DetailItemRow(Icons.Default.Person, "ID Pelapor (Petani)", reportData.petani_id.take(8), Color(0xFF388E3C))
                Spacer(modifier = Modifier.height(12.dp))
                DetailItemRow(Icons.Default.CalendarToday, "Waktu Lapor", reportData.created_at.replace("T", " ").take(16), Color(0xFFF57C00))

                Spacer(modifier = Modifier.height(30.dp))

                if (updateHistory.isNotEmpty()) {
                    Text("Riwayat Kondisi Terbaru", fontWeight = FontWeight.Bold)
                    updateHistory.forEach { update ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = update.foto_update_url, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Deteksi: ${update.label_ai_update}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(update.catatan, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.EditNote,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Panel Intervensi Ahli",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (reportData.status != "selesai") {
                            // Input Instruksi Teknis
                            OutlinedTextField(
                                value = instruksiInput,
                                onValueChange = { instruksiInput = it },
                                label = { Text("Instruksi Penanganan Mandiri") },
                                placeholder = { Text("Misal: Semprotkan pestisida dosis 2ml/L...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 3,
                                enabled = !isSubmitting
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Slider Radius (Geofencing Control)
                            Text(
                                text = "Radius Peringatan EWS: ${(radiusInput * 1000).toInt()} Meter",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Slider(
                                value = radiusInput.toFloat(),
                                onValueChange = { radiusInput = it.toDouble() },
                                valueRange = 0.1f..2.0f, // 100m sampai 2km
                                steps = 19
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Button Verifikasi (Status: terverifikasi)
                                Button(
                                    onClick = {
                                        isSubmitting = true
                                        scope.launch {
                                            val res = updateLaporanStatus(
                                                reportData.id,
                                                "terverifikasi",
                                                instruksiInput,
                                                radiusInput
                                            )
                                            if (res.isSuccess) {
                                                Toast.makeText(
                                                    context,
                                                    "Laporan Terverifikasi",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                navController.popBackStack()
                                            } else {
                                                Toast.makeText(context, "Gagal: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                            isSubmitting = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(
                                            0xFF2E7D32
                                        )
                                    ),
                                    enabled = !isSubmitting && instruksiInput.isNotBlank()
                                ) {
                                    if (isSubmitting) CircularProgressIndicator(
                                        modifier = Modifier.size(
                                            20.dp
                                        ), color = Color.White
                                    )
                                    else Text("Verifikasi")
                                }
                                // Button Kunjungan (Status: perlu_kunjungan)
                                Button(
                                    onClick = {
                                        isSubmitting = true
                                        scope.launch {
                                            val res = updateLaporanStatus(
                                                reportData.id,
                                                "perlu_kunjungan",
                                                instruksiInput,
                                                radiusInput
                                            )
                                            if (res.isSuccess) {
                                                Toast.makeText(
                                                    context,
                                                    "Status: Perlu Kunjungan",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                navController.popBackStack()
                                            }
                                            isSubmitting = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(
                                            0xFF7B1FA2
                                        )
                                    ),
                                    enabled = !isSubmitting
                                ) {
                                    Text("Kunjungan")
                                }
                            }

                            // button selesai muncul jika status terverifiikasi/perlu kunjungan
                            if (reportData.status == "terverifikasi" || reportData.status == "perlu_kunjungan") {
                                Spacer(Modifier.height(16.dp))

                                if (reportData.status == "perlu_kunjungan") {
                                    OutlinedButton(
                                        onClick = {
                                            navController.navigate("update_lahan/${reportData.id}/${reportData.lat}/${reportData.lon}")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.CameraAlt, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Ambil Foto Bukti Kunjungan")
                                    }
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isSubmitting = true
                                            if (updateHistory.isNotEmpty()) {
                                                markLaporanSelesai(reportData.id)
                                                navController.popBackStack()
                                            } else {
                                                Toast.makeText(context, "Wajib ada bukti foto update lahan sebelum diselesaikan!", Toast.LENGTH_LONG).show()
                                                isSubmitting = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                    enabled = !isSubmitting
                                ) {
                                    Icon(Icons.Default.CheckCircle, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Tandai Lahan Pulih (Selesai)")
                                }
                            }

                            // Button Tolak (Status: ditolak)
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        updateLaporanStatus(
                                            reportData.id,
                                            "ditolak",
                                            "Laporan tidak valid/foto tidak jelas",
                                            0.0
                                        )
                                        navController.popBackStack()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSubmitting
                            ) {
                                Text("Tolak Laporan", color = Color.Red)
                            }
                        } else {
                            Text("Laporan ini telah dinyatakan SELESAI.", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = {
                        // Menggunakan reportData.lat dan reportData.lon langsung
                        val gmmIntentUri = "google.navigation:q=${reportData.lat},${reportData.lon}".toUri()
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        context.startActivity(mapIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Map, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Navigasi ke Sawah")
                }
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun DetailItemRow(icon: ImageVector, label: String, value: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            value?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
        }
    }
}