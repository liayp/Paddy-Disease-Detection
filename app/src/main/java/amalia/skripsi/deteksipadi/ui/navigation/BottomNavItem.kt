package amalia.skripsi.deteksipadi.ui.navigation

import androidx.compose.material.icons.Icons
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
    data object Home : BottomNavItem("home", Icons.Filled.Home, "Home")
    data object History : BottomNavItem("history", Icons.Filled.DateRange, "History")

    data object Peta : BottomNavItem("peta", Icons.Filled.LocationOn, "Peta")

    data object Profile : BottomNavItem("profile", Icons.Filled.Person, "Profile")

    companion object {
        val items = listOf(Home, Peta, History, Profile)
        fun routes() = items.map { it.route }
    }
}