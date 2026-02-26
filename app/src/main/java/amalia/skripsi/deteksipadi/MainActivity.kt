package amalia.skripsi.deteksipadi

import amalia.skripsi.deteksipadi.ui.navigation.AppNavigation
import amalia.skripsi.deteksipadi.ui.theme.DeteksiPadiTheme
import amalia.skripsi.deteksipadi.util.NetworkMonitor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // <-- IMPORT INI
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

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