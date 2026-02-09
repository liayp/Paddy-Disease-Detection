package amalia.skripsi.deteksipadi.ui.screens.petani.history

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.local.AppDatabase
import amalia.skripsi.deteksipadi.data.local.PendingReport
import amalia.skripsi.deteksipadi.data.supabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class RemoteReport(
    val id: String, // Diubah ke String karena UUID database
    val ai_label: String,
    val confidence: Float,
    val status: String,
    val created_at: String,
    val image_url: String,
    val user_id: String
)

data class HistoryUiState(
    val pendingList: List<PendingReport> = emptyList(),
    val processList: List<RemoteReport> = emptyList(),
    val finishedList: List<RemoteReport> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val db: AppDatabase,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAllHistory()
    }

    fun loadAllHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userId = supabase.auth.currentUserOrNull()?.id

            if (userId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            // 1. Ambil Data Lokal
            val allLocals = db.pendingReportDao().getAllReports()
            val myLocals = allLocals.filter { it.userId == userId }

            // 2. Ambil Data Server
            try {
                val remoteData = supabase.from("reports")
                    .select {
                        filter { eq("user_id", userId) }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<RemoteReport>()

                _uiState.value = _uiState.value.copy(
                    pendingList = myLocals,
                    // Di database kamu statusnya adalah 'pending'
                    processList = remoteData.filter { it.status.lowercase() == "pending" },
                    // Selain 'pending' (verified/rejected) masuk ke Selesai
                    finishedList = remoteData.filter { it.status.lowercase() != "pending" },
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    pendingList = myLocals,
                    isLoading = false
                )
            }
        }
    }
}