package amalia.skripsi.deteksipadi.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem(
        route = "home",
        title = "Beranda",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Peta : BottomNavItem(
        route = "peta",
        title = "Peta",
        selectedIcon = Icons.Filled.LocationOn,
        unselectedIcon = Icons.Outlined.LocationOn
    )

    data object Profile : BottomNavItem(
        route = "profile",
        title = "Akun",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )

    data object History : BottomNavItem(
        route = "history",
        title = "Riwayat",
        selectedIcon = Icons.Filled.DateRange,
        unselectedIcon = Icons.Outlined.DateRange
    )

    // Menu REPORTS (Khusus POPT)
    data object Reports : BottomNavItem(
        route = "popt_reports", // Route harus unik
        title = "Laporan",
        selectedIcon = Icons.AutoMirrored.Filled.Assignment,
        unselectedIcon = Icons.AutoMirrored.Outlined.Assignment
    )

    companion object {
        fun petaniRoutes(): List<BottomNavItem> {
            return listOf(Home, Peta, History, Profile)
        }

        fun poptRoutes(): List<BottomNavItem> {
            return listOf(Home, Reports, Peta, Profile)
        }

        fun allRoutes(): List<String> {
            return listOf(Home.route, History.route, Peta.route, Profile.route, Reports.route)
        }
    }
}