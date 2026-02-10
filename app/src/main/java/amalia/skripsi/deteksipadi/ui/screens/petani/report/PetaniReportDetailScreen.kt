package amalia.skripsi.deteksipadi.ui.screens.petani.report

import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.ui.screens.popt.reports.DetailItemRow
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetaniReportDetailScreen(
    navController: NavController,
    reportData: HotspotDto?
) {
    val context = LocalContext.current
    if (reportData == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Laporan tidak ditemukan") }
        return
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(reportData.image_url).crossfade(true).build(),
                    contentDescription = "Foto Laporan",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.padding(top = 40.dp, start = 16.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    val acc = (reportData.confidence * 100).toInt()
                    AssistChip(
                        onClick = {},
                        label = { Text("Confidence Score: $acc%", color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2E7D32)),
                        border = null
                    )
                    Text(text = reportData.ai_label, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = "Status: ${reportData.status.uppercase()}", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                }
            }

            Column(
                modifier = Modifier.offset(y = (-20).dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(24.dp)
            ) {
                Text("Informasi Laporan Anda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(20.dp))

                DetailItemRow(Icons.Default.BugReport, "Hasil Deteksi AI", reportData.ai_label, Color(0xFFD32F2F))
                Spacer(modifier = Modifier.height(12.dp))
                DetailItemRow(Icons.Default.LocationOn, "Lokasi Temuan", "Kec. ${reportData.kecamatan ?: "-"}, Kel. ${reportData.kelurahan ?: "-"}", Color(0xFF1976D2))
                Spacer(modifier = Modifier.height(12.dp))
                DetailItemRow(Icons.Default.HomeWork, "Alamat Detail", reportData.address_detail ?: "Detail alamat tidak tersedia", Color(0xFF455A64))
                Spacer(modifier = Modifier.height(12.dp))
                DetailItemRow(Icons.Default.Event, "Waktu Pelaporan", reportData.created_at.replace("T", " ").take(16), Color(0xFFF57C00))

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = {
                        val gmmIntentUri = "google.navigation:q=${reportData.lat},${reportData.lon}".toUri()
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
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