package amalia.skripsi.deteksipadi.ui.screens.general.home

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.HazardRepository
import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.local.AppDatabase
import amalia.skripsi.deteksipadi.data.supabase
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val db: AppDatabase,
    private val hazardRepo: HazardRepository
) : ViewModel() {

    val isGeofenceDanger = hazardRepo.isDanger.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val distanceToHama = hazardRepo.currentDistance.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 0.0
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val chartColors = listOf(
        Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00),
        Color(0xFFD32F2F), Color(0xFF7B1FA2), Color(0xFF0097A7)
    )

    fun refreshData(userRole: String) {
        if (userRole == "popt") loadPoptDashboard() else loadPetaniDashboard()
    }

    private fun loadPoptDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val profile = authRepo.getUserProfile() ?: return@launch
                val wkpp = profile.wkpp_kecamatan ?: emptyList()
                val allReports = supabase.from("reports").select().decodeList<HotspotDto>()

                // 1. Filter mutlak wilayah binaan POPT yang sedang login
                val areaReports = allReports.filter { r ->
                    wkpp.any { it.trim().equals(r.kecamatan.trim(), ignoreCase = true) }
                }

                val pending = areaReports.filter { it.status.lowercase() == "pending" }
                val verified = areaReports.filter { it.status.lowercase() != "pending" }

                // 2. Hitung Distribusi Hama untuk Diagram
                val totalInArea = areaReports.size
                val distribution = areaReports.groupBy { it.ai_label }.entries.mapIndexed { index, entry ->
                    PestStat(
                        label = entry.key,
                        total = entry.value.size,
                        pending = entry.value.count { it.status.lowercase() == "pending" },
                        verified = entry.value.count { it.status.lowercase() != "pending" },
                        percentage = if (totalInArea > 0) entry.value.size.toFloat() / totalInArea else 0f,
                        color = chartColors[index % chartColors.size]
                    )
                }.sortedByDescending { it.total }

                // 3. Notifikasi Laporan Masuk
                val mappedNotifs = pending.map { report ->
                    NotificationItem(
                        id = report.id.toString(),
                        title = "Laporan Baru Masuk",
                        message = "Terdeteksi ${report.ai_label} di ${report.kelurahan}",
                        time = report.created_at.take(10),
                        isRead = false,
                        route = "popt_reports",
                        reportData = report
                    )
                }

                _uiState.value = _uiState.value.copy(
                    userName = profile.full_name ?: "Petugas POPT",
                    pendingReports = pending.size,
                    finishedReports = verified.size,
                    totalReports = areaReports.size,
                    pestDistribution = distribution,
                    notifications = mappedNotifs,
                    unreadCount = mappedNotifs.size,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun loadPetaniDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val profile = authRepo.getUserProfile() ?: return@launch
                val localReports = db.pendingReportDao().getAllReports()
                val remoteReports = supabase.from("reports")
                    .select { filter { eq("user_id", profile.id) } }
                    .decodeList<HotspotDto>()

                _uiState.value = _uiState.value.copy(
                    userName = profile.full_name ?: "Petani",
                    pendingReports = localReports.size,
                    totalReports = remoteReports.size,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}