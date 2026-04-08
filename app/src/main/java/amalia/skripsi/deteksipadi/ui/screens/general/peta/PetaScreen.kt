package amalia.skripsi.deteksipadi.ui.screens.general.peta

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.supabase
import amalia.skripsi.deteksipadi.services.HazardDetectionService
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

fun isOnlineMap(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
    return capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
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

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var selectedHotspot by remember { mutableStateOf<LaporanDto?>(null) }
    var isProtectionActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isAutoFollowActive by remember { mutableStateOf(true) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.5333, 123.0667), 10f)
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            isAutoFollowActive = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    // FETCH SELURUH DATA: Tidak ada filter RLS di sini agar POPT bisa melihat tren global
    LaunchedEffect(isOnline) {
        if (isOnline) {
            try {
                val list = supabase.from("laporan")
                    .select(columns = Columns.raw("id, petani_id, foto_url, label_ai, confidence, status, prioritas, termasuk_cluster, alamat_lengkap, created_at, lat, lon")) {
                        filter { neq("status", "ditolak") }
                    }.decodeList<LaporanDto>()
                petaViewModel.setInitialData(list)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(hasLocationPermission, petaViewModel.filteredHotspots) {
        if (hasLocationPermission) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            try {
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val newLatLng = LatLng(loc.latitude, loc.longitude)
                        userLocation = newLatLng
                        petaViewModel.updateHazardLocation(loc.latitude, loc.longitude)
                        if (isAutoFollowActive) {
                            scope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newLatLng, 16f))
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
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
            if (isOnline) {
                petaViewModel.filteredHotspots.forEach { spot ->
                    val pos = LatLng(spot.lat, spot.lon)

                    val fillColor = when(spot.prioritas) {
                        "tinggi" -> Color(0x4DFF0000) // Merah lebih tegas (30%)
                        "sedang" -> Color(0x4DFFA500) // Jingga 30%
                        else -> Color(0x4DFFEB3B)     // Kuning 30%
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
                        title = spot.label_ai,
                        snippet = if (userRole == "popt") "Klik untuk detail validasi" else "Radius Bahaya 300m",
                        onClick = {
                            selectedHotspot = spot
                            true
                        }
                    )
                }
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
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (userRole == "popt") onReportClick(spot)
                    }
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
                                Text(text = spot.label_ai, fontWeight = FontWeight.Bold)
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
                        if (userRole == "popt") Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
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