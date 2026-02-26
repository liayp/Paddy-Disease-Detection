package amalia.skripsi.deteksipadi.ui.screens.petani.home

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.HazardRepository
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

    init {
        loadData()
    }

    fun refreshData() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 1. Profil
            val profile = authRepo.getUserProfile()
            _uiState.value = _uiState.value.copy(userName = profile?.full_name ?: "Petani")

            // 2. Statistik Pending (Room)
            val localReports = db.pendingReportDao().getAllReports()
            _uiState.value = _uiState.value.copy(pendingReports = localReports.size)

            // 3. Statistik Total (Supabase)
            try {
                val count = supabase.from("reports").select(columns = Columns.list("id")) {
                    count(Count.EXACT)
                    limit(0)
                }.countOrNull()
                if (count != null) {
                    _uiState.value = _uiState.value.copy(totalReports = count.toInt())
                }
            } catch (_: Exception) {}

            // 4. Laporan Terakhir
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
                        _uiState.value = _uiState.value.copy(
                            reportDisplay = DisplayReport(
                                label = remote.ai_label,
                                confidence = remote.confidence,
                                status = remote.status,
                                time = formatDate(remote.created_at),
                                imageUrl = remote.image_url,
                                isFromLocal = false
                            )
                        )
                    }
                } catch (_: Exception) {
                    _uiState.value = _uiState.value.copy(reportDisplay = null)
                }
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            dateString.take(10) + " " + dateString.substring(11, 16)
        } catch (_: Exception) { "Riwayat" }
    }
}