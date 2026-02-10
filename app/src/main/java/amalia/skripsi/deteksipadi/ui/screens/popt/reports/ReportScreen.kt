package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.fetchActiveHotspots
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun PoptReportsScreen(
    navController: NavController,
    onReportClick: (HotspotDto) -> Unit
) {
    var reports by remember { mutableStateOf<List<HotspotDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Filter: Hanya tampilkan tugas yang statusnya 'pending'
        reports = fetchActiveHotspots().filter { it.status.lowercase() == "pending" }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Text(
            text = "Tugas Verifikasi",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Daftar laporan masuk yang memerlukan validasi lapangan",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Text("Semua tugas selesai", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reports) { report ->
                    PoptReportItemCard(report = report, onClick = { onReportClick(report) })
                }
            }
        }
    }
}

@Composable
fun PoptReportItemCard(report: HotspotDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(report.image_url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(90.dp).clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                val acc = (report.confidence * 100).toInt()
                Text(
                    text = "Akurasi AI: $acc%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if(acc > 70) Color(0xFF2E7D32) else Color.Red,
                    fontWeight = FontWeight.Bold
                )
                Text(text = report.ai_label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Wilayah: Kec. ${report.kecamatan ?: "-"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(
                    text = report.created_at.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}