package amalia.skripsi.deteksipadi.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Peta : BottomNavItem("peta", "Radar", Icons.Filled.Map, Icons.Outlined.Map)

    // RUTE BARU: Edukasi untuk Petani
    object Edukasi : BottomNavItem("edukasi", "Edukasi", Icons.Filled.MenuBook, Icons.Outlined.MenuBook)

    object Reports : BottomNavItem("popt_reports", "Validasi", Icons.Filled.Assignment, Icons.Outlined.Assignment)
    object History : BottomNavItem("history", "Riwayat", Icons.Filled.History, Icons.Outlined.History)
    object Profile : BottomNavItem("profile", "Profil", Icons.Filled.Person, Icons.Outlined.Person)

    companion object {
        // Petani mendapat akses Edukasi di tengah menu
        fun petaniRoutes() = listOf(Home, Peta, Edukasi, History, Profile)
        fun poptRoutes() = listOf(Home, Peta, Reports, Profile)
    }
}