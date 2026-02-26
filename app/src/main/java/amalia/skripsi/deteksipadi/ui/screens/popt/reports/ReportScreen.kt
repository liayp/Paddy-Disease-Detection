@file:Suppress("DEPRECATION")

package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import amalia.skripsi.deteksipadi.data.HotspotDto
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.util.*


fun isOnlinePopt(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
    return capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
}

@Composable
fun rememberPoptConnectivityState(context: Context): State<Boolean> {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isConnected = remember { mutableStateOf(isOnlinePopt(context)) }

    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isConnected.value = true }
            override fun onLost(network: Network) { isConnected.value = false }
        }

        cm.registerDefaultNetworkCallback(callback)

        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return isConnected
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PoptReportsScreen(
    navController: NavController,
    viewModel: PoptReportsViewModel,
    onReportClick: (HotspotDto) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val tabs = listOf("Proses", "Selesai")
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showManualPicker by remember { mutableStateOf(false) }

    // DETEKSI JARINGAN REALTIME
    val isConnected by rememberPoptConnectivityState(context)

    // AUTO-RELOAD SAAT JARINGAN KEMBALI
    LaunchedEffect(isConnected) {
        if (isConnected) {
            viewModel.loadPoptInitialData()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.primary,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Laporan Wilayah Binaan", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0),
                actions = {
                    // Tombol download hanya bisa diklik jika ada internet (agar tidak error)
                    if (isConnected) {
                        IconButton(onClick = { showDownloadSheet = true }) {
                            Icon(Icons.Default.Download, null, tint = Color.White)
                        }
                    }
                },
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
                            height = 3.dp
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        // Jika offline, sembunyikan angka badge
                        val count = if (!isConnected) 0 else if (index == 0) state.processList.size else state.finishedList.size

                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
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

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
                    val currentItems = if (page == 0) state.processList else state.finishedList

                    // LOGIKA RENDER BERDASARKAN JARINGAN
                    when {
                        // Jika Offline, Blokir Layar Langsung
                        !isConnected -> {
                            EmptyPoptState(isOffline = true)
                        }

                        // Loading State
                        state.isLoading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        // Kosong tapi Online
                        currentItems.isEmpty() -> {
                            EmptyPoptState(isOffline = false)
                        }

                        // Normal (Ada Internet & Ada Data)
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(currentItems) {
                                    PoptReportCard(it, onClick = { onReportClick(it) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloadSheet) {
        ModalBottomSheet(onDismissRequest = { showDownloadSheet = false }) {
            Column(Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Download Riwayat Laporan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { showManualPicker = true }, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pilih Bulan & Tahun Lainnya", fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.CalendarMonth, null)
                    }
                }
                Spacer(Modifier.height(12.dp))
                viewModel.getLastThreeMonths().forEach { (label, date) ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        viewModel.prepareExportPreview(date.first, date.second)
                        showDownloadSheet = false
                        navController.navigate("report_preview")
                    }, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label); Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
        }
    }

    if (showManualPicker) {
        val months = listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        var tempM by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
        var tempY by remember { mutableIntStateOf(currentYear) }

        AlertDialog(
            onDismissRequest = { showManualPicker = false },
            title = { Text("Pilih Periode", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { if(tempM > 0) tempM-- }) { Icon(Icons.Default.Remove, null) }
                        Text(months[tempM], Modifier.width(100.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { if(tempM < 11) tempM++ }) { Icon(Icons.Default.Add, null) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { tempY-- }) { Icon(Icons.Default.Remove, null) }
                        Text(tempY.toString(), Modifier.width(100.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { tempY++ }) { Icon(Icons.Default.Add, null) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.prepareExportPreview(tempM, tempY)
                    showManualPicker = false; showDownloadSheet = false
                    navController.navigate("report_preview")
                }) { Text("Pilih") }
            }
        )
    }
}

@Composable
fun PoptReportCard(item: HotspotDto, onClick: () -> Unit) {
    val context = LocalContext.current
    val statusColor = if (item.status.lowercase() == "pending") Color(0xFFF57C00) else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.size(70.dp)) {
                AsyncImage(model = ImageRequest.Builder(context).data(item.image_url).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text(item.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Text(item.created_at.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(item.ai_label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Kec. ${item.kecamatan}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = item.confidence.toFloat(), modifier = Modifier.width(80.dp).height(6.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("${(item.confidence * 100).toInt()}% Akurat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun EmptyPoptState(isOffline: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape).padding(24.dp)) {
            Icon(
                imageVector = if (isOffline) Icons.Default.Warning else Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isOffline) MaterialTheme.colorScheme.error else Color.Gray
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isOffline) "Koneksi Terputus" else "Tidak ada laporan",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (isOffline) "Laporan akan tampil jika perangkat terhubung ke internet." else "Laporan wilayah binaan Anda akan muncul di sini",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
