package amalia.skripsi.deteksipadi.ui.screens.general.peta

import amalia.skripsi.deteksipadi.util.LocationAccessPreference

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.supabase
import amalia.skripsi.deteksipadi.services.HazardDetectionService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.math.abs

fun isOnlineMap(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
    return capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
fun rememberMapConnectivityState(context: Context): State<Boolean> {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isConnected = remember { mutableStateOf(isOnlineMap(context)) }

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

@Composable
fun PetaScreen(
    navController: NavController,
    petaViewModel: PetaViewModel,
    userRole: String,
    onReportClick: (LaporanDto) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isInDanger by petaViewModel.isDanger.collectAsState()
    val isOnline by rememberMapConnectivityState(context)
    val mapCache = remember { context.getSharedPreferences("peta_cache", Context.MODE_PRIVATE) }
    val mapCacheJson = remember { Json { ignoreUnknownKeys = true } }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var selectedHotspot by remember { mutableStateOf<LaporanDto?>(null) }
    var isProtectionActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isAutoFollowActive by remember { mutableStateOf(true) }
    var mapLoadError by remember { mutableStateOf<String?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.5333, 123.0667), 10f)
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            isAutoFollowActive = false
        }
    }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            runCatching {
                val cached = mapCache.getString("laporan_peta", null) ?: return@runCatching
                val cachedReports = mapCacheJson.decodeFromString(ListSerializer(LaporanDto.serializer()), cached)
                if (cachedReports.isNotEmpty()) petaViewModel.setInitialData(cachedReports)
            }
        }
    }

    LaunchedEffect(isOnline) {
        if (isOnline) {
            try {
                // View khusus peta: hanya data hotspot yang boleh dibagikan lintas pengguna.
                // vw_laporan_peta_public mengecualikan laporan menunggu/ditolak dan membutuhkan lokasi.
                val mapColumns = "id, petani_id, foto_url, label_ai, confidence, status, prioritas, termasuk_cluster, created_at, kecamatan_id, radius, jenis_pelaporan, lat, lon, alamat_lengkap, label_koreksi_popt"
                val list = supabase.from("vw_laporan_peta_public")
                    .select(columns = Columns.raw(mapColumns))
                    .decodeList<LaporanDto>()
                    .filter { report ->
                        report.lat.isFinite() && report.lon.isFinite() &&
                                report.lat in -90.0..90.0 && report.lon in -180.0..180.0 &&
                                !(abs(report.lat) < 1e-6 && abs(report.lon) < 1e-6) &&
                                report.status != "ditolak" && report.status != "menunggu_verifikasi"
                    }
                petaViewModel.setInitialData(list)
                runCatching {
                    mapCache.edit().putString("laporan_peta", mapCacheJson.encodeToString(ListSerializer(LaporanDto.serializer()), list)).apply()
                }
                mapLoadError = null
            } catch (e: Exception) {
                mapLoadError = if (isOnline) "Titik peta belum dapat diperbarui. Periksa koneksi lalu coba lagi." else "Koneksi terputus. Titik yang ditampilkan mungkin merupakan data terakhir yang tersimpan."
                android.util.Log.e("PetaScreen", "Gagal memuat vw_laporan_peta_public", e)
                Toast.makeText(context, mapLoadError.orEmpty(), Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        // Izin lokasi hanya diminta dari Pengaturan aplikasi, bukan otomatis saat membuka peta.
        hasLocationPermission = LocationAccessPreference.canUseLocation(context)
    }

    LaunchedEffect(hasLocationPermission, petaViewModel.filteredHotspots) {
        // Periksa izin Android secara eksplisit agar pemanggilan lastLocation
        // tidak bergantung hanya pada state izin yang mungkin sudah kedaluwarsa.
        val hasFinePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val locationEnabledInApp = LocationAccessPreference.isEnabled(context)
        if (!locationEnabledInApp || (!hasFinePermission && !hasCoarsePermission)) {
            userLocation = null
            return@LaunchedEffect
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        try {
            client.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null && LocationAccessPreference.canUseLocation(context)) {
                        val newLatLng = LatLng(loc.latitude, loc.longitude)
                        userLocation = newLatLng
                        petaViewModel.updateHazardLocation(loc.latitude, loc.longitude)
                        if (isAutoFollowActive) {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(newLatLng, 16f)
                                )
                            }
                        }
                    }
                }
                .addOnFailureListener { error ->
                    android.util.Log.w("PetaScreen", "Gagal membaca lokasi terakhir", error)
                }
        } catch (error: SecurityException) {
            // Izin mungkin dicabut setelah pemeriksaan izin di atas.
            userLocation = null
            android.util.Log.w("PetaScreen", "Izin lokasi tidak tersedia saat membaca lokasi", error)
        } catch (error: Exception) {
            android.util.Log.w("PetaScreen", "Tidak dapat membaca lokasi terakhir", error)
        }
    }

    // LOGIKA ZOOM OTOMATIS SAAT FILTER KECAMATAN AKTIF
    LaunchedEffect(petaViewModel.selectedKecamatanList) {
        // Hanya jalan jika ada kecamatan yang di-filter dan poligonnya tersedia
        if (petaViewModel.selectedKecamatanList.isNotEmpty() && petaViewModel.currentPolygons.isNotEmpty()) {
            try {
                val boundsBuilder = LatLngBounds.Builder()
                var hasPoints = false

                // Kumpulkan semua titik koordinat dari poligon yang terpilih
                petaViewModel.currentPolygons.forEach { polygonPoints ->
                    polygonPoints.forEach { point ->
                        boundsBuilder.include(point)
                        hasPoints = true
                    }
                }

                if (hasPoints) {
                    val bounds = boundsBuilder.build()
                    isAutoFollowActive = false
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(bounds, 150)
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleService(active: Boolean) {
        val intent = Intent(context, HazardDetectionService::class.java)
        if (active) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            isProtectionActive = true
            Toast.makeText(context, "Alarm Pantau Aktif", Toast.LENGTH_SHORT).show()
        } else {
            context.stopService(intent)
            isProtectionActive = false
            Toast.makeText(context, "Alarm Dimatikan", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false
            ),
            onMapClick = { selectedHotspot = null }
        ) {
            petaViewModel.currentPolygons.forEach { polygonPoints ->
                Polygon(
                    points = polygonPoints,
                    fillColor = Color(0x220078D4),
                    strokeColor = Color(0xFF0078D4),
                    strokeWidth = 3f
                )
            }

            petaViewModel.filteredHotspots.forEach { spot ->
                val pos = LatLng(spot.lat, spot.lon)

                val fillColor = when(spot.prioritas) {
                    "tinggi" -> Color(0x4DFF0000)
                    "sedang" -> Color(0x4DFFA500)
                    else -> Color(0x4DFFEB3B)
                }

                val strokeColor = when(spot.prioritas) {
                    "tinggi" -> Color.Red
                    "sedang" -> Color(0xFFFFA500)
                    else -> Color(0xFFFBC02D)
                }

                Circle(
                    center = pos,
                    radius = 300.0,
                    fillColor = fillColor,
                    strokeColor = strokeColor,
                    strokeWidth = 4f
                )
                Marker(
                    state = MarkerState(position = pos),
                    title = spot.label_efektif,
                    snippet = "${spot.alamat_lengkap?.takeIf { it.isNotBlank() } ?: "Lokasi laporan"} • ${statusLabelPeta(spot.status)}",
                    onClick = {
                        selectedHotspot = spot
                        true
                    }
                )
            }
        }

        if (petaViewModel.polygonLoadError != null || mapLoadError != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                color = Color(0xFFFFF3CD),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = petaViewModel.polygonLoadError ?: mapLoadError.orEmpty(),
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF664D03),
                    fontSize = 12.sp
                )
            }
        }

        if (hasLocationPermission) {
            StatusPill(
                isInDanger = isInDanger,
                isOffline = !isOnline,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                SmallFloatingActionButton(
                    onClick = { showSettingsDialog = true },
                    containerColor = Color.White,
                    shape = CircleShape
                ) {
                    Box {
                        Icon(Icons.Default.Notifications, "Settings")
                        if (isProtectionActive) {
                            Box(Modifier.size(10.dp).background(Color.Green, CircleShape).align(Alignment.TopEnd))
                        }
                    }
                }

                SmallFloatingActionButton(
                    onClick = {
                        if (isOnline) navController.navigate("filter_screen")
                        else Toast.makeText(context, "Sambungkan internet untuk memfilter", Toast.LENGTH_SHORT).show()
                    },
                    containerColor = if (isOnline) Color.White else Color.LightGray,
                    contentColor = if (isOnline) Color(0xFF0078D4) else Color.DarkGray
                ) {
                    Icon(Icons.Default.FilterList, "Filter")
                }

                FloatingActionButton(
                    onClick = {
                        isAutoFollowActive = true
                        userLocation?.let {
                            scope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f))
                            }
                        }
                    },
                    containerColor = if (isAutoFollowActive) Color(0xFF0078D4) else Color.White,
                    contentColor = if (isAutoFollowActive) Color.White else Color.Black,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isAutoFollowActive) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                        contentDescription = "Center Location"
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedHotspot != null && isOnline,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
        ) {
            selectedHotspot?.let { spot ->
                val distanceText = if (userLocation == null) "Menghitung..."
                else calculateDistanceString(userLocation!!, LatLng(spot.lat, spot.lon))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().then(
                        if (userRole.equals("popt", ignoreCase = true) || userRole.equals("admin", ignoreCase = true)) {
                            Modifier.clickable { onReportClick(spot) }
                        } else Modifier
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(spot.foto_url).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                spot.label_efektif?.let { Text(text = it, fontWeight = FontWeight.Bold) }
                                if (spot.prioritas == "tinggi") {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = Color.Red, shape = RoundedCornerShape(4.dp)) {
                                        Text("URGENT", color = Color.White, fontSize = 8.sp, modifier = Modifier.padding(2.dp))
                                    }
                                }
                            }
                            Text(text = distanceText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(text = spot.alamat_lengkap ?: "Lokasi tidak diketahui", style = MaterialTheme.typography.labelSmall, maxLines = 1, color = Color.Gray)
                        }
                        if (userRole.equals("popt", ignoreCase = true) || userRole.equals("admin", ignoreCase = true)) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Mode Pantau (EWS)") },
                text = {
                    Column {
                        Text("Aktifkan alarm untuk mendapatkan notifikasi suara jika Anda memasuki radius bahaya hama.")
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isProtectionActive) "Status: AKTIF" else "Status: MATI", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = isProtectionActive, onCheckedChange = { toggleService(it) })
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Tutup") } }
            )
        }
    }
}

