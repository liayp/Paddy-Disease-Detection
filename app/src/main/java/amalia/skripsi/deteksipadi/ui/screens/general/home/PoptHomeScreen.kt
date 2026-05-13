package amalia.skripsi.deteksipadi.ui.screens.general.home

import amalia.skripsi.deteksipadi.ui.navigation.BottomNavItem
import amalia.skripsi.deteksipadi.ui.navigation.navigateSingleTopTo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@Composable
fun PoptHomeScreen(
    navController: NavController,
    viewModel: HomeViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isNetworkAvailable by rememberHomeConnectivityState(context)

    LaunchedEffect(isNetworkAvailable) {
        viewModel.refreshData("popt")
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

        HeroStatusCard(
            isDanger = state.pendingReports > 0,
            distance = state.pendingReports.toDouble(),
            isOffline = !isNetworkAvailable
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Aksi Cepat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionIconCard(
                label = "Peta Sebaran",
                icon = Icons.Default.Map,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            ) { navController.navigateSingleTopTo(BottomNavItem.Peta.route) }

            ActionIconCard(
                label = "Validasi Laporan",
                icon = Icons.Default.Description,
                color = Color(0xFFF57C00),
                modifier = Modifier.weight(1f)
            ) { navController.navigateSingleTopTo(BottomNavItem.Reports.route) }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // STATISTIK KECAMATAN
        Text("Statistik Wilayah Binaan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))

        if (state.kecamatanDistribution.isEmpty()) {
            Text(
                "Tidak ada data per kecamatan di wilayah Anda.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(state.kecamatanDistribution) { kecStat ->
                    Card(
                        modifier = Modifier.width(280.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(kecStat.namaKecamatan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Total Laporan: ${kecStat.totalLaporan}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            if (kecStat.pestHama.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                kecStat.pestHama.forEach { hama ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("• ${hama.first}", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                        Text("${hama.second}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text("Distribusi Hama Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (state.pestDistribution.isEmpty()) {
                    Text("Belum ada laporan hama masuk.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = TextAlign.Center)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PestPieChart(state.pestDistribution, modifier = Modifier.size(120.dp))
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.pestDistribution.take(3).forEach { stat ->
                                LegendItem(stat)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Laporan Masuk Terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            TextButton(onClick = { navController.navigateSingleTopTo(BottomNavItem.Reports.route) }) {
                Text("Cek Validasi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val latestDisplay = state.reportDisplay

        if (!isNetworkAvailable && latestDisplay == null) {
            EmptyStateCard(onClick = {}, isOffline = true, customMessage = "Koneksi terputus. Tidak dapat memantau laporan baru.")
        } else if (latestDisplay == null) {
            EmptyStateCard(
                onClick = { navController.navigateSingleTopTo(BottomNavItem.Reports.route) },
                isOffline = false,
                customMessage = "Bagus! Saat ini tidak ada laporan baru yang menunggu verifikasi Anda."
            )
        } else {
            LatestReportCard(
                report = latestDisplay,
                onClick = { navController.navigateSingleTopTo(BottomNavItem.Reports.route) }
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}