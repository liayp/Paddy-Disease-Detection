package amalia.skripsi.deteksipadi.ui.screens.petani.home

import amalia.skripsi.deteksipadi.R
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val isDanger by viewModel.isGeofenceDanger.collectAsStateWithLifecycle()
    val distance by viewModel.distanceToHama.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        HomeHeader(state.userName) {
            Toast.makeText(context, "Fitur Notifikasi", Toast.LENGTH_SHORT).show()
        }

        Spacer(modifier = Modifier.height(24.dp))

        HeroStatusCard(isDanger = isDanger, distance = distance)

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
                navController.navigate("history") { launchSingleTop = true }
            }) {
                Text("Lihat Semua")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.reportDisplay != null) {
            LatestReportCard(
                report = state.reportDisplay!!,
                onClick = {
                    if (state.reportDisplay!!.isFromLocal) {
                        Toast.makeText(context, "Laporan menunggu sinyal...", Toast.LENGTH_SHORT).show()
                    } else {
                        navController.navigate("history")
                    }
                }
            )
        } else {
            EmptyStateCard(onClick = { navController.navigate("scan") })
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun HomeHeader(userName: String, onNotifClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Selamat pagi, 👋",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(
            onClick = onNotifClick,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Outlined.Notifications, "Notifikasi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HeroStatusCard(isDanger: Boolean, distance: Double) {
    val gradientColors = if (isDanger) {
        listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
    } else {
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    }

    val icon = if (isDanger) Icons.Default.Warning else Icons.Default.Security
    val title = if (isDanger) "WASPADA: ZONA HAMA" else "Sistem Aktif Memantau"
    val subtitle = if (isDanger) "Hama terdeteksi ${distance.toInt()} meter di dekat Anda!" else "Wilayah Anda terpantau aman."

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "alpha"
    )

    Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(24.dp)
        ) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                Box(modifier = Modifier.size(80.dp).scale(scale).border(2.dp, Color.White.copy(alpha = alpha), CircleShape))
                Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape).align(Alignment.Center))
            }

            Column {
                Box(modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape).padding(12.dp)) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pantauan lokasi realtime aktif", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun StatCard(icon: ImageVector, count: String, label: String, iconBgColor: Color, iconColor: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.background(iconBgColor.copy(alpha = 0.2f), CircleShape).padding(10.dp)) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(count, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LatestReportCard(report: DisplayReport, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.size(70.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(report.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val (statusColor, containerColor) = when {
                        report.isFromLocal -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
                        report.status.equals("active", true) -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
                        report.status.equals("verified", true) -> Color(0xFF10B981) to Color(0xFFD1FAE5)
                        else -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
                    }

                    Surface(color = containerColor, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = if(report.isFromLocal) "MENUNGGU UPLOAD" else report.status.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(report.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(report.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = report.confidence,
                        modifier = Modifier.width(80.dp).height(6.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${(report.confidence * 100).toInt()}% Akurat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Belum ada laporan. Ayo scan sekarang!",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}