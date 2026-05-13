@file:Suppress("DEPRECATION")
package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import amalia.skripsi.deteksipadi.data.LaporanDto
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
import androidx.compose.ui.text.style.TextOverflow
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
    onReportClick: (LaporanDto) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    val tabs = listOf("Menunggu", "Kunjungan", "Verifikasi", "Selesai", "Ditolak")
    val pagerState = rememberPagerState { tabs.size }

    val scope = rememberCoroutineScope()
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showManualPicker by remember { mutableStateOf(false) }
    var showKecamatanDropdown by remember { mutableStateOf(false) }

    val isConnected by rememberPoptConnectivityState(context)

    LaunchedEffect(isConnected) {
        if (isConnected) {
            viewModel.loadPoptInitialData()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.primary,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Laporan Wilayah", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0),
                actions = {
                    if (isConnected) {
                        IconButton(onClick = { showDownloadSheet = true }) {
                            Icon(Icons.Default.Download, contentDescription = "Download Report", tint = Color.White)
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // REVISI UX: Filter Dropdown Kecamatan
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { if (state.availableKecamatanList.isNotEmpty()) showKecamatanDropdown = true },
                    shape = RoundedCornerShape(50),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterAlt, contentDescription = "Filter", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = state.selectedFilterKecamatan,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                    }
                }

                DropdownMenu(
                    expanded = showKecamatanDropdown,
                    onDismissRequest = { showKecamatanDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.9f).background(Color.White)
                ) {
                    state.availableKecamatanList.forEach { kec ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    kec,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (state.selectedFilterKecamatan == kec) FontWeight.Bold else FontWeight.Normal,
                                    color = if (state.selectedFilterKecamatan == kec) MaterialTheme.colorScheme.primary else Color.DarkGray
                                )
                            },
                            onClick = {
                                viewModel.updateKecamatanFilter(kec)
                                showKecamatanDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 2.dp
            ) {
                Column {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        edgePadding = 16.dp,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                height = 3.dp
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val count = if (!isConnected) 0 else when(index) {
                                0 -> state.menungguList.size
                                1 -> state.kunjunganList.size
                                2 -> state.terverifikasiList.size
                                3 -> state.selesaiList.size
                                4 -> state.ditolakList.size
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

                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
                        val currentItems = when(page) {
                            0 -> state.menungguList
                            1 -> state.kunjunganList
                            2 -> state.terverifikasiList
                            3 -> state.selesaiList
                            4 -> state.ditolakList
                            else -> emptyList()
                        }

                        when {
                            !isConnected -> EmptyPoptState(isOffline = true)
                            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            currentItems.isEmpty() -> EmptyPoptState(isOffline = false, message = "Belum ada laporan di kategori dan wilayah ini.")
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(currentItems) { item ->
                                        PoptReportCard(item, onClick = { onReportClick(item) })
                                    }
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
                Text(
                    text = "Download Rekapitulasi Laporan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Laporan yang diunduh akan otomatis tersaring berdasarkan kecamatan yang Anda pilih saat ini (${state.selectedFilterKecamatan}).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(Modifier.height(20.dp))

                OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { showManualPicker = true }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pilih Bulan & Tahun Lainnya", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))

                viewModel.getLastThreeMonths().forEach { (label, date) ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        viewModel.prepareExportPreview(date.first, date.second)
                        showDownloadSheet = false
                        navController.navigate("report_preview")
                    }, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
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
            title = { Text("Pilih Periode Laporan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { if(tempM > 0) tempM-- }) { Icon(Icons.Default.Remove, null) }
                        Text(months[tempM], Modifier.width(100.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { if(tempM < 11) tempM++ }) { Icon(Icons.Default.Add, null) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { tempY-- }) { Icon(Icons.Default.Remove, null) }
                        Text(tempY.toString(), Modifier.width(100.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { tempY++ }) { Icon(Icons.Default.Add, null) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.prepareExportPreview(tempM, tempY)
                    showManualPicker = false
                    showDownloadSheet = false
                    navController.navigate("report_preview")
                }) { Text("Konfirmasi Pilihan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@Composable
fun PoptReportCard(report: LaporanDto, onClick: () -> Unit) {
    val context = LocalContext.current
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
                            displayStatus,
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
fun EmptyPoptState(isOffline: Boolean, message: String = "Laporan wilayah binaan Anda akan muncul di sini") {
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isOffline) "Laporan akan tampil jika perangkat terhubung ke internet." else message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}