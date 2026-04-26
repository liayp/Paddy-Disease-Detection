package amalia.skripsi.deteksipadi.ui.screens.general.notification

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import amalia.skripsi.deteksipadi.data.NotificationItem
import amalia.skripsi.deteksipadi.ui.screens.general.home.HomeViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController: NavController,
    viewModel: HomeViewModel,
    // REVISI: Cukup kirim ID, biarkan halaman detail yang ambil data terbaru
    onNavigateToDetail: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Aktivitas", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                // REVISI: Tambahkan aksi "Tandai semua dibaca" (Fitur standar aplikasi besar)
                actions = {
                    if (state.notifications.any { !it.sudah_dibaca }) {
                        TextButton(onClick = { /* Implementasi markAllAsRead di ViewModel */ }) {
                            Text("Baca Semua", fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (state.notifications.isEmpty()) {
            EmptyNotificationState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // REVISI: Urutkan berdasarkan yang terbaru
                items(state.notifications) { notif ->
                    NotificationRow(notif) {
                        viewModel.markAsRead(notif.id)
                        notif.laporan_id?.let { id ->
                            onNavigateToDetail(id)
                        }
                    }
                }
                item { Spacer(Modifier.height(50.dp)) }
            }
        }
    }
}

@Composable
fun NotificationRow(item: NotificationItem, onClick: () -> Unit) {
    val context = LocalContext.current

    // REVISI: Gunakan warna surface yang lebih kontras untuk membedakan status baca
    val backgroundColor = if (!item.sudah_dibaca)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    else
        MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top // Top alignment lebih rapi untuk pesan panjang
        ) {
            // ICON/AVATAR SECTION
            Box(modifier = Modifier.size(48.dp)) {
                val (icon, iconColor) = when (item.jenis) {
                    "geofence_alert" -> Icons.Default.Warning to Color(0xFFE53935)
                    "laporan_masuk" -> Icons.Default.AddLocationAlt to Color(0xFF2196F3)
                    "verifikasi_selesai" -> Icons.Default.FactCheck to Color(0xFF43A047)
                    "instruksi_baru" -> Icons.Default.PsychologyAlt to Color(0xFFFF9800)
                    "kunjungan_petugas" -> Icons.Default.DirectionsRun to Color(0xFF9C27B0)
                    "lahan_pulih" -> Icons.Default.CheckCircle to Color(0xFF2E7D32)
                    else -> Icons.Default.Notifications to Color.Gray
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = if (item.sudah_dibaca) Color(0xFFF5F5F5) else iconColor.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (item.sudah_dibaca) Color.Gray else iconColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (!item.sudah_dibaca) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // TEXT CONTENT
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.judul,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (!item.sudah_dibaca) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = item.pesan,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = if (item.sudah_dibaca) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = formatNotificationTime(item.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // THUMBNAIL SECTION (Mirip Instagram Feed Notif)
            val thumbnailUri = item.foto_url_hama ?: item.laporan?.foto_url
            if (thumbnailUri != null) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.foto_url_hama)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        // Divider tipis antar item
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = Color.LightGray.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun EmptyNotificationState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = Color(0xFFF9F9F9)
            ) {
                Icon(
                    Icons.Outlined.NotificationsNone,
                    null,
                    modifier = Modifier.padding(30.dp),
                    tint = Color.LightGray
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Belum ada kabar terbaru",
                color = Color.Gray,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Notifikasi terkait laporan Anda akan muncul di sini",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// REVISI: Fungsi waktu yang lebih cerdas (Today, Yesterday, etc)
fun formatNotificationTime(rawDate: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(rawDate) ?: return rawDate
        val now = Calendar.getInstance().time

        val diff = now.time - date.time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        when {
            seconds < 60 -> "Baru saja"
            minutes < 60 -> "$minutes menit lalu"
            hours < 24 -> "$hours jam lalu"
            days == 1L -> "Kemarin"
            days < 7 -> "$days hari lalu"
            else -> SimpleDateFormat("dd MMM", Locale("id", "ID")).format(date)
        }
    } catch (e: Exception) {
        rawDate.take(10)
    }
}