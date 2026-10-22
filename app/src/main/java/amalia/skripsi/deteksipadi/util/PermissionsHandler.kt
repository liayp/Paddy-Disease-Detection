package amalia.skripsi.deteksipadi.util

import amalia.skripsi.deteksipadi.services.HazardDetectionService
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit

private const val PREFS_NAME = "permission_prefs"
private const val KEY_NOTIFICATION_PERMISSION_PROMPTED =
    "notification_permission_prompted"

@Composable
fun RequestPermissionsAndStartService() {
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun startHazardService() {
        val intent = Intent(context, HazardDetectionService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun startServiceIfLocationAllowed() {
        if (LocationAccessPreference.canUseLocation(context)) startHazardService()
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {
            // Yang penting: prompt awal hanya dianggap sudah diproses
            // setelah sistem mengembalikan hasil permission.
            prefs.edit {
                putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, true)
            }

            // Izin lokasi diminta saat pengguna mengaktifkan atau memakai fitur lokasi.
            startServiceIfLocationAllowed()
        }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val notificationGranted =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

            val alreadyPrompted =
                prefs.getBoolean(
                    KEY_NOTIFICATION_PERMISSION_PROMPTED,
                    false
                )

            when {
                notificationGranted -> {
                    prefs.edit {
                        putBoolean(
                            KEY_NOTIFICATION_PERMISSION_PROMPTED,
                            true
                        )
                    }

                    startServiceIfLocationAllowed()
                }

                !alreadyPrompted -> {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }

                else -> {
                    // Sudah pernah ditanya sebelumnya.
                    // Jangan tampilkan dialog lagi setiap launch.
                    startServiceIfLocationAllowed()
                }
            }

        } else {
            // Android < 13 tidak memiliki runtime notification permission.
            startServiceIfLocationAllowed()
        }
    }
}