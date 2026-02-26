package amalia.skripsi.deteksipadi.ui.screens.general.home

import amalia.skripsi.deteksipadi.data.HotspotDto

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val isRead: Boolean,
    val route: String,
    val reportData: HotspotDto? = null
)

data class DisplayReport(
    val label: String,
    val confidence: Float,
    val status: String,
    val time: String,
    val imageUrl: Any,
    val isFromLocal: Boolean
)

// Model data untuk statistik hama di diagram
data class PestStat(
    val label: String,
    val total: Int,
    val pending: Int,
    val verified: Int,
    val percentage: Float,
    val color: androidx.compose.ui.graphics.Color
)

data class HomeUiState(
    val userName: String = "User",
    val totalReports: Int = 0,
    val pendingReports: Int = 0,
    val finishedReports: Int = 0,
    val pestDistribution: List<PestStat> = emptyList(),
    val reportDisplay: DisplayReport? = null,
    val notifications: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false
)