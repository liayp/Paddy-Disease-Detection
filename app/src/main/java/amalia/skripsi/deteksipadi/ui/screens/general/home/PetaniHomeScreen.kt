package amalia.skripsi.deteksipadi.ui.screens.general.home

import amalia.skripsi.deteksipadi.ui.navigation.BottomNavItem
import amalia.skripsi.deteksipadi.ui.navigation.navigateSingleTopTo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@Composable
fun PetaniHomeScreen(
    navController: NavController,
    viewModel: HomeViewModel
) {
    val isDanger by viewModel.isGeofenceDanger.collectAsStateWithLifecycle()
    val distance by viewModel.distanceToHama.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isNetworkAvailable by rememberHomeConnectivityState(context)

    LaunchedEffect(isNetworkAvailable) {
        viewModel.refreshData("petani")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        HomeHeader(state.userName, state.unreadCount) {
            navController.navigate("notifications")
        }

        Spacer(modifier = Modifier.height(24.dp))

        HeroStatusCard(isDanger = isDanger, distance = distance, isOffline = !isNetworkAvailable)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Ringkasan Aktivitas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                icon = Icons.Outlined.Assignment,
                count = state.totalReports.toString(),
                label = "Total Laporan",
                iconBgColor = MaterialTheme.colorScheme.primary,
                iconColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Outlined.History,
                count = state.pendingReports.toString(),
                label = "Menunggu",
                iconBgColor = Color.DarkGray,
                iconColor = Color.DarkGray,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pantauan Terakhir",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = {
                // REVISI: Pindah tab History dengan bersih
                navController.navigateSingleTopTo(BottomNavItem.History.route)
            }) {
                Text("Lihat Semua")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val report = state.reportDisplay

        if (report != null) {
            if (!isNetworkAvailable && !report.isFromLocal) {
                EmptyStateCard(
                    onClick = { navController.navigate("scanner") },
                    isOffline = true,
                    customMessage = "Koneksi terputus. Riwayat pantauan terbaru Anda tidak dapat dimuat."
                )
            } else {
                LatestReportCard(
                    report = report,
                    onClick = {
                        if (report.isFromLocal) {
                            android.widget.Toast.makeText(context, "Laporan ini tersimpan di memori dan menunggu sinyal internet untuk dikirim.", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            // REVISI: Klik card arahkan ke tab History
                            navController.navigateSingleTopTo(BottomNavItem.History.route)
                        }
                    }
                )
            }
        } else {
            EmptyStateCard(
                onClick = { navController.navigate("scanner") },
                isOffline = false,
                customMessage = "Belum ada pantauan. Ayo mulai deteksi area sawah Anda sekarang!"
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}