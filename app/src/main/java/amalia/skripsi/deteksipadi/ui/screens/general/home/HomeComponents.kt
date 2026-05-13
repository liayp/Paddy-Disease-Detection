package amalia.skripsi.deteksipadi.ui.screens.general.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Calendar

fun isOnlineHome(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val cap = cm.getNetworkCapabilities(cm.activeNetwork)
    return cap != null && (cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
}

@Composable
fun rememberHomeConnectivityState(context: Context): State<Boolean> {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isConnected = remember { mutableStateOf(isOnlineHome(context)) }

    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isConnected.value = true }
            override fun onLost(network: Network) { isConnected.value = false }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        cm.registerNetworkCallback(request, callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return isConnected
}

@Composable
fun HomeHeader(userName: String, unreadCount: Int, onNotifClick: () -> Unit) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..10 -> "Selamat pagi"
            in 11..14 -> "Selamat siang"
            in 15..18 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "$greeting, 👋", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = userName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(
            onClick = onNotifClick,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
        ) {
            BadgedBox(badge = {
                if (unreadCount > 0) Badge { Text(unreadCount.toString()) }
            }) {
                Icon(Icons.Outlined.Notifications, "Notifikasi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HeroStatusCard(isDanger: Boolean, distance: Double, isOffline: Boolean) {
    val gradientColors = when {
        isOffline -> listOf(Color(0xFF9E9E9E), Color(0xFF616161))
        isDanger -> listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
        else -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    }

    val icon = if (isOffline || isDanger) Icons.Default.Warning else Icons.Default.Security
    val title = when {
        isOffline -> "KONEKSI TERPUTUS"
        isDanger -> "PERINGATAN DINI: WASPADA"
        else -> "Wilayah Pantauan Aman"
    }

    val subtitle = when {
        isOffline -> "Pantauan geofencing realtime dijeda. Harap sambungkan ke internet."
        isDanger && distance < 10.0 -> "Peringatan! Hama terdeteksi dalam radius ${distance.toInt()} meter!"
        isDanger && distance >= 10.0 -> "Terdapat ${distance.toInt()} laporan masuk yang perlu divalidasi/dikunjungi."
        else -> "Tidak ada deteksi ancaman hama di sekitar area."
    }

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (isOffline) 1f else 1.4f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (isOffline) 0f else 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "alpha"
    )

    Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(gradientColors)).padding(24.dp)) {
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
                    Text(if (isOffline) "Sistem offline" else "Early Warning System Aktif", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// PANEL RINGKASAN PETANI (Baru, menggantikan 4 Card terpisah)
@Composable
fun PetaniSummaryBoard(total: Int, diproses: Int, selesai: Int, ditolak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SummaryItem(icon = Icons.Outlined.Assignment, count = total.toString(), label = "Total Laporan", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                VerticalDivider(modifier = Modifier.height(40.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SummaryItem(icon = Icons.Default.HourglassEmpty, count = diproses.toString(), label = "Diproses", color = Color(0xFFF57C00), modifier = Modifier.weight(1f))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SummaryItem(icon = Icons.Default.CheckCircle, count = selesai.toString(), label = "Selesai", color = Color(0xFF388E3C), modifier = Modifier.weight(1f))
                VerticalDivider(modifier = Modifier.height(40.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SummaryItem(icon = Icons.Default.Cancel, count = ditolak.toString(), label = "Ditolak", color = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SummaryItem(icon: ImageVector, count: String, label: String, color: Color, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.background(color.copy(alpha = 0.15f), CircleShape).padding(10.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LatestReportCard(report: DisplayReport, onClick: () -> Unit) {
    val context = LocalContext.current
    val statusColor = when {
        report.isFromLocal || report.status == "ditolak" -> Color(0xFFD32F2F)
        report.status == "selesai" || report.status == "terverifikasi" -> Color(0xFF388E3C)
        report.status == "perlu_kunjungan" -> Color(0xFF7B1FA2)
        else -> Color(0xFFF57C00)
    }

    val displayStatus = if(report.isFromLocal) "MENUNGGU UPLOAD" else report.status.replace("_", " ").uppercase()
    val displayLabel = report.label ?: "Belum Teridentifikasi"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(report.imageUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text(displayStatus, color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Text(report.time, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
                Text(displayLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(report.address ?: "Lokasi tidak diketahui", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun EmptyStateCard(onClick: () -> Unit, isOffline: Boolean = false, customMessage: String? = null) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.AssignmentLate, null, tint = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = customMessage ?: if (isOffline) "Koneksi terputus. Data tidak ditampilkan." else "Belum ada aktivitas laporan.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
        }
    }
}

@Composable
fun PestPieChart(data: List<PestStat>, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        var startAngle = -90f
        data.forEach { stat ->
            val sweepAngle = stat.percentage * 360f
            drawArc(color = stat.color, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 30f, cap = StrokeCap.Round))
            startAngle += sweepAngle
        }
    }
}

@Composable
fun LegendItem(stat: PestStat) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(stat.color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(stat.label ?: "Belum Teridentifikasi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = "${stat.verified} Selesai • ${stat.pending} Menunggu",
            style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(start = 18.dp)
        )
    }
}

@Composable
fun ActionIconCard(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)), border = BorderStroke(1.dp, color.copy(alpha = 0.2f))) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}