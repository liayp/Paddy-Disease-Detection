package amalia.skripsi.deteksipadi.ui.navigation

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.ui.screens.general.login.LoginScreen
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(splashScreen: SplashScreen) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository(context) }

    var startDestination by remember { mutableStateOf<String?>(null) }
    var userRole by remember { mutableStateOf<String?>(null) }

    // --- TAHAN SPLASH SCREEN ---
    // Splash screen (ikon app) akan terus tampil di layar selama startDestination masih null
    splashScreen.setKeepOnScreenCondition {
        startDestination == null
    }

    // --- CEK LOGIN (Hanya jalan sekali saat app dibuka) ---
    LaunchedEffect(Unit) {
        // Ini akan menunggu Supabase baca token dari storage HP
        val isLoggedIn = authRepo.isUserLoggedIn()

        if (isLoggedIn) {
            val profile = authRepo.getUserProfile()
            if (profile != null) {
                userRole = profile.role
                startDestination = "main" // Langsung masuk ke App
            } else {
                authRepo.logout()
                startDestination = "login" // Profil bermasalah, suruh login lagi
            }
        } else {
            startDestination = "login" // Belum login, ke halaman login
        }
    }

    // --- RENDER NAVIGASI ---
    // Karena kita pakai Splash Screen native, kita tidak perlu lagi Box Loading/CircularProgressIndicator
    if (startDestination != null) {
        NavHost(navController = navController, startDestination = startDestination!!) {

            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        scope.launch {
                            val profile = authRepo.getUserProfile()
                            userRole = profile?.role ?: "petani"
                            navController.navigate("main") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable("main") {
                MainScreen(
                    userRole = userRole ?: "petani",
                    onLogout = {
                        scope.launch {
                            authRepo.logout() // Hapus token dari HP
                            userRole = null
                            navController.navigate("login") {
                                popUpTo(0) // Bersihkan semua history agar tidak bisa back
                            }
                        }
                    }
                )
            }
        }
    }
}