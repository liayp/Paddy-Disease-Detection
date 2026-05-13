package amalia.skripsi.deteksipadi.ui.screens.general.report

import amalia.skripsi.deteksipadi.ui.screens.general.peta.LocationUtils
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@SuppressLint("UnrememberedMutableState")
@Composable
fun UpdateLahanMapScreen(
    navController: NavController,
    laporanId: String,
    targetLat: Double,
    targetLon: Double
) {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var currentDistance by remember { mutableDoubleStateOf(Double.MAX_VALUE) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val targetLocation = LatLng(targetLat, targetLon)
    val maxRadiusMeters = 20.0

    val isInsideGeofence = currentDistance <= maxRadiusMeters

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetLocation, 19f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        userLocation = LatLng(loc.latitude, loc.longitude)
                        currentDistance = LocationUtils.calculateDistance(loc.latitude, loc.longitude, targetLat, targetLon)
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE))) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = false)
            ) {
                val circleColor = if (isInsideGeofence) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                Circle(
                    center = targetLocation,
                    radius = maxRadiusMeters,
                    fillColor = circleColor.copy(alpha = 0.15f),
                    strokeColor = circleColor,
                    strokeWidth = 4f
                )
                Marker(state = MarkerState(position = targetLocation), title = "Titik Koordinat Sawah")
            }

            // HEADER FLOATING TRANSPARAN
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = paddingValues.calculateTopPadding() + 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    userLocation?.let { cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(it, 19f)) }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 250.dp),
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) { Icon(Icons.Default.MyLocation, contentDescription = "Pusatkan Lokasi") }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = paddingValues.calculateBottomPadding() + 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (userLocation == null) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Mengunci sinyal GPS...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    } else {
                        val distanceText = currentDistance.toInt().toString()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isInsideGeofence) Icons.Default.CheckCircle else Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = if (isInsideGeofence) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (isInsideGeofence) "Target Tercapai" else "Jarak: $distanceText Meter",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isInsideGeofence) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (isInsideGeofence) "Posisi valid. Silakan ambil foto bukti penanganan/pemulihan lahan sekarang."
                            else "Mendekatlah ke area lingkaran pada peta (kurang dari 20 meter) untuk mengaktifkan kamera.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = Color.DarkGray
                        )

                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { navController.navigate("camera_update/$laporanId/$targetLat/$targetLon") },
                            enabled = isInsideGeofence,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = Color(0xFFE0E0E0),
                                disabledContentColor = Color(0xFF9E9E9E)
                            )
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Buka Kamera", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}