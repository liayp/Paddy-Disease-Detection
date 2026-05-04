package amalia.skripsi.deteksipadi.ui.screens.petani.history

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.local.AppDatabase
import amalia.skripsi.deteksipadi.data.local.PendingReport
import amalia.skripsi.deteksipadi.data.supabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val pendingList: List<PendingReport> = emptyList(),
    val processList: List<LaporanDto> = emptyList(),
    val finishedList: List<LaporanDto> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val db: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState = _uiState.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null

    init {
        loadHistory()
        setupRealtimeListener()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userId = supabase.auth.currentUserOrNull()?.id
            if (userId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            // Begitu Worker menghapus data, UI otomatis hilang
            launch {
                db.pendingReportDao().getAllReportsFlow().collectLatest { localData ->
                    _uiState.value = _uiState.value.copy(
                        pendingList = localData.filter { it.userId == userId }
                    )
                }
            }

            // Load remote data (Sudah dipantau oleh setupRealtimeListener di bawahnya)
            fetchRemoteData(userId)
        }
    }

    private fun setupRealtimeListener() {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

            realtimeChannel = supabase.realtime.channel("petani_history_realtime")

            val flow = realtimeChannel!!
                .postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "laporan"

                    // ✅ FIX FILTER (tidak error lagi)
                    filter("petani_id", FilterOperator.EQ, userId)
                }

            realtimeChannel!!.subscribe()

            flow.collectLatest {
                // 🔥 setiap ada perubahan → fetch ulang
                fetchRemoteData(userId)
            }
        }
    }

    private suspend fun fetchRemoteData(userId: String) {
        try {
            val remoteData = supabase.from("laporan")
                .select(
                    columns = Columns.raw(
                        "id, petani_id, foto_url, label_ai, confidence, status, prioritas, termasuk_cluster, alamat_lengkap, created_at, lat, lon"
                    )
                ) {
                    filter { eq("petani_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<LaporanDto>()

            _uiState.value = _uiState.value.copy(
                processList = remoteData.filter {
                    it.status == "menunggu" ||
                            it.status == "menunggu_verifikasi" ||
                            it.status == "perlu_kunjungan"
                },
                finishedList = remoteData.filter {
                    it.status == "terverifikasi" ||
                            it.status == "selesai" ||
                            it.status == "ditolak"
                },
                isLoading = false
            )

        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            // ✅ FIX: tidak pakai leave()
            realtimeChannel?.unsubscribe()
        }
    }
}