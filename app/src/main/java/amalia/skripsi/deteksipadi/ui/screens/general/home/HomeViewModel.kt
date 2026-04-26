package amalia.skripsi.deteksipadi.ui.screens.general.home

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.HazardRepository
import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.NotificationItem
import amalia.skripsi.deteksipadi.data.local.AppDatabase
import amalia.skripsi.deteksipadi.data.supabase
import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val authRepo: AuthRepository,
    private val db: AppDatabase,
    private val hazardRepo: HazardRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchNotifications()
    }

    fun fetchNotifications() {
        viewModelScope.launch {
            val user = supabase.auth.currentUserOrNull() ?: return@launch
            try {
                val list = supabase.from("notifikasi")
                    .select() {
                        filter { eq("user_id", user.id) }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<NotificationItem>()

                _uiState.update { it.copy(notifications = list) }
            } catch (e: Exception) {
                Log.e("NOTIF_ERROR", e.message.toString())
            }
        }
    }

    // Observasi langsung dari Repository agar UI Home terupdate otomatis
    val isGeofenceDanger = hazardRepo.isDanger.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val distanceToHama = hazardRepo.currentDistance.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private var allHotspots = listOf<LaporanDto>()

    private val chartColors = listOf(
        Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00),
        Color(0xFFD32F2F), Color(0xFF7B1FA2), Color(0xFF0097A7)
    )

    init {
        // Saat Home pertama kali dibuat, langsung ambil data titik bahaya global
        fetchGlobalHotspots()
    }

    private fun fetchGlobalHotspots() {
        viewModelScope.launch {
            try {
                allHotspots = supabase.from("laporan")
                    .select(columns = Columns.raw("id, lat, lon, label_ai, status, prioritas")) {
                        filter { neq("status", "ditolak") }
                    }.decodeList<LaporanDto>()

                // Setelah data titik bahaya didapat, mulai pantau lokasi user
                startLocationTracking()
            } catch (e: Exception) {
                Log.e("HOME_INIT", "Gagal ambil hotspots: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        // Ambil lokasi terakhir atau pantau secara periodik untuk update Hero Card
        viewModelScope.launch {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        location?.let {
                            hazardRepo.updateLocation(it.latitude, it.longitude, allHotspots)
                        }
                    }
            } catch (e: Exception) {
                Log.e("HOME_LOCATION", "Gagal akses lokasi: ${e.message}")
            }
        }
    }

    fun refreshData(userRole: String) {
        loadNotifications()
        fetchGlobalHotspots() // Refresh juga data titik bahaya
        if (userRole == "popt") loadPoptDashboard() else loadPetaniDashboard()
    }

    private fun loadPoptDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val profile = authRepo.getUserProfile()
                if (profile == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, userName = "Sesi Tidak Valid")
                    return@launch
                }

                _uiState.value = _uiState.value.copy(userName = profile.full_name ?: "Petugas POPT")

                val areaReports = supabase.from("laporan").select().decodeList<LaporanDto>()

                val pending = areaReports.filter { it.status == "menunggu_verifikasi" }
                val verified = areaReports.filter { it.status != "menunggu_verifikasi" && it.status != "ditolak" }

                val totalInArea = areaReports.size
                val distribution = areaReports.groupBy { it.label_ai }.entries.mapIndexed { index, entry ->
                    PestStat(
                        label = entry.key,
                        total = entry.value.size,
                        pending = entry.value.count { it.status == "menunggu_verifikasi" },
                        verified = entry.value.count { it.status != "menunggu_verifikasi" && it.status != "ditolak" },
                        percentage = if (totalInArea > 0) entry.value.size.toFloat() / totalInArea else 0f,
                        color = chartColors[index % chartColors.size]
                    )
                }.sortedByDescending { it.total }

                _uiState.value = _uiState.value.copy(
                    pendingReports = pending.size,
                    finishedReports = verified.size,
                    totalReports = areaReports.size,
                    pestDistribution = distribution,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("POPT_DASHBOARD", "Error: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun loadPetaniDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val profile = authRepo.getUserProfile()
                if (profile == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, userName = "Sesi Tidak Valid")
                    return@launch
                }

                _uiState.value = _uiState.value.copy(userName = profile.full_name ?: "Mitra Petani")

                val remoteReports = supabase.from("laporan")
                    .select { filter { eq("petani_id", profile.id) } }
                    .decodeList<LaporanDto>()

                _uiState.value = _uiState.value.copy(
                    totalReports = remoteReports.size,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("PETANI_DASH", "Error: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            try {
                val user = supabase.auth.currentUserOrNull() ?: return@launch

                // Query dengan alias laporan:laporan_id(*)
                val response = supabase.from("notifikasi")
                    .select(columns = Columns.raw("*, laporan:laporan_id(*)")) {
                        filter { eq("user_id", user.id) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<NotificationItem>()

                _uiState.update { it.copy(
                    notifications = response,
                    unreadCount = response.count { n -> !n.sudah_dibaca },
                    isLoading = false
                )}
                Log.d("DEBUG_NOTIF", "Berhasil mengambil ${response.size} notifikasi")
            } catch (e: Exception) {
                Log.e("DEBUG_NOTIF", "Fetch Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun markAsRead(notifId: String) {
        viewModelScope.launch {
            try {
                supabase.from("notifikasi").update(mapOf("sudah_dibaca" to true)) {
                    filter { eq("id", notifId) }
                }
                loadNotifications()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun getReportById(reportId: String, onResult: (LaporanDto) -> Unit) {
        viewModelScope.launch {
            try {
                val report = supabase.from("laporan").select { filter { eq("id", reportId) } }.decodeSingle<LaporanDto>()
                onResult(report)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}