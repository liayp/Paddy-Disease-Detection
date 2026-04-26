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
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
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

    private var realtimeChannel: RealtimeChannel? = null

    init {
        fetchNotifications()
        fetchGlobalHotspots()
        setupRealtimeListener()
    }

    private fun setupRealtimeListener() {
        viewModelScope.launch {
            realtimeChannel = supabase.realtime.channel("home_realtime")
            val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "laporan"
            }
            realtimeChannel!!.subscribe()
            changeFlow.collect {
                // Saat ada perubahan data laporan, refresh dashboard sesuai role terakhir
                val profile = authRepo.getUserProfile()
                profile?.let {
                    if (it.role == "popt") loadPoptDashboard() else loadPetaniDashboard()
                }
                fetchGlobalHotspots()
            }
        }
    }

    fun fetchNotifications() {
        viewModelScope.launch {
            val user = supabase.auth.currentUserOrNull() ?: return@launch
            try {
                val list = supabase.from("notifikasi")
                    .select(columns = Columns.raw("*, laporan:laporan_id(*)")) {
                        filter { eq("user_id", user.id) }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<NotificationItem>()

                _uiState.update { it.copy(
                    notifications = list,
                    unreadCount = list.count { n -> !n.sudah_dibaca }
                ) }
            } catch (e: Exception) {
                Log.e("NOTIF_ERROR", e.message.toString())
            }
        }
    }

    val isGeofenceDanger = hazardRepo.isDanger.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val distanceToHama = hazardRepo.currentDistance.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private var allHotspots = listOf<LaporanDto>()

    private val chartColors = listOf(
        Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFF57C00),
        Color(0xFFD32F2F), Color(0xFF7B1FA2), Color(0xFF0097A7)
    )

    private fun fetchGlobalHotspots() {
        viewModelScope.launch {
            try {
                allHotspots = supabase.from("laporan")
                    .select(columns = Columns.raw("id, lat, lon, label_ai, status, prioritas")) {
                        filter { neq("status", "ditolak") }
                    }.decodeList<LaporanDto>()
                startLocationTracking()
            } catch (e: Exception) {
                Log.e("HOME_INIT", "Gagal ambil hotspots: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
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
        fetchNotifications()
        fetchGlobalHotspots()
        if (userRole == "popt") loadPoptDashboard() else loadPetaniDashboard()
    }

    private fun loadPoptDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profile = authRepo.getUserProfile() ?: return@launch
                val areaReports = supabase.from("laporan").select().decodeList<LaporanDto>()

                val pending = areaReports.filter { it.status == "menunggu_verifikasi" || it.status == "perlu_kunjungan" }
                val verified = areaReports.filter { it.status != "menunggu_verifikasi" && it.status != "perlu_kunjungan" && it.status != "ditolak" }

                val distribution = areaReports.groupBy { it.label_ai }.entries.mapIndexed { index, entry ->
                    PestStat(
                        label = entry.key,
                        total = entry.value.size,
                        pending = entry.value.count { it.status == "menunggu_verifikasi" || it.status == "perlu_kunjungan" },
                        verified = entry.value.count { it.status == "terverifikasi" || it.status == "selesai" },
                        percentage = if (areaReports.isNotEmpty()) entry.value.size.toFloat() / areaReports.size else 0f,
                        color = chartColors[index % chartColors.size]
                    )
                }.sortedByDescending { it.total }

                _uiState.update { it.copy(
                    userName = profile.full_name ?: "Petugas POPT",
                    pendingReports = pending.size,
                    finishedReports = verified.size,
                    totalReports = areaReports.size,
                    pestDistribution = distribution,
                    isLoading = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadPetaniDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profile = authRepo.getUserProfile() ?: return@launch
                val remoteReports = supabase.from("laporan")
                    .select {
                        filter { eq("petani_id", profile.id) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<LaporanDto>()

                val latest = remoteReports.firstOrNull()?.let {
                    DisplayReport(
                        label = it.label_ai,
                        confidence = it.confidence,
                        status = it.status,
                        time = it.created_at.take(16).replace("T", " "),
                        imageUrl = it.foto_url,
                        address = it.alamat_lengkap,
                        isFromLocal = false
                    )
                }

                _uiState.update { it.copy(
                    userName = profile.full_name ?: "Mitra Petani",
                    totalReports = remoteReports.size,
                    pendingReports = remoteReports.count { r -> r.status == "menunggu" || r.status == "menunggu_verifikasi" },
                    reportDisplay = latest,
                    isLoading = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun markAsRead(notifId: String) {
        viewModelScope.launch {
            try {
                supabase.from("notifikasi").update(mapOf("sudah_dibaca" to true)) {
                    filter { eq("id", notifId) }
                }
                fetchNotifications()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { realtimeChannel?.unsubscribe() }
    }
}