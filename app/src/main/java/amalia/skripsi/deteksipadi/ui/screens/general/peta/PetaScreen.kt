package amalia.skripsi.deteksipadi.ui.screens.general.peta

import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.fetchActiveHotspots
import amalia.skripsi.deteksipadi.services.HazardDetectionService
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun PetaScreen(navController: NavController, petaViewModel: PetaViewModel) {
    val context = LocalContext.current

    // --- State Data ---
    var hotspots by remember { mutableStateOf<List<HotspotDto>>(emptyList()) }
    var isInDanger by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    // State UI Interaction
    var isProtectionActive by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionGuide by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-6.200, 106.816), 10f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    // Load Data
    LaunchedEffect(Unit) {
        hotspots = fetchActiveHotspots()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Realtime Location Update
    LaunchedEffect(hasLocationPermission, hotspots) {
        if (hasLocationPermission) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            try {
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f))
                        isInDanger = LocationUtils.isUserInDangerZone(loc.latitude, loc.longitude, hotspots)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Toggle Logic
    fun toggleService(active: Boolean) {
        val intent = Intent(context, HazardDetectionService::class.java)
        if (active) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    showPermissionGuide = true
                    return
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            isProtectionActive = true
            Toast.makeText(context, "Mode Jaga Aktif", Toast.LENGTH_SHORT).show()
        } else {
            context.stopService(intent)
            isProtectionActive = false
            Toast.makeText(context, "Mode Jaga Mati", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. MAP LAYER
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        ) {
            hotspots.forEach { spot ->
                val pos = LatLng(spot.lat, spot.lon)
                Circle(center = pos, radius = 300.0, fillColor = Color(0x33FF0000), strokeColor = Color.Red, strokeWidth = 1f)
                Marker(state = MarkerState(position = pos), title = spot.ai_label)
            }
        }

        // 2. TOP UI LAYER (HEADER)
        // Kita gunakan Row atau Box alignment untuk mensejajarkan
        if (hasLocationPermission) {
            // A. STATUS PILL (Tengah Atas)
            StatusPill(
                isInDanger = isInDanger,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp) // Jarak dari status bar
            )

            // B. SETTINGS BUTTON (Kanan Atas - Sejajar)
            // Tombol ini posisinya absolut di kanan atas, sejajar secara vertikal karena padding top-nya sama (48.dp)
            SmallFloatingActionButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp), // Padding Top sama dengan Status Pill
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Box {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                    // Dot Hijau jika aktif
                    if (isProtectionActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.Green, CircleShape)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }

        // --- DIALOGS ---
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                icon = { Icon(Icons.Default.Notifications, null) },
                title = { Text("Alarm Peringatan Hama") },
                text = {
                    Column {
                        Text("Aktifkan ini agar HP berbunyi keras saat Anda memasuki area hama (walau HP dikunci).")
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Status Alarm", fontWeight = FontWeight.Bold)
                                Text(if (isProtectionActive) "ON (Memantau)" else "OFF", fontSize = 12.sp, color = if (isProtectionActive) Color(0xFF2E7D32) else Color.Gray)
                            }
                            Switch(
                                checked = isProtectionActive,
                                onCheckedChange = { toggleService(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF2E7D32))
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Tutup") } }
            )
        }

        if (showPermissionGuide) {
            AlertDialog(
                onDismissRequest = { showPermissionGuide = false },
                title = { Text("Izin Lokasi Background") },
                text = { Text("Mohon ubah izin lokasi menjadi 'Allow all the time' (Sepanjang Waktu) di pengaturan agar alarm bisa jalan saat HP di saku.") },
                confirmButton = {
                    Button(onClick = {
                        showPermissionGuide = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }
                        context.startActivity(intent)
                    }) { Text("Buka Pengaturan") }
                },
                dismissButton = { TextButton(onClick = { showPermissionGuide = false }) { Text("Batal") } }
            )
        }
    }
}

@Composable
fun StatusPill(isInDanger: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.shadow(6.dp, CircleShape), // Shadow sedikit lebih tebal biar kontras dengan peta
        color = Color.White,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), // Padding dalam agak besar biar enak dilihat
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
                text = if (isInDanger) "ZONA HAMA!" else "LOKASI AMAN",
                fontWeight = FontWeight.Bold,
                color = if (isInDanger) Color.Red else Color(0xFF2E7D32),
                fontSize = 13.sp
            )
        }
    }
}