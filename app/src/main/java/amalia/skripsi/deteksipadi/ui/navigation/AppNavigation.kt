package amalia.skripsi.deteksipadi.ui.navigation

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.ui.screens.general.login.LoginScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository(context) }

    // State untuk menentukan start destination dan role
    var startDestination by remember { mutableStateOf<String?>(null) }
    var userRole by remember { mutableStateOf<String?>(null) } // 'petani' atau 'popt'

    // Cek Login & Ambil Role saat pertama kali dibuka
    LaunchedEffect(Unit) {
        if (authRepo.isUserLoggedIn()) {
            // Jika sudah login, ambil profil dulu dari DB
            val profile = authRepo.getUserProfile()
            if (profile != null) {
                userRole = profile.role
                startDestination = "main" // Lanjut ke MainScreen dengan role
            } else {
                // Fallback jika gagal ambil profil (misal koneksi putus), anggap belum login atau error
                // Opsi lain: tetap masuk sebagai 'petani' default, tapi lebih aman suruh login ulang
                authRepo.logout()
                startDestination = "login"
            }
        } else {
            startDestination = "login"
        }
    }

    // Tampilkan Loading selama startDestination belum ditentukan
    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        NavHost(navController = navController, startDestination = startDestination!!) {

            // --- RUTE 1: LOGIN ---
            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        // Saat sukses login baru, kita perlu fetch role lagi sebelum pindah
                        scope.launch {
                            val profile = authRepo.getUserProfile()
                            userRole = profile?.role ?: "petani" // Default jika gagal fetch
                            navController.navigate("main") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    }
                )
            }

            // --- RUTE 2: MAIN APP (Dashboard) ---
            composable("main") {
                // Oper userRole ke MainScreen
                MainScreen(
                    userRole = userRole ?: "petani", // Kirim role yang didapat
                    onLogout = {
                        scope.launch {
                            authRepo.logout()
                            userRole = null // Reset role
                            navController.navigate("login") {
                                popUpTo(0)
                            }
                        }
                    }
                )
            }
        }
    }
}