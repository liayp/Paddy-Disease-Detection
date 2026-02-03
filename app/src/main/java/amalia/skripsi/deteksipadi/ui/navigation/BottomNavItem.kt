package amalia.skripsi.deteksipadi.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    // Menu untuk semua Role
    data object Home : BottomNavItem("home", Icons.Filled.Home, "Home")
    data object Peta : BottomNavItem("peta", Icons.Filled.LocationOn, "Peta")
    data object Profile : BottomNavItem("profile", Icons.Filled.Person, "Profile")

    // Menu Khusus Petani
    data object History : BottomNavItem("history", Icons.Filled.DateRange, "History")

    // Menu Khusus POPT
    object Reports : BottomNavItem("Laporan Masuk", Icons.AutoMirrored.Filled.Assignment, "popt_reports")

    companion object {
        // Daftar Menu untuk PETANI
        fun petaniRoutes(): List<BottomNavItem> {
            return listOf(Home, Peta, History, Profile)
        }

        // Daftar Menu untuk POPT
        fun poptRoutes(): List<BottomNavItem> {
            return listOf(Home, Reports, Peta, Profile)
        }

        // Gabungan semua route untuk pengecekan di MainScreen
        fun allRoutes(): List<String> {
            return listOf(Home.route, History.route, Peta.route, Profile.route, Reports.route)
        }
    }
}