package amalia.skripsi.deteksipadi.ui.screens.general.home

import amalia.skripsi.deteksipadi.ui.navigation.BottomNavItem
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

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

        // HERO CARD (Status Wilayah Binaan)
        HeroStatusCard(
            isDanger = state.pendingReports > 0,
            distance = state.pendingReports.toDouble(),
            isOffline = !isNetworkAvailable
        )

        Spacer(modifier = Modifier.height(24.dp))

        // QUICK ACTIONS
        Text("Aksi Cepat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionIconCard(
                label = "Peta Sebaran",
                icon = Icons.Default.Map,
                color = Color(0xFF1976D2),
                modifier = Modifier.weight(1f)
            ) { navController.navigate(BottomNavItem.Peta.route) }

            ActionIconCard(
                label = "Buat Rekap",
                icon = Icons.Default.Description,
                color = Color(0xFF388E3C),
                modifier = Modifier.weight(1f)
            ) {
                // Arahkan ke Reports karena di sana ada fitur Download/Rekap
                navController.navigate(BottomNavItem.Reports.route)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // STATISTIK HAMA (Diagram Lingkaran)
        Text("Sebaran Hama & Penyakit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (state.pestDistribution.isEmpty()) {
                    Text("Belum ada data di wilayah Anda", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PestPieChart(state.pestDistribution, modifier = Modifier.size(130.dp))
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.pestDistribution.take(3).forEach { stat ->
                                LegendItem(stat)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SEKSI 4: LAPORAN MASUK TERBARU
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Laporan Masuk Terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                // FIX NAVIGASI POPT: Ke menu Laporan
                navController.navigate(BottomNavItem.Reports.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }) {
                Text("Lihat Semua")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.notifications.isEmpty()) {
            EmptyStateCard(onClick = {}, isOffline = !isNetworkAvailable)
        } else {
            val latest = state.notifications.first().reportData
            latest?.let {
                LatestReportCard(
                    report = DisplayReport(
                        label = it.ai_label,
                        confidence = it.confidence.toFloat(),
                        status = it.status,
                        time = it.created_at.take(10),
                        imageUrl = it.image_url,
                        isFromLocal = false
                    ),
                    onClick = {
                        navController.navigate(BottomNavItem.Reports.route)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

// Komponen Pendukung Diagram
@Composable
fun PestPieChart(data: List<PestStat>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        var startAngle = -90f
        data.forEach { stat ->
            val sweepAngle = stat.percentage * 360f
            drawArc(
                color = stat.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 30f, cap = StrokeCap.Round)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun LegendItem(stat: PestStat) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(stat.color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(stat.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "${stat.verified} Terverifikasi • ${stat.pending} Pending",
            fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun ActionIconCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}