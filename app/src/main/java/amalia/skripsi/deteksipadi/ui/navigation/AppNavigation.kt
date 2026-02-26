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

    splashScreen.setKeepOnScreenCondition {
        startDestination == null
    }

    LaunchedEffect(Unit) {
        val isLoggedIn = authRepo.isUserLoggedIn()

        if (isLoggedIn) {
            userRole = authRepo.getSavedRole()
            startDestination = "main"
        } else {
            startDestination = "login"
        }
    }

    if (startDestination != null) {
        NavHost(navController = navController, startDestination = startDestination!!) {

            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        scope.launch {
                            val profile = authRepo.getUserProfile()
                            val role = profile?.role ?: "petani"

                            authRepo.saveUserRole(role) // SIMPAN KE LOKAL SAAT LOGIN
                            userRole = role

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
                            authRepo.logout()
                            userRole = null
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