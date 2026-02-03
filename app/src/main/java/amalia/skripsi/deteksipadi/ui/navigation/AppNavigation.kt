package amalia.skripsi.deteksipadi.ui.navigation

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.supabase // <--- Pastikan import variable client supabase Anda
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
import io.github.jan.supabase.auth.auth // Import ini
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository(context) }

    var startDestination by remember { mutableStateOf<String?>(null) }
    var userRole by remember { mutableStateOf<String?>(null) }

    // Cek Login & Ambil Role saat pertama kali dibuka
    LaunchedEffect(Unit) {

        val isSessionRestored = supabase.auth.loadFromStorage()

        if (isSessionRestored || authRepo.isUserLoggedIn()) {

            val profile = authRepo.getUserProfile()

            if (profile != null) {
                userRole = profile.role
                startDestination = "main"
            } else {
                authRepo.logout()
                startDestination = "login"
            }
        } else {
            // Tidak ada session tersimpan -> Ke Login Screen
            startDestination = "login"
        }
    }

    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        NavHost(navController = navController, startDestination = startDestination!!) {

            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        scope.launch {
                            // Fetch role saat login baru berhasil
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
                            authRepo.logout() // Ini akan menghapus session di Storage
                            userRole = null
                            navController.navigate("login") {
                                popUpTo(0) // Hapus history backstack
                            }
                        }
                    }
                )
            }
        }
    }
}