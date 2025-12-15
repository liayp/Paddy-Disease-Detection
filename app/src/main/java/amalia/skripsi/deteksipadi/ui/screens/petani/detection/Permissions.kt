package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

@Composable
fun EnsurePermissions(
    context: Context,
    onPermissionsGranted: (Boolean) -> Unit
) {
    // List izin dasar
    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    // FIX: Tambahkan izin Media Location khusus Android 10+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    // Tambahkan izin Read Storage (Tergantung versi Android)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        // Cek apakah SEMUA izin disetujui
        val allGranted = permissionsMap.values.all { it }
        onPermissionsGranted(allGranted)
    }

    LaunchedEffect(Unit) {
        val allAlreadyGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allAlreadyGranted) {
            launcher.launch(permissions.toTypedArray())
        } else {
            onPermissionsGranted(true)
        }
    }
}