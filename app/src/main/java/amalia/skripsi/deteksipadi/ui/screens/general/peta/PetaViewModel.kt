package amalia.skripsi.deteksipadi.ui.screens.general.peta

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.HazardRepository
import androidx.compose.runtime.mutableIntStateOf
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class PetaViewModel @Inject constructor(
    private val hazardRepo: HazardRepository
) : ViewModel() {

    // Data asli dari server
    private var allHotspots = listOf<HotspotDto>()

    // Data hasil filter untuk Google Maps
    var filteredHotspots by mutableStateOf<List<HotspotDto>>(emptyList())

    // Observasi status dari Repository (untuk digunakan di PetaScreen)
    val isDanger = hazardRepo.isDanger
    val currentDistance = hazardRepo.currentDistance

    // State Filter
    var selectedTimeRange by mutableStateOf("Semua")
    var selectedHama by mutableStateOf("Semua Hama")
    var selectedKecamatan by mutableStateOf("")
    var startDateMillis by mutableStateOf<Long?>(null)
    var endDateMillis by mutableStateOf<Long?>(null)
    var selectedMonth by mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH))
    var selectedYear by mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR))

    fun setInitialData(list: List<HotspotDto>) {
        allHotspots = list
        applyFilter()
    }

    // Fungsi pusat untuk update lokasi bahaya ke Repository (Sinkron ke Home)
    fun updateHazardLocation(lat: Double, lon: Double) {
        hazardRepo.updateLocation(lat, lon, filteredHotspots)
    }

    fun resetFilter() {
        selectedTimeRange = "Semua"
        selectedHama = "Semua Hama"
        selectedKecamatan = ""
        startDateMillis = null
        endDateMillis = null
        selectedMonth = Calendar.getInstance().get(Calendar.MONTH)
        selectedYear = Calendar.getInstance().get(Calendar.YEAR)
        applyFilter()
    }

    fun applyFilter() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        filteredHotspots = allHotspots.filter { spot ->
            val matchHama = selectedHama == "Semua Hama" || spot.ai_label == selectedHama
            val matchLoc = selectedKecamatan.isEmpty() || spot.kecamatan.trim().contains(selectedKecamatan.trim(), ignoreCase = true)

            val spotTime = try { sdf.parse(spot.created_at.take(10)) } catch (_: Exception) { null }
            val matchTime = when (selectedTimeRange) {
                "Hari ini" -> isSameDay(spotTime, today.time)
                "7 Hari Terakhir" -> isWithinDays(spotTime, 7)
                "Pilih Bulan" -> isSameMonth(spotTime, selectedMonth, selectedYear)
                "Pilih Tanggal" -> {
                    if (startDateMillis != null && endDateMillis != null) {
                        val time = spotTime?.time ?: 0L
                        time in startDateMillis!!..endDateMillis!!
                    } else true
                }
                else -> true
            }
            matchHama && matchLoc && matchTime
        }
    }

    // Helper Functions
    private fun isSameDay(d1: Date?, d2: Date): Boolean {
        if (d1 == null) return false
        val c1 = Calendar.getInstance().apply { time = d1 }
        val c2 = Calendar.getInstance().apply { time = d2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
    private fun isWithinDays(d: Date?, days: Int): Boolean {
        if (d == null) return false
        val lim = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        return d.after(lim.time)
    }
    private fun isSameMonth(d: Date?, m: Int, y: Int): Boolean {
        if (d == null) return false
        val c = Calendar.getInstance().apply { time = d }
        return c.get(Calendar.MONTH) == m && c.get(Calendar.YEAR) == y
    }
}