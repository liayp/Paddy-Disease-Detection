package amalia.skripsi.deteksipadi.ui.screens.general.home

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.HazardRepository
import amalia.skripsi.deteksipadi.data.LAPORAN_COLUMNS
import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.NotificationItem
import amalia.skripsi.deteksipadi.data.PoptWilayahDto
import amalia.skripsi.deteksipadi.data.fetchLaporanUntukPOPT
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
    private var currentUserRole: String = "petani"

    init {
        viewModelScope.launch {
            val profile = authRepo.getUserProfile()
            if (profile != null) {
                currentUserRole = profile.role ?: "petani"
                _uiState.update { it.copy(userName = profile.full_name ?: "Pengguna") }
                refreshData(currentUserRole)
            }
        }
        setupRealtimeListener()
    }

    private fun setupRealtimeListener() {
        viewModelScope.launch {
            try {
                realtimeChannel = supabase.realtime.channel("home_realtime")
                val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "laporan"
                }
                realtimeChannel!!.subscribe()

                changeFlow.collect {
                    refreshData(currentUserRole)
                }
            } catch (e: Exception) {
                Log.e("REALTIME_ERROR", "Gagal inisialisasi: ${e.message}")
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
                    .select(columns = Columns.raw(LAPORAN_COLUMNS)) {
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
        currentUserRole = userRole
        fetchNotifications()
        fetchGlobalHotspots()
        if (userRole == "popt") loadPoptDashboard() else loadPetaniDashboard()
    }

    private fun loadPoptDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profile = authRepo.getUserProfile() ?: return@launch

                val wilayahResponse = supabase.from("popt_wilayah")
                    .select(columns = Columns.raw("kecamatan_id, kecamatan(nama_kecamatan)")) {
                        filter { eq("popt_id", profile.id) }
                    }.decodeList<PoptWilayahDto>()
                val kecMap = wilayahResponse.associate {
                    it.kecamatan_id to (it.kecamatan?.nama_kecamatan ?: "Kecamatan Tidak Diketahui")
                }

                val areaReports = fetchLaporanUntukPOPT(profile.id)

                val pending = areaReports.filter { it.status == "menunggu_verifikasi" || it.status == "perlu_kunjungan" }
                val verified = areaReports.filter { it.status == "terverifikasi" || it.status == "selesai" }

                val distribution = areaReports.groupBy { it.label_ai ?: "Belum Teridentifikasi" }.entries.mapIndexed { index, entry ->
                    PestStat(
                        label = entry.key,
                        total = entry.value.size,
                        pending = entry.value.count { it.status == "menunggu_verifikasi" || it.status == "perlu_kunjungan" },
                        verified = entry.value.count { it.status == "terverifikasi" || it.status == "selesai" },
                        percentage = if (areaReports.isNotEmpty()) entry.value.size.toFloat() / areaReports.size else 0f,
                        color = chartColors[index % chartColors.size]
                    )
                }.sortedByDescending { it.total }

                val kecDist = kecMap.map { (kecId, kecName) ->
                    val reportsInKec = areaReports.filter { it.kecamatan_id == kecId }
                    val pests = reportsInKec.groupBy { it.label_ai ?: "Belum Teridentifikasi" }
                        .map { it.key to it.value.size }
                        .sortedByDescending { it.second }
                        .take(3)

                    KecamatanStat(
                        namaKecamatan = kecName,
                        totalLaporan = reportsInKec.size,
                        pestHama = pests
                    )
                }.sortedByDescending { it.totalLaporan }

                val latest = areaReports.maxByOrNull { it.created_at }?.let {
                    DisplayReport(
                        id = it.id,
                        label = it.label_ai,
                        confidence = it.confidence,
                        status = it.status,
                        time = it.created_at.take(16).replace("T", " "),
                        imageUrl = it.foto_url,
                        address = it.alamat_lengkap,
                        lat = it.lat,
                        lon = it.lon,
                        isFromLocal = false
                    )
                }

                _uiState.update { it.copy(
                    userName = profile.full_name ?: "Petugas POPT",
                    pendingReports = pending.size,
                    finishedReports = verified.size,
                    totalReports = areaReports.size,
                    pestDistribution = distribution,
                    kecamatanDistribution = kecDist,
                    reportDisplay = latest,
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

                // REVISI FATAL: Gunakan LAPORAN_COLUMNS agar tidak terjadi Silent Crash karena kehilangan field wajib
                val remoteReports = supabase.from("laporan")
                    .select(columns = Columns.raw(LAPORAN_COLUMNS)) {
                        filter { eq("petani_id", profile.id) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<LaporanDto>()

                val latest = remoteReports.firstOrNull()?.let {
                    DisplayReport(
                        id = it.id,
                        label = it.label_ai,
                        confidence = it.confidence,
                        status = it.status,
                        time = it.created_at.take(16).replace("T", " "),
                        imageUrl = it.foto_url,
                        address = it.alamat_lengkap,
                        lat = it.lat,
                        lon = it.lon,
                        isFromLocal = false
                    )
                }

                _uiState.update { it.copy(
                    userName = profile.full_name ?: "Mitra Petani",
                    totalReports = remoteReports.size,
                    // Status valid: "menunggu", "menunggu_verifikasi", "perlu_kunjungan", "terverifikasi"
                    pendingReports = remoteReports.count { r -> r.status == "menunggu" || r.status == "menunggu_verifikasi" || r.status == "perlu_kunjungan" || r.status == "terverifikasi" },
                    finishedReports = remoteReports.count { r -> r.status == "selesai" },
                    rejectedReports = remoteReports.count { r -> r.status == "ditolak" },
                    reportDisplay = latest,
                    isLoading = false
                ) }
            } catch (e: Exception) {
                Log.e("PETANI_DASHBOARD", "Error: ${e.message}")
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