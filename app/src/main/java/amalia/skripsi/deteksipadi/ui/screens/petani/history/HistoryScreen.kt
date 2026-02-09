package amalia.skripsi.deteksipadi.ui.screens.petani.history

import amalia.skripsi.deteksipadi.data.local.PendingReport
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    historyViewModel: HistoryViewModel
) {
    val state by historyViewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Selesai", "Proses", "Tertunda")

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    title = { Text("Riwayat Laporan", fontWeight = FontWeight.Bold) }
                )
                SecondaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEachIndexed { index, title ->
                        val count = when(index) {
                            0 -> state.finishedList.size
                            1 -> state.processList.size
                            else -> state.pendingList.size
                        }
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, fontSize = 13.sp)
                                    if (count > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Badge { Text(count.toString()) }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val currentItems = when (selectedTabIndex) {
                    0 -> state.finishedList
                    1 -> state.processList
                    else -> state.pendingList
                }

                if (currentItems.isEmpty()) {
                    EmptyHistoryState()
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(currentItems) { item ->
                            HistoryReportCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryReportCard(item: Any) {
    val context = LocalContext.current

    val imageUrl: Any
    val label: String
    val confidence: Float
    val status: String
    val time: String
    val isFromLocal: Boolean

    if (item is RemoteReport) {
        imageUrl = item.image_url
        label = item.ai_label
        confidence = item.confidence
        status = item.status
        time = item.created_at.take(10)
        isFromLocal = false
    } else {
        val pending = item as PendingReport
        imageUrl = File(pending.imagePath)
        label = pending.label
        confidence = pending.confidence
        status = "pending_local"
        time = "Baru Saja"
        isFromLocal = true
    }

    val (statusColor, containerColor) = when {
        isFromLocal -> {
            MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        }
        status.lowercase() == "pending" -> {
            // Biru untuk sedang diproses
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        }
        status.lowercase() == "verified" -> {
            // Hijau untuk selesai
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        }
        else -> {
            // Merah untuk ditolak/error
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.size(70.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(color = containerColor, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = if(isFromLocal) "TERTUNDA" else status.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = confidence,
                        modifier = Modifier.width(80.dp).height(6.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${(confidence * 100).toInt()}% Akurat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(24.dp)) {
            Icon(Icons.Default.History, null, Modifier.size(48.dp), Color.Gray)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Belum ada riwayat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("Semua laporan Anda akan tersimpan di sini", color = Color.Gray, fontSize = 14.sp)
    }
}