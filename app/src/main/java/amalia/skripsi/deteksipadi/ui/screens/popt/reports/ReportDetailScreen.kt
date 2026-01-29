package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import amalia.skripsi.deteksipadi.data.HotspotDto
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    navController: NavController,
    // Kita passing objek HotspotDto langsung biar cepat (bisa juga by ID fetch ulang)
    reportData: HotspotDto?
) {
    val context = LocalContext.current

    if (reportData == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Data tidak ditemukan") }
        return
    }

    Scaffold(
        floatingActionButton = {
            // Tombol Aksi Utama: Buka Google Maps
            ExtendedFloatingActionButton(
                onClick = {
                    val gmmIntentUri =
                        "google.navigation:q=${reportData.lat},${reportData.lon}".toUri()
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    context.startActivity(mapIntent)
                },
                icon = { Icon(Icons.Default.Map, null) },
                text = { Text("Navigasi ke Lokasi") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(reportData.image_url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Bukti Foto",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient Gelap di Bawah Gambar (Agar teks terbaca)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 100f
                            )
                        )
                )

                // Tombol Back Floating
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .padding(top = 48.dp, start = 16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }

                // Judul Hama di atas Gambar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("CONFIDENCE SCORE TINGGI", color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        border = null
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = reportData.ai_label,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Terdeteksi di area persawahan warga",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // --- KONTEN DETAIL (Card Style) ---
            Column(
                modifier = Modifier
                    .offset(y = (-20).dp) // Efek menumpuk sedikit ke atas
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(24.dp)
            ) {

                // Indikator Dekorasi (Garis kecil di tengah)
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color.LightGray, CircleShape)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Detail Laporan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Informasi dalam Grid/List
                DetailItemRow(
                    icon = Icons.Default.Warning,
                    label = "Jenis Hama",
                    value = reportData.ai_label,
                    color = Color(0xFFD32F2F)
                )

                Spacer(modifier = Modifier.height(12.dp))

                DetailItemRow(
                    icon = Icons.Default.LocationOn,
                    label = "Koordinat Lokasi",
                    value = "${reportData.lat}, ${reportData.lon}",
                    color = Color(0xFF1976D2)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bagian ini nanti bisa diambil dari database jika reportData punya field 'district' atau 'reporter_name'
                DetailItemRow(
                    icon = Icons.Default.Person,
                    label = "Pelapor (Petani)",
                    value = "Mitra Tani (ID: ${reportData.id.take(5)}...)",
                    color = Color(0xFF388E3C)
                )

                Spacer(modifier = Modifier.height(12.dp))

                DetailItemRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Waktu Laporan",
                    value = "Baru Saja (Realtime)", // Nanti ambil created_at dari DTO
                    color = Color(0xFFF57C00)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --- AREA TINDAKAN POPT ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Instruksi Penanganan:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Lakukan verifikasi visual di lapangan.\n2. Jika valid, koordinasikan penyemprotan.\n3. Tandai status 'Selesai' jika hama teratasi.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { /* TODO: Update status report jadi 'handled' di database */ },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.CheckCircle, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifikasi & Tandai Selesai")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp)) // Ruang untuk FAB
            }
        }
    }
}

// Komponen Baris Info yang Cantik
@Composable
fun DetailItemRow(icon: ImageVector, label: String, value: String, color: Color) {
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
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}