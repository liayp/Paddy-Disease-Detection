package amalia.skripsi.deteksipadi.ui.screens.general.peta

import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.fetchActiveHotspots
import amalia.skripsi.deteksipadi.services.HazardDetectionService
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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

@Composable
fun PetaScreen(
    navController: NavController,
    petaViewModel: PetaViewModel,
    userRole: String,
    onReportClick: (HotspotDto) -> Unit
) {
    val context = LocalContext.current

    // --- Data State ---
    var hotspots by remember { mutableStateOf<List<HotspotDto>>(emptyList()) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    // --- UI State ---
    var selectedHotspot by remember { mutableStateOf<HotspotDto?>(null) }
    var isProtectionActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isInDanger by remember { mutableStateOf(false) } // Indikator Bahaya

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.5333, 123.0667), 10f)
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    // 1. Fetch Data Awal
    LaunchedEffect(Unit) {
        hotspots = fetchActiveHotspots()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // 2. Logic Lokasi Realtime & Cek Bahaya
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            try {
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        userLocation = LatLng(loc.latitude, loc.longitude)

                        // Pindahkan kamera ke user saat pertama kali dapat lokasi
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(userLocation!!, 14f))

                        // LOGIKA GEOFENCING: Cek apakah user masuk radius 300m dari salah satu hama
                        val dangerCheck = hotspots.any { spot ->
                            val results = FloatArray(1)
                            Location.distanceBetween(loc.latitude, loc.longitude, spot.lat, spot.lon, results)
                            results[0] <= 300.0 // Radius 300 meter
                        }
                        isInDanger = dangerCheck
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // 3. Logic Toggle Service (Notifikasi)
    fun toggleService(active: Boolean) {
        val intent = Intent(context, HazardDetectionService::class.java)
        if (active) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isProtectionActive = true
            Toast.makeText(context, "Alarm Pantau Aktif", Toast.LENGTH_SHORT).show()
        } else {
            context.stopService(intent)
            isProtectionActive = false
            Toast.makeText(context, "Alarm Dimatikan", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- LAYER 1: MAP ---
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            onMapClick = { selectedHotspot = null }
        ) {
            hotspots.forEach { spot ->
                val pos = LatLng(spot.lat, spot.lon)

                // Visual Radius Merah (Geofence)
                Circle(
                    center = pos,
                    radius = 300.0,
                    fillColor = Color(0x33FF0000), // Merah Transparan
                    strokeColor = Color.Red,
                    strokeWidth = 2f
                )

                // Pin Hama
                Marker(
                    state = MarkerState(position = pos),
                    title = spot.ai_label,
                    onClick = {
                        selectedHotspot = spot
                        true
                    }
                )
            }
        }

        // --- LAYER 2: HEADER UI (Status & Settings) ---
        if (hasLocationPermission) {
            // A. Status Pill (Aman/Bahaya)
            StatusPill(
                isInDanger = isInDanger,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            )

            // B. Tombol Settings (Untuk Alarm)
            SmallFloatingActionButton(
                onClick = { showSettingsDialog = true }, // <--- DIALOG MUNCUL DISINI
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Box {
                    Icon(Icons.Default.Notifications, "Settings")
                    // Dot hijau kalau service aktif
                    if (isProtectionActive) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(Color.Green, CircleShape)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }

        // --- LAYER 3: FLOATING TOOLTIP CARD ---
        AnimatedVisibility(
            visible = selectedHotspot != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
        ) {
            selectedHotspot?.let { spot ->
                val distanceText = remember(userLocation, spot) {
                    if (userLocation == null) "Menghitung..."
                    else calculateDistanceString(userLocation!!, LatLng(spot.lat, spot.lon))
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (userRole == "popt") onReportClick(spot) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(spot.image_url).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = spot.ai_label, fontWeight = FontWeight.Bold)
                            Text(text = distanceText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        if (userRole == "popt") {
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // --- DIALOG SETTINGS ---
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Notifikasi Peringatan") },
                text = {
                    Column {
                        Text("Nyalakan 'Mode Pantau' agar HP Anda berbunyi otomatis jika memasuki zona merah (radius 300m dari hama), meskipun layar mati.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isProtectionActive) "Status: AKTIF" else "Status: MATI", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(
                                checked = isProtectionActive,
                                onCheckedChange = { toggleService(it) }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) { Text("Tutup") }
                }
            )
        }
    }
}

// --- KOMPONEN STATUS (AMAN/BAHAYA) ---
@Composable
fun StatusPill(isInDanger: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.shadow(6.dp, CircleShape),
        color = if (isInDanger) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
        shape = CircleShape,
        border = if (isInDanger) androidx.compose.foundation.BorderStroke(1.dp, Color.Red) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isInDanger) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isInDanger) Color.Red else Color(0xFF2E7D32),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isInDanger) "ZONA BAHAYA!" else "LOKASI AMAN",
                fontWeight = FontWeight.Bold,
                color = if (isInDanger) Color.Red else Color(0xFF2E7D32),
                fontSize = 14.sp
            )
        }
    }
}

// --- UTILITAS HITUNG JARAK ---
@SuppressLint("DefaultLocale")
fun calculateDistanceString(userLoc: LatLng, spotLoc: LatLng): String {
    val results = FloatArray(1)
    Location.distanceBetween(
        userLoc.latitude, userLoc.longitude,
        spotLoc.latitude, spotLoc.longitude,
        results
    )
    val distanceMeters = results[0]

    return if (distanceMeters < 1000) {
        "${distanceMeters.toInt()} m dari Anda"
    } else {
        String.format("%.1f km dari Anda", distanceMeters / 1000)
    }
}