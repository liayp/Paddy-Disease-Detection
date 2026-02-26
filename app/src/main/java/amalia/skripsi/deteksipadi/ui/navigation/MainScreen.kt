package amalia.skripsi.deteksipadi.ui.navigation

import amalia.skripsi.deteksipadi.data.HazardRepository
import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.fetchActiveHotspots
import amalia.skripsi.deteksipadi.ui.screens.general.peta.FilterPetaScreen
import amalia.skripsi.deteksipadi.ui.screens.general.peta.PetaScreen
import amalia.skripsi.deteksipadi.ui.screens.general.peta.PetaViewModel
import amalia.skripsi.deteksipadi.ui.screens.general.profile.ProfileScreen
import amalia.skripsi.deteksipadi.ui.screens.general.profile.ProfileViewModel
import amalia.skripsi.deteksipadi.ui.screens.petani.detection.DetectionScreen
import amalia.skripsi.deteksipadi.ui.screens.petani.history.HistoryScreen
import amalia.skripsi.deteksipadi.ui.screens.petani.history.HistoryViewModel
import amalia.skripsi.deteksipadi.ui.screens.general.home.HomeViewModel
import amalia.skripsi.deteksipadi.ui.screens.general.home.PetaniHomeScreen
import amalia.skripsi.deteksipadi.ui.screens.general.home.PoptHomeScreen
import amalia.skripsi.deteksipadi.ui.screens.general.notification.NotificationScreen
import amalia.skripsi.deteksipadi.ui.screens.petani.report.PetaniReportDetailScreen
import amalia.skripsi.deteksipadi.ui.screens.popt.reports.PoptReportsScreen
import amalia.skripsi.deteksipadi.ui.screens.popt.reports.PoptReportsViewModel
import amalia.skripsi.deteksipadi.ui.screens.popt.reports.ReportDetailScreen
import amalia.skripsi.deteksipadi.ui.screens.popt.reports.ReportPreviewScreen
import amalia.skripsi.deteksipadi.util.RequestPermissionsAndStartService
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

const val SCANNER_ROUTE = "scanner"

@Composable
fun MainScreen(
    userRole: String,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazardRepo: HazardRepository = hiltViewModel<PetaViewModel>().hazardRepo

    LaunchedEffect(Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val allHotspots = try { fetchActiveHotspots() } catch(e: Exception) { emptyList() }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // Cek tiap 5 detik
            .setMinUpdateDistanceMeters(10f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val lastLocation = result.lastLocation ?: return
                hazardRepo.updateLocation(
                    lastLocation.latitude,
                    lastLocation.longitude,
                    allHotspots
                )
            }
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                android.os.Looper.getMainLooper()
            )
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var selectedReport by remember { mutableStateOf<HotspotDto?>(null) }

    val homeViewModel: HomeViewModel = hiltViewModel()
    val petaViewModel: PetaViewModel = hiltViewModel()
    val poptReportsViewModel: PoptReportsViewModel = hiltViewModel()

    val bottomBarItems = remember(userRole) {
        if (userRole == "popt") BottomNavItem.poptRoutes()
        else BottomNavItem.petaniRoutes()
    }

    val isMainTab = currentRoute in BottomNavItem.allRoutes()

    RequestPermissionsAndStartService()

    Scaffold(
        bottomBar = {
            if (isMainTab) {
                if (userRole == "petani") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        BottomNavigationBar(
                            navController = navController,
                            items = bottomBarItems
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-28).dp)
                                .size(56.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .clickable {
                                    navController.navigate(SCANNER_ROUTE) {
                                        launchSingleTop = true
                                    }
                                },
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
                    BottomNavigationBar(
                        navController = navController,
                        items = bottomBarItems
                    )
                }
            }
        }
    ) { innerPadding ->

        val contentModifier =
            if (currentRoute == SCANNER_ROUTE || currentRoute == "filter_screen") {
                Modifier
            } else {
                Modifier.padding(innerPadding)
            }

        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = contentModifier
        ) {

            composable(BottomNavItem.Home.route) {
                if (userRole == "popt") {
                    PoptHomeScreen(navController = navController, viewModel = homeViewModel)
                } else {
                    PetaniHomeScreen(navController = navController, viewModel = homeViewModel)
                }
            }

            composable("notifications") {
                NotificationScreen(navController = navController, viewModel = homeViewModel)
            }

            composable(BottomNavItem.Profile.route) {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                ProfileScreen(
                    profileViewModel = profileViewModel,
                    navController = navController,
                    onLogout = onLogout
                )
            }

            composable(BottomNavItem.Peta.route) {
                PetaScreen(
                    navController = navController,
                    petaViewModel = petaViewModel,
                    userRole = userRole,
                    onReportClick = {
                        selectedReport = it
                        navController.navigate("report_detail")
                    }
                )
            }

            composable("filter_screen") {
                FilterPetaScreen(
                    navController = navController,
                    viewModel = petaViewModel
                )
            }

            composable("report_detail") {
                ReportDetailScreen(
                    navController = navController,
                    reportData = selectedReport
                )
            }

            // ================= PETANI =================
            if (userRole == "petani") {

                composable(SCANNER_ROUTE) {
                    val homeViewModel: HomeViewModel = hiltViewModel()
                    DetectionScreen(
                        navController = navController,
                        homeViewModel = homeViewModel
                    )
                }

                composable(BottomNavItem.History.route) {
                    val historyViewModel: HistoryViewModel = hiltViewModel()
                    HistoryScreen(
                        navController = navController,
                        historyViewModel = historyViewModel,
                        onNavigateToDetail = {
                            selectedReport = it
                            navController.navigate("petani_report_detail")
                        }
                    )
                }

                composable("petani_report_detail") {
                    PetaniReportDetailScreen(
                        navController = navController,
                        reportData = selectedReport
                    )
                }
            }

            // ================= POPT =================
            if (userRole == "popt") {

                composable(BottomNavItem.Reports.route) {
                    PoptReportsScreen(
                        navController = navController,
                        viewModel = poptReportsViewModel,
                        onReportClick = {
                            selectedReport = it
                            navController.navigate("report_detail")
                        }
                    )
                }

                composable("report_preview") {
                    ReportPreviewScreen(
                        navController = navController,
                        viewModel = poptReportsViewModel
                    )
                }
            }
        }
    }
}
