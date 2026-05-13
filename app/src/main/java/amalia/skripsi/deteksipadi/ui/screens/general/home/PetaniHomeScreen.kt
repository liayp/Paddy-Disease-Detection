package amalia.skripsi.deteksipadi.ui.screens.general.home

import amalia.skripsi.deteksipadi.ui.navigation.BottomNavItem
import amalia.skripsi.deteksipadi.ui.navigation.navigateSingleTopTo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Ringkasan Laporan Anda",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        // REVISI: Menggunakan Panel Terpadu yang jauh lebih hemat tempat dan elegan
        PetaniSummaryBoard(
            total = state.totalReports,
            diproses = state.pendingReports,
            selesai = state.finishedReports,
            ditolak = state.rejectedReports
        )

        Spacer(modifier = Modifier.height(28.dp))

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
            TextButton(onClick = { navController.navigateSingleTopTo(BottomNavItem.History.route) }) {
                Text("Lihat Semua", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val report = state.reportDisplay

        if (report != null) {
            LatestReportCard(
                report = report,
                onClick = { navController.navigateSingleTopTo(BottomNavItem.History.route) }
            )
        } else {
            EmptyStateCard(
                onClick = { navController.navigate("scanner") },
                isOffline = !isNetworkAvailable,
                customMessage = if (!isNetworkAvailable) "Koneksi terputus. Data tidak dapat dimuat."
                else "Belum ada pantauan. Ayo mulai deteksi area sawah Anda!"
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}