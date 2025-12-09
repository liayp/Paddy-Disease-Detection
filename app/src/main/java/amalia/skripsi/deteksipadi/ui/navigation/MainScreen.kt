package amalia.skripsi.deteksipadi.ui.navigation

import amalia.skripsi.deteksipadi.ui.screens.petani.detection.DetectionScreen
import amalia.skripsi.deteksipadi.ui.screens.petani.history.HistoryScreen
import amalia.skripsi.deteksipadi.ui.screens.petani.history.HistoryViewModel
import amalia.skripsi.deteksipadi.ui.screens.petani.home.HomeScreen
import amalia.skripsi.deteksipadi.ui.screens.petani.home.HomeViewModel
import amalia.skripsi.deteksipadi.ui.screens.general.peta.PetaScreen
import amalia.skripsi.deteksipadi.ui.screens.general.peta.PetaViewModel
import amalia.skripsi.deteksipadi.ui.screens.general.profile.ProfileScreen
import amalia.skripsi.deteksipadi.ui.screens.general.profile.ProfileViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

const val SCANNER_ROUTE = "scanner"

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = remember { BottomNavItem.routes() }

    val isMainTab = currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = {
            if (isMainTab) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {

                    BottomNavigationBar(navController = navController)

                    val gradientBrush = Brush.linearGradient(
                        colors = listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-28).dp)
                            .size(56.dp)
                            .background(brush = gradientBrush, shape = CircleShape)
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
            }
        },

        floatingActionButton = {},
    ) { innerPadding ->

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
            composable(BottomNavItem.Home.route) {
                HomeScreen(navController = navController)
            }
            composable(SCANNER_ROUTE) {
                val homeViewModel: HomeViewModel = hiltViewModel()
                DetectionScreen(navController = navController, homeViewModel = homeViewModel)
            }
            composable(BottomNavItem.History.route) {
                val historyViewModel: HistoryViewModel = hiltViewModel()
                HistoryScreen(historyViewModel = historyViewModel, navController = navController)
            }
            composable(BottomNavItem.Peta.route) {
                val petaViewModel: PetaViewModel = hiltViewModel()
                PetaScreen(petaViewModel = petaViewModel, navController = navController)
            }
            composable(BottomNavItem.Profile.route) {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                ProfileScreen(profileViewModel = profileViewModel, navController = navController)
            }
        }
    }
}