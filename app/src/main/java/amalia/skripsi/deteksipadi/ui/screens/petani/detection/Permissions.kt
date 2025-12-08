package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.core.content.ContextCompat

@Composable
fun SetupCameraPermission(context: Context, hasCameraPermission: MutableState<Boolean>) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission.value = granted
    }
    LaunchedEffect(true) {
        hasCameraPermission.value = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission.value) launcher.launch(Manifest.permission.CAMERA)
    }
}
