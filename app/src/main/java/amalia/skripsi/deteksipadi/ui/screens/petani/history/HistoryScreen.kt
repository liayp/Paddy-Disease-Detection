package amalia.skripsi.deteksipadi.ui.screens.petani.history

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.local.PendingReport
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun rememberConnectivityState(context: Context): State<Boolean> {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isConnected = remember { mutableStateOf(false) }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isConnected.value = true }
            override fun onLost(network: Network) { isConnected.value = false }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isConnected.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivityManager.registerNetworkCallback(request, callback)
        }

        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        isConnected.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    return isConnected
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    historyViewModel: HistoryViewModel,
    onNavigateToDetail: (LaporanDto) -> Unit
) {
    val context = LocalContext.current
    val state by historyViewModel.uiState.collectAsState()
    val isOnline by rememberConnectivityState(context)

    LaunchedEffect(isOnline) {
        historyViewModel.loadHistory()
    }

    val tabs = listOf("Menunggu", "Diproses", "Selesai")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.primary,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Riwayat Laporan", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp
        ) {
            Column {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        val count = when (index) {
                            0 -> state.pendingLocalList.size + (if (isOnline) state.waitingRemoteList.size else 0)
                            1 -> if (isOnline) state.processRemoteList.size else 0
                            2 -> if (isOnline) state.finishedRemoteList.size else 0
                            else -> 0
                        }

                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (count > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Badge(containerColor = MaterialTheme.colorScheme.error) { Text(count.toString()) }
                                    }
                                }
                            }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    when (page) {
                        0 -> { // TAB MENUNGGU (Local + Remote Menunggu)
                            val combinedList = state.pendingLocalList + state.waitingRemoteList

                            if (combinedList.isEmpty()) {
                                EmptyHistoryState(isOffline = false, message = "Belum ada laporan yang menunggu verifikasi.")
                            } else {
                                LazyColumn(contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.pendingLocalList) { report -> PendingReportCard(report) }
                                    items(state.waitingRemoteList) { report -> RemoteReportCard(report, onClick = { onNavigateToDetail(report) }) }
                                }
                            }
                        }
                        1 -> { // TAB DIPROSES (Perlu Kunjungan & Terverifikasi)
                            if (!isOnline) {
                                EmptyHistoryState(isOffline = true)
                            } else if (state.isLoading) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            } else if (state.processRemoteList.isEmpty()) {
                                EmptyHistoryState(isOffline = false, message = "Tidak ada laporan yang sedang ditindaklanjuti POPT.")
                            } else {
                                LazyColumn(contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.processRemoteList) { report -> RemoteReportCard(report, onClick = { onNavigateToDetail(report) }) }
                                }
                            }
                        }
                        2 -> { // TAB SELESAI (Selesai & Ditolak)
                            if (!isOnline) {
                                EmptyHistoryState(isOffline = true)
                            } else if (state.isLoading) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            } else if (state.finishedRemoteList.isEmpty()) {
                                EmptyHistoryState(isOffline = false, message = "Belum ada laporan yang berstatus selesai/ditolak.")
                            } else {
                                LazyColumn(contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.finishedRemoteList) { report -> RemoteReportCard(report, onClick = { onNavigateToDetail(report) }) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteReportCard(report: LaporanDto, onClick: () -> Unit) {
    val context = LocalContext.current

    // Warna Teks Status yang bervariasi
    val statusColor = when(report.status) {
        "ditolak" -> Color(0xFFD32F2F)
        "selesai" -> Color(0xFF388E3C)
        "terverifikasi" -> Color(0xFF1976D2)
        "perlu_kunjungan" -> Color(0xFF7B1FA2)
        else -> Color(0xFFF57C00)
    }

    val displayStatus = report.status.replace("_", " ").uppercase()
    val displayLabel = report.label_ai ?: "Belum Teridentifikasi"
    val displayAddress = report.alamat_lengkap ?: "Lokasi tidak diketahui"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(report.foto_url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = displayStatus,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(report.created_at.take(10), style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
                Text(displayLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(displayAddress, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun PendingReportCard(report: PendingReport) {
    val displayLabel = report.label.ifBlank { "Belum Teridentifikasi" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = File(report.imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Surface(color = Color(0xFFD32F2F).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "MENUNGGU UPLOAD JARINGAN",
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(displayLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("${report.addressDetail}, Kec. ${report.kecamatan}", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun EmptyHistoryState(isOffline: Boolean, message: String = "Laporan Anda akan tersimpan di sini.") {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape).padding(24.dp)) {
            Icon(imageVector = if (isOffline) Icons.Default.Warning else Icons.Default.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = if (isOffline) MaterialTheme.colorScheme.error else Color.Gray)
        }
        Spacer(Modifier.height(16.dp))
        Text(text = if (isOffline) "Koneksi Terputus" else "Belum ada laporan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(text = if (isOffline) "Riwayat akan tampil saat terhubung ke internet." else message, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
    }
}