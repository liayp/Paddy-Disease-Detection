package amalia.skripsi.deteksipadi.ui.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        BottomNavItem.items.forEach { item ->
            val isSelected = currentRoute == item.route

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                selected = isSelected,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigateSingleTopTo(item.route)
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,

                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,

                    unselectedIconColor = MaterialTheme.colorScheme.onSurface,

                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            )
        }
    }
}

fun NavController.navigateSingleTopTo(route: String) {
    if (route in BottomNavItem.routes()) {
        this.navigate(route) {
            popUpTo(this@navigateSingleTopTo.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        this.navigate(route)
    }
}