@Composable
fun StatusPill(isInDanger: Boolean, isOffline: Boolean, modifier: Modifier = Modifier) {
    val bgColor = when {
        isOffline -> Color(0xFFEEEEEE)
        isInDanger -> Color(0xFFFFEBEE)
        else -> Color(0xFFE8F5E9)
    }

    val contentColor = when {
        isOffline -> Color.DarkGray
        isInDanger -> Color.Red
        else -> Color(0xFF2E7D32)
    }

    val icon = when {
        isOffline -> Icons.Default.CloudOff
        isInDanger -> Icons.Default.Warning
        else -> Icons.Default.CheckCircle
    }

    val text = when {
        isOffline -> "KONEKSI TERPUTUS"
        isInDanger -> "ZONA BAHAYA HAMA!"
        else -> "LOKASI ANDA AMAN"
    }

    Surface(
        modifier = modifier.shadow(6.dp, CircleShape),
        color = bgColor,
        shape = CircleShape,
        border = if (isInDanger && !isOffline) androidx.compose.foundation.BorderStroke(1.dp, Color.Red) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                fontSize = 14.sp
            )
        }
    }
}

@SuppressLint("DefaultLocale")
fun calculateDistanceString(userLoc: LatLng, spotLoc: LatLng): String {
    val results = FloatArray(1)
    Location.distanceBetween(userLoc.latitude, userLoc.longitude, spotLoc.latitude, spotLoc.longitude, results)
    val distanceMeters = results[0]
    return if (distanceMeters < 1000) "${distanceMeters.toInt()} m dari Anda"
    else String.format("%.1f km dari Anda", distanceMeters / 1000)
}


private fun statusLabelPeta(status: String): String = when (status.trim().lowercase()) {
    "menunggu_verifikasi" -> "Menunggu verifikasi"
    "penanganan_mandiri" -> "Penanganan mandiri"
    "perlu_kunjungan" -> "Perlu kunjungan POPT"
    "selesai" -> "Selesai"
    "ditolak" -> "Ditolak"
    else -> status.replace('_', ' ')
}
