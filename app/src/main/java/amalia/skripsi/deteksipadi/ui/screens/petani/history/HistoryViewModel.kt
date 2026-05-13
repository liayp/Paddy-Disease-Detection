package amalia.skripsi.deteksipadi.ui.screens.petani.history

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.LAPORAN_COLUMNS
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
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val pendingLocalList: List<PendingReport> = emptyList(),
    val waitingRemoteList: List<LaporanDto> = emptyList(), // Menunggu Verifikasi
    val processRemoteList: List<LaporanDto> = emptyList(), // Terverifikasi & Perlu Kunjungan
    val finishedRemoteList: List<LaporanDto> = emptyList(), // Selesai & Ditolak
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

            // Memantau data lokal (Offline)
            launch {
                db.pendingReportDao().getAllReportsFlow().collectLatest { localData ->
                    _uiState.value = _uiState.value.copy(
                        pendingLocalList = localData.filter { it.userId == userId }
                    )
                }
            }

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
                    filter("petani_id", FilterOperator.EQ, userId)
                }

            realtimeChannel!!.subscribe()

            flow.collectLatest {
                fetchRemoteData(userId)
            }
        }
    }

    private suspend fun fetchRemoteData(userId: String) {
        try {
            val remoteData = supabase.from("laporan")
                .select(columns = Columns.raw(LAPORAN_COLUMNS)) {
                    filter { eq("petani_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<LaporanDto>()

            _uiState.value = _uiState.value.copy(
                waitingRemoteList = remoteData.filter { it.status == "menunggu" || it.status == "menunggu_verifikasi" },
                processRemoteList = remoteData.filter { it.status == "terverifikasi" || it.status == "perlu_kunjungan" },
                finishedRemoteList = remoteData.filter { it.status == "selesai" || it.status == "ditolak" },
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { realtimeChannel?.unsubscribe() }
    }
}