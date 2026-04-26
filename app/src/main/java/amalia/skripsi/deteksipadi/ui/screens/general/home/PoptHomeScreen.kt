package amalia.skripsi.deteksipadi.ui.screens.general.home

import amalia.skripsi.deteksipadi.ui.navigation.BottomNavItem
import amalia.skripsi.deteksipadi.ui.navigation.navigateSingleTopTo
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage

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

        Text("Aksi Cepat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionIconCard(
                label = "Peta Sebaran",
                icon = Icons.Default.Map,
                color = Color(0xFF1976D2),
                modifier = Modifier.weight(1f)
            ) {
                // REVISI: Pindah tab Peta, bukan stack baru
                navController.navigateSingleTopTo(BottomNavItem.Peta.route)
            }

            ActionIconCard(
                label = "Validasi Laporan",
                icon = Icons.Default.Description,
                color = Color(0xFF388E3C),
                modifier = Modifier.weight(1f)
            ) {
                // REVISI: Pindah tab Reports
                navController.navigateSingleTopTo(BottomNavItem.Reports.route)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                    Text("Belum ada data laporan yang masuk di wilayah Anda.", color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall)
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Laporan Masuk Terbaru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                navController.navigateSingleTopTo(BottomNavItem.Reports.route)
            }) {
                Text("Cek Validasi")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val latestReportData = state.notifications.firstOrNull()?.laporan

        val latestDisplay = latestReportData?.let {
            DisplayReport(
                label = it.label_ai,
                confidence = it.confidence,
                status = it.status,
                time = it.created_at.take(16).replace("T", " "),
                imageUrl = it.foto_url,
                address = it.alamat_lengkap,
                isFromLocal = false
            )
        }

        if (!isNetworkAvailable && latestDisplay == null) {
            EmptyStateCard(
                onClick = {},
                isOffline = true,
                customMessage = "Koneksi terputus. Tidak dapat memantau laporan baru."
            )
        } else if (latestDisplay == null) {
            EmptyStateCard(
                onClick = {
                    navController.navigateSingleTopTo(BottomNavItem.Reports.route)
                },
                isOffline = false,
                customMessage = "Bagus! Saat ini tidak ada laporan baru yang menunggu verifikasi Anda."
            )
        } else {
            PoptStyleLatestReportCard(
                report = latestDisplay,
                onClick = {
                    navController.navigateSingleTopTo(BottomNavItem.Reports.route)
                }
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun PoptStyleLatestReportCard(
    report: DisplayReport,
    onClick: () -> Unit
) {
    val statusColor = when {
        report.status == "menunggu_verifikasi" -> Color(0xFFF57C00)
        report.status == "perlu_kunjungan" -> Color(0xFF7B1FA2)
        report.status == "ditolak" -> MaterialTheme.colorScheme.error
        else -> Color(0xFF388E3C)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {

            // IMAGE
            AsyncImage(
                model = report.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = report.status.replace("_", " ").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = report.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                Spacer(Modifier.height(6.dp))

                // LABEL AI
                Text(
                    text = report.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // ALAMAT
                Text(
                    text = report.address ?: "Lokasi tidak diketahui",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1
                )

                Spacer(Modifier.height(6.dp))

                // CONFIDENCE BAR
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = report.confidence,
                        modifier = Modifier
                            .width(90.dp)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "${(report.confidence * 100).toInt()}% Akurat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(onClick: () -> Unit, isOffline: Boolean = false, customMessage: String? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(
                imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.AssignmentLate,
                contentDescription = null,
                tint = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = customMessage ?: if (isOffline) "Koneksi terputus." else "Belum ada aktivitas.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
            text = "${stat.verified} Selesai • ${stat.pending} Menunggu",
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