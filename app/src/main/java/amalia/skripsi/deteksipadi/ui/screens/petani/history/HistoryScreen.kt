package amalia.skripsi.deteksipadi.ui.screens.petani.history

import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.local.PendingReport
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.io.File

fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
}

@Composable
fun rememberNetworkConnectivityState(context: Context): State<Boolean> {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isConnected = remember { mutableStateOf(isOnline(context)) }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnected.value = true
            }
            override fun onLost(network: Network) {
                isConnected.value = false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        }

        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    return isConnected
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    historyViewModel: HistoryViewModel,
    onNavigateToDetail: (HotspotDto) -> Unit
) {
    val context = LocalContext.current
    val state by historyViewModel.uiState.collectAsState()
    val tabs = listOf("Selesai", "Proses", "Tertunda")

    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    val isConnected by rememberNetworkConnectivityState(context)

    LaunchedEffect(isConnected) {
        if (isConnected) {
            historyViewModel.loadAllHistory()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.primary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Riwayat Laporan",
                        fontWeight = FontWeight.Bold
                    )
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp
        ) {

            Column {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(
                                tabPositions[pagerState.currentPage]
                            ),
                            height = 3.dp
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        // Jika offline dan ini tab server (Selesai/Proses), tampilkan badge 0
                        val isRemoteTab = (index == 0 || index == 1)
                        val count = if (!isConnected && isRemoteTab) {
                            0
                        } else {
                            when (index) {
                                0 -> state.finishedList.size
                                1 -> state.processList.size
                                else -> state.pendingList.size
                            }
                        }

                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
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

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->

                    val currentItems = when (page) {
                        0 -> state.finishedList
                        1 -> state.processList
                        else -> state.pendingList
                    }

                    val isRemoteTab = (page == 0 || page == 1)
                    val showOfflineAlert = !isConnected && isRemoteTab

                    // --- PERBAIKAN LOGIKA RENDER (STRICT MODE) ---
                    when {
                        // 1. PRIORITAS UTAMA: Sedang buka tab server TAPI tidak ada internet.
                        // Langsung blokir tampilan datanya dan tunjukkan peringatan.
                        showOfflineAlert -> {
                            EmptyHistoryState(isOffline = true)
                        }

                        // 2. Jika sedang loading dari server
                        state.isLoading -> {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        // 3. Jika internet ada tapi datanya memang kosong
                        currentItems.isEmpty() -> {
                            EmptyHistoryState(isOffline = false)
                        }

                        // 4. Kondisi normal (ada internet, ada data ATAU tab lokal tertunda)
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp, top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(currentItems) { item ->
                                    HistoryReportCard(
                                        item = item,
                                        onClick = {
                                            if (item is RemoteReport) {
                                                val hotspot = HotspotDto(
                                                    id = item.id,
                                                    ai_label = item.ai_label,
                                                    confidence = item.confidence.toDouble(),
                                                    status = item.status,
                                                    lat = 0.0,
                                                    lon = 0.0,
                                                    image_url = item.image_url,
                                                    created_at = item.created_at,
                                                    kecamatan = item.kecamatan ?: "-",
                                                    kelurahan = item.kelurahan ?: "-",
                                                    address_detail = item.address_detail ?: "Detail tidak tersedia"
                                                )
                                                onNavigateToDetail(hotspot)
                                            }
                                        }
                                    )
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
fun HistoryReportCard(
    item: Any,
    onClick: () -> Unit
) {
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
        time = item.created_at.replace("T", " ").take(16)
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
            MaterialTheme.colorScheme.tertiary to
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        }

        status.lowercase() == "pending" -> {
            MaterialTheme.colorScheme.primary to
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        }

        status.lowercase() == "verified" -> {
            val green = MaterialTheme.colorScheme.primary
            green to green.copy(alpha = 0.1f)
        }

        else -> {
            MaterialTheme.colorScheme.error to
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(70.dp)
            ) {
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

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isFromLocal) "TERTUNDA" else status.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            )
                        )
                    }

                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = confidence,
                        modifier = Modifier
                            .width(80.dp)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        "${(confidence * 100).toInt()}% Akurat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryState(isOffline: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    CircleShape
                )
                .padding(24.dp)
        ) {
            Icon(
                imageVector = if (isOffline) Icons.Default.Warning else Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isOffline) MaterialTheme.colorScheme.error else Color.Gray
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isOffline) "Koneksi Terputus" else "Belum ada riwayat",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (isOffline) "Riwayat laporan akan tampil jika perangkat terhubung ke internet."
            else "Semua laporan Anda akan tersimpan di sini",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}