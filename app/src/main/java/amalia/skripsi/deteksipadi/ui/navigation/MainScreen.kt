package amalia.skripsi.deteksipadi.ui.navigation

import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.ui.screens.general.peta.PetaScreen
import amalia.skripsi.deteksipadi.ui.screens.general.peta.PetaViewModel
import amalia.skripsi.deteksipadi.ui.screens.general.profile.ProfileScreen
import amalia.skripsi.deteksipadi.ui.screens.general.profile.ProfileViewModel
import amalia.skripsi.deteksipadi.ui.screens.petani.detection.DetectionScreen
import amalia.skripsi.deteksipadi.ui.screens.petani.history.HistoryScreen
import amalia.skripsi.deteksipadi.ui.screens.petani.history.HistoryViewModel
import amalia.skripsi.deteksipadi.ui.screens.petani.home.HomeScreen
import amalia.skripsi.deteksipadi.ui.screens.petani.home.HomeViewModel
import amalia.skripsi.deteksipadi.ui.screens.popt.reports.PoptReportsScreen
import amalia.skripsi.deteksipadi.ui.screens.popt.reports.ReportDetailScreen
// Import Screen POPT Baru (Nanti kita buat filenya di Tahap 2)
// import amalia.skripsi.deteksipadi.ui.screens.popt.reports.PoptReportsScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

const val SCANNER_ROUTE = "scanner"

@Composable
fun MainScreen(
    userRole: String, // Menerima Role
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var selectedReport by remember { mutableStateOf<HotspotDto?>(null) }

    // Tentukan menu berdasarkan Role
    val bottomBarItems = remember(userRole) {
        if (userRole == "popt") BottomNavItem.poptRoutes() else BottomNavItem.petaniRoutes()
    }

    // Cek apakah route sekarang termasuk di bottom bar (menggunakan allRoutes)
    val isMainTab = currentRoute in BottomNavItem.allRoutes()

    Scaffold(
        bottomBar = {
            // Tampilkan BottomBar hanya di tab utama
            if (isMainTab) {
                // Gunakan Box agar bisa menumpuk Scanner Button di tengah (Hanya untuk Petani)
                if (userRole == "petani") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Kirim items yang sudah difilter ke BottomNavigationBar
                        BottomNavigationBar(navController = navController, items = bottomBarItems)

                        // Tombol Scanner Tengah (Hanya Petani)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-28).dp)
                                .size(56.dp)
                                .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                                .clickable { navController.navigateSingleTopTo(SCANNER_ROUTE) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "scanner",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                } else {
                    // Untuk POPT, tampilkan BottomBar biasa tanpa tombol tengah
                    BottomNavigationBar(navController = navController, items = bottomBarItems)
                }
            }
        },
    ) { innerPadding ->
        // ... (contentModifier logic sama) ...
        val contentModifier = if (currentRoute == SCANNER_ROUTE) {
            Modifier
        } else {
            Modifier.padding(innerPadding)
        }

        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = contentModifier
        ) {
            // --- ROUTE UMUM ---
            composable(BottomNavItem.Home.route) {
                // Bisa dibuat HomeScreen berbeda untuk POPT jika mau dashboard beda
                HomeScreen(navController = navController)
            }

            composable(BottomNavItem.Profile.route) {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                ProfileScreen(profileViewModel = profileViewModel, navController = navController, onLogout = onLogout)
            }

            composable(BottomNavItem.Peta.route) {
                val petaViewModel: PetaViewModel = hiltViewModel()
                PetaScreen(
                    navController = navController,
                    petaViewModel = petaViewModel,
                    userRole = userRole,
                    onReportClick = { report ->
                        selectedReport = report
                        navController.navigate("report_detail")
                    }
                )
            }

            // --- ROUTE KHUSUS PETANI ---
            if (userRole == "petani") {
                composable(SCANNER_ROUTE) {
                    val homeViewModel: HomeViewModel = hiltViewModel()
                    DetectionScreen(navController = navController, homeViewModel = homeViewModel)
                }
                composable(BottomNavItem.History.route) {
                    val historyViewModel: HistoryViewModel = hiltViewModel()
                    HistoryScreen(historyViewModel = historyViewModel, navController = navController)
                }
            }

            // --- ROUTE KHUSUS POPT ---
            if (userRole == "popt") {
                composable(BottomNavItem.Reports.route) {
                    PoptReportsScreen(
                        navController = navController,
                        onReportClick = { report ->
                            selectedReport = report
                            navController.navigate("report_detail")
                        }
                    )
                }

                composable("report_detail") {
                    ReportDetailScreen(
                        navController = navController,
                        reportData = selectedReport
                    )
                }
            }
        }
    }
}