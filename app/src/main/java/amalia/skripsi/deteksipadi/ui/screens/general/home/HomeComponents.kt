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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Calendar

// --- HELPER JARINGAN ---
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

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return isConnected
}

// --- KOMPONEN UI ---

@Composable
fun HomeHeader(userName: String, unreadCount: Int, onNotifClick: () -> Unit) {
    val greeting = remember {
        val calendar = Calendar.getInstance()
        when (calendar.get(Calendar.HOUR_OF_DAY)) {
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
            Text(text = userName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(
            onClick = onNotifClick,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
        ) {
            BadgedBox(badge = {
                if (unreadCount > 0) {
                    Badge { Text(unreadCount.toString()) }
                }
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

    // Teks diubah agar mendukung narasi Peringatan Dini Berbasis Geofence/Jarak
    val subtitle = when {
        isOffline -> "Pantauan geofencing realtime dijeda. Harap sambungkan ke internet."
        isDanger && distance < 10.0 -> "Peringatan! Hama terdeteksi dalam radius ${distance.toInt()} meter!" // Petani
        isDanger && distance >= 10.0 -> "Terdapat ${distance.toInt()} laporan cluster / masuk yang perlu verifikasi." // POPT
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
                    Text(if (isOffline) "Sistem offline" else "Early Warning System Aktif", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun StatCard(icon: ImageVector, count: String, label: String, iconBgColor: Color, iconColor: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
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
    val context = LocalContext.current
    val statusColor = when {
        report.isFromLocal -> Color(0xFFD32F2F)
        report.status == "ditolak" -> Color(0xFFD32F2F)
        report.status == "selesai" || report.status == "terverifikasi" -> Color(0xFF388E3C)
        report.status == "perlu_kunjungan" -> Color(0xFF7B1FA2)
        else -> Color(0xFFF57C00)
    }
    val displayStatus = if(report.isFromLocal) "MENUNGGU UPLOAD" else report.status.replace("_", " ").uppercase()
    val displayLabel = report.label ?: "Belum Teridentifikasi"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
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
                        Text(displayStatus, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Text(report.time, fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
                Text(displayLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(report.address!!, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, maxLines = 2)
            }
        }
    }
}

@Composable
fun EmptyStateCard(onClick: () -> Unit, isOffline: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(if (isOffline) Icons.Default.Warning else Icons.Default.CameraAlt, null, tint = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = if (isOffline) "Koneksi terputus. Riwayat terbaru tidak dapat ditampilkan." else "Belum ada pantauan. Ayo deteksi area Anda!", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}