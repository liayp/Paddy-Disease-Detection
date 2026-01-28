package amalia.skripsi.deteksipadi.ui.navigation

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.ui.screens.general.login.LoginScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

    // Inisialisasi Repository
    val authRepo = remember { AuthRepository(context) }

    // Cek Status Login
    val startDestination = if (authRepo.isUserLoggedIn()) "main" else "login"

    NavHost(navController = navController, startDestination = startDestination) {

        // --- RUTE 1: LOGIN ---
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    // Pindah ke Main dan Hapus Login dari Backstack
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // --- RUTE 2: MAIN APP (Dashboard) ---
        composable("main") {
            MainScreen(
                onLogout = {
                    scope.launch {
                        authRepo.logout()
                        // Kembali ke Login dan Hapus semua history
                        navController.navigate("login") {
                            popUpTo(0)
                        }
                    }
                }
            )
        }
    }
}