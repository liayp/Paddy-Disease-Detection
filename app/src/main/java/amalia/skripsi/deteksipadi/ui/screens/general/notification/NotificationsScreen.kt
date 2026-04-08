package amalia.skripsi.deteksipadi.ui.screens.general.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.ui.screens.general.home.HomeViewModel
import amalia.skripsi.deteksipadi.ui.screens.general.home.NotificationItem
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController: NavController,
    viewModel: HomeViewModel,
    onNavigateToDetail: (LaporanDto) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notifikasi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
        if (state.notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Belum ada notifikasi", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.notifications) { notif ->
                    NotificationRow(notif) {
                        viewModel.markAsRead(notif.id)
                        notif.laporan_id?.let { id ->
                            viewModel.getReportById(id) { data ->
                                onNavigateToDetail(data)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationRow(item: NotificationItem, onClick: () -> Unit) {
    // Variabel logika memanggil is_read diganti dengan sudah_dibaca
    val backgroundColor = if (!item.sudah_dibaca) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(
                    if (!item.sudah_dibaca) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.4f)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (!item.sudah_dibaca) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = if (!item.sudah_dibaca) Color.White else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.judul, // title diganti judul
                    fontWeight = if (!item.sudah_dibaca) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (!item.sudah_dibaca) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(item.pesan, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) // message diganti pesan
                Text(item.created_at.take(16).replace("T", " "), fontSize = 10.sp, color = Color.Gray)
            }

            if (!item.sudah_dibaca) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(Icons.Default.FiberManualRecord, null, Modifier.size(12.dp), MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))
    }
}