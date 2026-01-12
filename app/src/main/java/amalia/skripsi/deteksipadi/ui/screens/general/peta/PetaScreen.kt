package amalia.skripsi.deteksipadi.ui.screens.general.peta

import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.fetchActiveHotspots
import amalia.skripsi.deteksipadi.ui.screens.general.peta.LocationUtils
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

@Composable
fun PetaScreen(navController: NavController, petaViewModel: PetaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- State Data ---
    var hotspots by remember { mutableStateOf<List<HotspotDto>>(emptyList()) }
    var isInDanger by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // State Izin Lokasi (Default False supaya tidak crash)
    var hasLocationPermission by remember { mutableStateOf(false) }

    // --- Camera State ---
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-6.200, 106.816), 10f)
    }

    // Launcher untuk meminta izin
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            // Jika diizinkan, refresh lokasi user
            // (Trigger ulang logic lokasi bisa ditambahkan di sini jika perlu)
        }
    }

    // 1. Cek Izin & Load Data Awal
    LaunchedEffect(Unit) {
        hotspots = fetchActiveHotspots()
        isLoading = false

        // Cek apakah izin sudah ada sebelumnya
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
        } else {
            // Jika belum, minta izin
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // 2. Logic Lokasi & Geofencing (Hanya jalan jika izin active)
    LaunchedEffect(hasLocationPermission, hotspots) {
        if (hasLocationPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val userLatLng = LatLng(loc.latitude, loc.longitude)
                        // Pindahkan kamera ke user
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                        // Cek Bahaya
                        isInDanger = LocationUtils.isUserInDangerZone(loc.latitude, loc.longitude, hotspots)
                    }
                }
            } catch (e: SecurityException) {
                // Should not happen check permission flag
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                // FIX CRASH: Hanya aktifkan MyLocation jika izin sudah true
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission)
            ) {
                hotspots.forEach { spot ->
                    val spotLoc = LatLng(spot.lat, spot.lon)
                    Marker(
                        state = MarkerState(position = spotLoc),
                        title = spot.ai_label,
                        snippet = "Waspada! Serangan aktif."
                    )
                    Circle(
                        center = spotLoc,
                        radius = 300.0,
                        fillColor = Color(0x33FF0000),
                        strokeColor = Color.Red,
                        strokeWidth = 2f
                    )
                }
            }

            // Kartu hanya muncul jika lokasi diizinkan
            if (hasLocationPermission) {
                HazardAlertCard(
                    isInDanger = isInDanger,
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                )
            } else {
                // Pesan jika izin ditolak (Opsional)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                ) {
                    Text("Izin lokasi diperlukan untuk fitur deteksi bahaya.", Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun HazardAlertCard(isInDanger: Boolean, modifier: Modifier = Modifier) {
    val containerColor = if (isInDanger) Color(0xFFFFEBEE) else Color(0xFFE8F5E9) // Merah Muda vs Hijau Muda
    val contentColor = if (isInDanger) Color.Red else Color(0xFF2E7D32) // Merah vs Hijau Tua
    val icon = if (isInDanger) Icons.Default.Warning else Icons.Default.CheckCircle
    val title = if (isInDanger) "PERINGATAN ZONA MERAH!" else "LOKASI ANDA AMAN"
    val desc = if (isInDanger) "Anda berada dalam radius 300m dari serangan hama." else "Tidak ada laporan hama aktif dalam radius 300m."

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black
                )
            }
        }
    }
}