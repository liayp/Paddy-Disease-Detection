package amalia.skripsi.deteksipadi

import amalia.skripsi.deteksipadi.ui.navigation.AppNavigation
import amalia.skripsi.deteksipadi.ui.navigation.MainScreen
import amalia.skripsi.deteksipadi.ui.theme.DeteksiPadiTheme
import amalia.skripsi.deteksipadi.util.NetworkMonitor
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        networkMonitor = NetworkMonitor(this)
        networkMonitor.startMonitoring()

        setContent {
            DeteksiPadiTheme {
                // --- LOGIKA IZIN NOTIFIKASI (START) ---
                val context = LocalContext.current
                var showExplanationDialog by remember { mutableStateOf(false) }

                // Launcher untuk meminta izin ke Sistem Android
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        // Opsional: Lakukan sesuatu jika user menolak/menerima
                        // Misalnya tracking analytics
                    }
                )

                // Cek Izin saat Aplikasi Dibuka (Hanya untuk Android 13+)
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val permissionStatus = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        )

                        // Jika belum diizinkan, tampilkan dialog penjelasan dulu
                        if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                            showExplanationDialog = true
                        }
                    }
                }

                // UI Dialog Penjelasan (Custom)
                if (showExplanationDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            // Jika user klik luar dialog, kita anggap dia mau tutup dialog tapi tetap trigger izin sistem (agresif dikit gapapa)
                            showExplanationDialog = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        title = {
                            Text(text = "Izinkan Notifikasi Aplikasi ini?")
                        },
                        text = {
                            Text("Aplikasi memerlukan izin ini untuk memberi peringatan dini serangan hama dan informasi laporan terbaru secara real-time.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExplanationDialog = false
                                    // Minta izin ke Sistem Android setelah user klik "Izinkan"
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            ) {
                                Text("Izinkan")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showExplanationDialog = false }
                            ) {
                                Text("Nanti Saja")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    )
                }
                // --- LOGIKA IZIN NOTIFIKASI (END) ---

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}