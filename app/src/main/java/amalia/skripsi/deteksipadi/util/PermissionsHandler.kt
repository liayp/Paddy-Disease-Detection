package amalia.skripsi.deteksipadi.util

import amalia.skripsi.deteksipadi.services.HazardDetectionService
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun RequestPermissionsAndStartService() {
    val context = LocalContext.current
    var showNotifDialog by remember { mutableStateOf(false) }

    fun startHazardService() {
        val intent = Intent(context, HazardDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            val locGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            // Jika lokasi diizinkan, nyalakan service
            if (locGranted) {
                startHazardService()
            }
        }
    )

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()

        // Cek Izin Lokasi (Wajib)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Cek Izin Notifikasi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                // Tampilkan dialog penjelasan untuk notifikasi
                showNotifDialog = true
            }
        }

        if (permissionsToRequest.isNotEmpty() && !showNotifDialog) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else if (permissionsToRequest.isEmpty()) {
            // Semua izin sudah ada, langsung jalankan service
            startHazardService()
        }
    }
}