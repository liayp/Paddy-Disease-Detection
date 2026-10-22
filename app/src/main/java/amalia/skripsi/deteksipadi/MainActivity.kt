package amalia.skripsi.deteksipadi

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.supabase
import amalia.skripsi.deteksipadi.ui.navigation.AppNavigation
import amalia.skripsi.deteksipadi.ui.theme.DeteksiPadiTheme
import amalia.skripsi.deteksipadi.util.NetworkMonitor
import amalia.skripsi.deteksipadi.util.NotificationHelper
import amalia.skripsi.deteksipadi.util.VisitReminderScheduler
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authRepo: AuthRepository
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        NotificationHelper.createNotificationChannels(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            runCatching { VisitReminderScheduler.rescheduleStored(applicationContext) }
                .onFailure { Log.e("VISIT_REMINDER", "Gagal memulihkan pengingat tersimpan", it) }
        }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    supabase.auth.awaitInitialization()

                    if (authRepo.isUserLoggedIn()) {
                        // Beri jeda 2 detik untuk memastikan Google Play Services stabil
                        delay(2000)
                        authRepo.syncFcmToken()
                    }
                } catch (e: Exception) {
                    Log.e("MAIN_FCM", "Auto sync failed: ${e.message}")
                }
            }
        }

        networkMonitor = NetworkMonitor(this)
        networkMonitor.startMonitoring()

        setContent {
            DeteksiPadiTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(splashScreen = splashScreen)
                }
            }
        }
    }
}