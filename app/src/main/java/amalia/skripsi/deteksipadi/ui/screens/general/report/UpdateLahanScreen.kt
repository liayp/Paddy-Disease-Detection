package amalia.skripsi.deteksipadi.ui.screens.general.report

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.submitLaporanUpdate
import amalia.skripsi.deteksipadi.ml.YoloDetector
import amalia.skripsi.deteksipadi.ui.screens.general.peta.LocationUtils
import amalia.skripsi.deteksipadi.ui.screens.petani.detection.ImageUtils
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

@Composable
fun UpdateLahanScreen(
    navController: NavController,
    reportId: String,
    originalLat: Double,
    originalLon: Double
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }

    // Gunakan detector yang sudah ada
    val detector = remember { try { YoloDetector(context, "best.tflite") } catch (_: Exception) { null } }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // UI sederhana untuk mengambil foto (Anda bisa reuse ScannerFab yang Anda kirim)
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Update Kondisi Lahan", style = MaterialTheme.typography.headlineSmall)
        Text("Pastikan Anda berada di lokasi lahan yang sama", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            launch(Dispatchers.Main) {
                                Toast.makeText(context, "Izin lokasi belum diberikan", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        val location = fusedLocationClient.lastLocation.await()
                        if (location == null) {
                            launch(Dispatchers.Main) { Toast.makeText(context, "GPS tidak aktif", Toast.LENGTH_SHORT).show() }
                            return@launch
                        }

                        // VALIDASI GEOFENCING UPDATE (Max 25 meter)
                        val distance = LocationUtils.calculateDistance(location.latitude, location.longitude, originalLat, originalLon)
                        if (distance > 25.0) {
                            launch(Dispatchers.Main) {
                                Toast.makeText(context, "Gagal: Jarak terlalu jauh (${distance.toInt()}m). Anda harus berada di lokasi lahan.", Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        }

                        try {
                            val location = fusedLocationClient.lastLocation.await()
                        } catch (e: SecurityException) {
                            launch(Dispatchers.Main) {
                                Toast.makeText(context, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // Lanjutkan proses deteksi AI dan upload jika jarak valid
                        // (Gunakan logika upload yang sudah direvisi di SupabaseClient)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isUploading
        ) {
            if (isUploading) CircularProgressIndicator() else Text("Ambil & Kirim Foto Update")
        }
    }
}