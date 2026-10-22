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
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val pendingLocalList: List<PendingReport> = emptyList(),
    val waitingRemoteList: List<LaporanDto> = emptyList(), // Menunggu Verifikasi
    val processRemoteList: List<LaporanDto> = emptyList(), // Penanganan Mandiri & Perlu Kunjungan
    val finishedRemoteList: List<LaporanDto> = emptyList(),
    val isLoading: Boolean = true,
    val remoteUnavailable: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val db: AppDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val cachePrefs = context.getSharedPreferences("history_cache", Context.MODE_PRIVATE)
    private val cacheJson = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState = _uiState.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null
    private var localObserverStarted = false

    init {
        observeLocalReports()
        loadHistory()
        setupRealtimeListener()
    }

    private fun observeLocalReports() {
        if (localObserverStarted) return
        localObserverStarted = true
        viewModelScope.launch {
            db.pendingReportDao().getAllReportsFlow().collectLatest { localData ->
                val activeUserId = supabase.auth.currentUserOrNull()?.id
                _uiState.value = _uiState.value.copy(
                    pendingLocalList = if (activeUserId == null) emptyList() else localData.filter { it.userId == activeUserId }
                )
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userId = supabase.auth.currentUserOrNull()?.id
            if (userId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            val cached = readCached(userId)
            if (cached != null) applyRemoteData(cached, remoteUnavailable = true)
            fetchRemoteData(userId)
        }
    }

    private fun setupRealtimeListener() {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

            try {
                realtimeChannel = supabase.realtime.channel("petani_history_realtime")
                val channel = realtimeChannel ?: return@launch
                val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "laporan"
                    filter("petani_id", FilterOperator.EQ, userId)
                }
                channel.subscribe()
                flow.collectLatest { fetchRemoteData(userId) }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(remoteUnavailable = true)
            }
        }
    }

    private fun readCached(userId: String): List<LaporanDto>? = runCatching {
        val raw = cachePrefs.getString("reports_$userId", null) ?: throw IllegalStateException("Cache riwayat belum tersedia")
        cacheJson.decodeFromString(ListSerializer(LaporanDto.serializer()), raw)
    }.getOrNull()

    private fun applyRemoteData(remoteData: List<LaporanDto>, remoteUnavailable: Boolean) {
        _uiState.value = _uiState.value.copy(
            waitingRemoteList = remoteData.filter { it.status == "menunggu_verifikasi" },
            processRemoteList = remoteData.filter { it.status == "penanganan_mandiri" || it.status == "perlu_kunjungan" },
            finishedRemoteList = remoteData.filter { it.status == "selesai" || it.status == "ditolak" },
            isLoading = false,
            remoteUnavailable = remoteUnavailable
        )
    }

    private suspend fun fetchRemoteData(userId: String) {
        try {
            val remoteData = supabase.from("laporan")
                .select(columns = Columns.raw(LAPORAN_COLUMNS)) {
                    filter { eq("petani_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<LaporanDto>()

            runCatching {
                cachePrefs.edit().putString(
                    "reports_$userId",
                    cacheJson.encodeToString(ListSerializer(LaporanDto.serializer()), remoteData)
                ).apply()
            }
            applyRemoteData(remoteData, remoteUnavailable = false)
        } catch (e: Exception) {
            val cached = readCached(userId)
            if (cached != null) applyRemoteData(cached, remoteUnavailable = true)
            else _uiState.value = _uiState.value.copy(isLoading = false, remoteUnavailable = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { realtimeChannel?.unsubscribe() }
    }
}