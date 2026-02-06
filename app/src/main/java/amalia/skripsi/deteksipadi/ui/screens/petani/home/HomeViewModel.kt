package amalia.skripsi.deteksipadi.ui.screens.petani.home

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.HazardRepository // Import Repository Baru
import amalia.skripsi.deteksipadi.data.local.AppDatabase
import amalia.skripsi.deteksipadi.data.supabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

// Model UI Universal
data class DisplayReport(
    val label: String,
    val confidence: Float,
    val status: String,
    val time: String,
    val imageUrl: Any,
    val isFromLocal: Boolean
)

data class HomeUiState(
    val userName: String = "Petani",
    val isGeofenceDanger: Boolean = false,
    val distanceToHama: Double = 0.0,
    val totalReports: Int = 0,
    val pendingReports: Int = 0,
    val reportDisplay: DisplayReport? = null,
    val isLoading: Boolean = false
)

@Serializable
data class ReportHistoryDto(
    val ai_label: String,
    val confidence: Float,
    val status: String,
    val created_at: String,
    val image_url: String
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val db: AppDatabase,
    private val hazardRepo: HazardRepository // <--- Inject HazardRepository
) : ViewModel() {

    val isGeofenceDanger = hazardRepo.isDanger.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val distanceToHama = hazardRepo.currentDistance.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
        viewModelScope.launch {
            hazardRepo.isDanger.collect { isDangerStatus ->
                _uiState.value = _uiState.value.copy(isGeofenceDanger = isDangerStatus)
            }
        }
    }

    fun refreshData() {
        loadData()
    }

    // Fungsi Pantau Realtime dari Service
    private fun observeHazardStatus() {
        viewModelScope.launch {
            hazardRepo.isDanger.collect { isDanger ->
                _uiState.value = _uiState.value.copy(isGeofenceDanger = isDanger)
            }
        }
        viewModelScope.launch {
            hazardRepo.currentDistance.collect { distance ->
                _uiState.value = _uiState.value.copy(distanceToHama = distance)
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            // 1. Profil
            val profile = authRepo.getUserProfile()
            _uiState.value = _uiState.value.copy(userName = profile?.full_name ?: "Petani")

            // 2. Statistik Pending (Dari Room)
            val localReports = db.pendingReportDao().getAllReports()
            val pendingCount = localReports.size
            _uiState.value = _uiState.value.copy(pendingReports = pendingCount)

            // 3. Statistik Total (Supabase)
            try {
                val count = supabase.from("reports").select(columns = Columns.list("id")) {
                    count(Count.EXACT)
                    limit(0)
                }.countOrNull()

                if (count != null) {
                    _uiState.value = _uiState.value.copy(totalReports = count.toInt())
                }
            } catch (_: Exception) {
                // Offline
            }

            // 4. Logic Cascading Laporan Terakhir
            if (localReports.isNotEmpty()) {
                val latestLocal = localReports.last()
                val display = DisplayReport(
                    label = latestLocal.label,
                    confidence = latestLocal.confidence,
                    status = "Menunggu Sinyal",
                    time = "Baru saja",
                    imageUrl = java.io.File(latestLocal.imagePath),
                    isFromLocal = true
                )
                _uiState.value = _uiState.value.copy(reportDisplay = display)
            } else {
                try {
                    val result = supabase.from("reports")
                        .select(columns = Columns.list("ai_label, confidence, status, created_at, image_url")) {
                            order("created_at", Order.DESCENDING)
                            limit(1)
                        }.decodeList<ReportHistoryDto>()

                    if (result.isNotEmpty()) {
                        val remote = result[0]
                        val display = DisplayReport(
                            label = remote.ai_label,
                            confidence = remote.confidence,
                            status = remote.status,
                            time = formatDate(remote.created_at),
                            imageUrl = remote.image_url,
                            isFromLocal = false
                        )
                        _uiState.value = _uiState.value.copy(reportDisplay = display)
                    } else {
                        _uiState.value = _uiState.value.copy(reportDisplay = null)
                    }
                } catch (_: Exception) {
                    _uiState.value = _uiState.value.copy(reportDisplay = null)
                }
            }
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            dateString.take(10) + " " + dateString.substring(11, 16)
        } catch (_: Exception) { "Riwayat" }
    }
}