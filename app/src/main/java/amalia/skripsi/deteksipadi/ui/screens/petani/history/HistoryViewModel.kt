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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userId = supabase.auth.currentUserOrNull()?.id

            if (userId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            val allLocals = db.pendingReportDao().getAllReports()
            val myLocals = allLocals.filter { it.userId == userId }

            try {
                // PENTING: Gunakan Columns.raw agar tidak fetch kolom 'lokasi' yang berbentuk Hex
                val remoteData = supabase.from("laporan")
                    .select(columns = Columns.raw("id, petani_id, foto_url, label_ai, confidence, status, prioritas, termasuk_cluster, alamat_lengkap, created_at, lat, lon")) {
                        filter { eq("petani_id", userId) }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<LaporanDto>()

                _uiState.value = _uiState.value.copy(
                    pendingList = myLocals,
                    processList = remoteData.filter { it.status == "menunggu_verifikasi" || it.status == "perlu_kunjungan" },
                    finishedList = remoteData.filter { it.status == "terverifikasi" || it.status == "selesai" || it.status == "ditolak" },
                    isLoading = false
                )
            } catch (e: Exception) {
                android.util.Log.e("HISTORY_DEBUG", "CRASH SAAT FETCH HISTORY: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    pendingList = myLocals,
                    isLoading = false
                )
            }
        }
    }
}