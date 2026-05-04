package amalia.skripsi.deteksipadi.ui.screens.general.peta

import amalia.skripsi.deteksipadi.data.HazardRepository
import amalia.skripsi.deteksipadi.data.LaporanDto
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.*
import javax.inject.Inject

@HiltViewModel
class PetaViewModel @Inject constructor(
    val hazardRepo: HazardRepository
) : ViewModel() {

    private var allHotspots = listOf<LaporanDto>()
    var filteredHotspots by mutableStateOf<List<LaporanDto>>(emptyList())

    val isDanger = hazardRepo.isDanger
    val currentDistance = hazardRepo.currentDistance

    var selectedTimeRange by mutableStateOf("Semua")
    var selectedHama by mutableStateOf("Semua Hama")
    var selectedKecamatan by mutableStateOf("")
    var startDateMillis by mutableStateOf<Long?>(null)
    var endDateMillis by mutableStateOf<Long?>(null)
    var selectedMonth by mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH))
    var selectedYear by mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR))

    fun setInitialData(data: List<LaporanDto>) {
        allHotspots = data
        applyFilter()
    }

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
        val today = Calendar.getInstance()

        filteredHotspots = allHotspots.filter { spot ->
            val matchHama = if (selectedHama == "Semua Hama") true else spot.label_ai == selectedHama
            val matchLoc = if (selectedKecamatan.isEmpty()) true
            else spot.alamat_lengkap?.contains(selectedKecamatan, ignoreCase = true) == true

            val spotTime = try {
                val parts = spot.created_at.split("T")[0].split("-")
                Calendar.getInstance().apply {
                    set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }.time
            } catch (e: Exception) { null }

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

    private fun isSameMonth(d: Date?, month: Int, year: Int): Boolean {
        if (d == null) return false
        val c = Calendar.getInstance().apply { time = d }
        return c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year
    }
}