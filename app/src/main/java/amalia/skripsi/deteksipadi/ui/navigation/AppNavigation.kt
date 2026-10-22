package amalia.skripsi.deteksipadi.ui.navigation

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.ui.screens.general.login.LoginScreen
import amalia.skripsi.deteksipadi.ui.screens.general.profile.ChangePasswordScreen
import amalia.skripsi.deteksipadi.ui.screens.general.profile.EditProfileScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
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
    var showPoptPasswordReminder by remember { mutableStateOf(false) }

    splashScreen.setKeepOnScreenCondition { startDestination == null }

    suspend fun resolveAfterAuth() {
        val profile = authRepo.getUserProfile()
        if (profile == null) {
            startDestination = "login"
            userRole = null
            return
        }
        if (!profile.is_active) {
            authRepo.logout()
            startDestination = "login"
            userRole = null
            return
        }

        userRole = profile.role
        if (profile.role == "popt" && authRepo.shouldShowPoptPasswordReminder(profile.id)) {
            authRepo.markPoptPasswordReminderShown(profile.id)
            showPoptPasswordReminder = true
        }

        startDestination = if (authRepo.isProfileComplete(profile)) "main" else "profile_completion"
    }

    LaunchedEffect(Unit) {
        if (authRepo.isUserLoggedIn()) resolveAfterAuth() else startDestination = "login"
    }

    if (startDestination != null) {
        NavHost(navController = navController, startDestination = startDestination!!) {
            composable("login") {
                LoginScreen(onLoginSuccess = {
                    scope.launch {
                        startDestination = null
                        resolveAfterAuth()
                        navController.navigate(startDestination ?: "login") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                })
            }

            composable("profile_completion") {
                EditProfileScreen(
                    navController = navController,
                    profileViewModel = hiltViewModel(),
                    isProfileCompletion = true,
                    onProfileCompleted = {
                        navController.navigate("main") {
                            popUpTo("profile_completion") { inclusive = true }
                        }
                    }
                )
            }

            composable("change_password") {
                ChangePasswordScreen(
                    navController = navController,
                    profileViewModel = hiltViewModel()
                )
            }

            composable("main") {
                MainScreen(
                    userRole = userRole ?: "petani",
                    onLogout = {
                        scope.launch {
                            authRepo.logout()
                            userRole = null
                            startDestination = "login"
                            navController.navigate("login") {
                                popUpTo("main") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    }

    if (showPoptPasswordReminder) {
        AlertDialog(
            onDismissRequest = { showPoptPasswordReminder = false },
            title = { Text("Pengingat Keamanan Akun") },
            text = {
                Text("Anda login sebagai Petugas POPT. Password awal akun dibuat menggunakan NIP. Demi keamanan, Anda disarankan mengganti password tersebut.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPoptPasswordReminder = false
                    navController.navigate("change_password")
                }) { Text("Ganti Password") }
            },
            dismissButton = {
                TextButton(onClick = { showPoptPasswordReminder = false }) { Text("Nanti") }
            }
        )
    }
}