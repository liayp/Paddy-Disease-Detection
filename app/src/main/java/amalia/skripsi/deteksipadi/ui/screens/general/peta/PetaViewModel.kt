package amalia.skripsi.deteksipadi.ui.screens.general.peta

import amalia.skripsi.deteksipadi.data.HazardRepository
import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.KecamatanDto
import amalia.skripsi.deteksipadi.data.fetchSemuaKecamatan
import amalia.skripsi.deteksipadi.data.parseGeoJsonToLatLng
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class PetaViewModel @Inject constructor(
    val hazardRepo: HazardRepository
) : ViewModel() {

    private var allHotspots = listOf<LaporanDto>()
    var filteredHotspots by mutableStateOf<List<LaporanDto>>(emptyList())

    // State untuk Kecamatan & Poligon
    var semuaKecamatan by mutableStateOf<List<KecamatanDto>>(emptyList())
    var currentPolygons by mutableStateOf<List<List<LatLng>>>(emptyList())

    val isDanger = hazardRepo.isDanger
    val currentDistance = hazardRepo.currentDistance

    var selectedTimeRange by mutableStateOf("Semua")
    var selectedHama by mutableStateOf("Semua Hama")
    var selectedKecamatanList by mutableStateOf<List<String>>(emptyList())
    var startDateMillis by mutableStateOf<Long?>(null)
    var endDateMillis by mutableStateOf<Long?>(null)
    var selectedMonth by mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH))
    var selectedYear by mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR))

    init {
        // Tarik data master kecamatan di awal
        viewModelScope.launch {
            semuaKecamatan = fetchSemuaKecamatan()
        }
    }

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
        selectedKecamatanList = emptyList()
        startDateMillis = null
        endDateMillis = null
        selectedMonth = Calendar.getInstance().get(Calendar.MONTH)
        selectedYear = Calendar.getInstance().get(Calendar.YEAR)
        applyFilter()
    }

    fun applyFilter() {
        val today = Calendar.getInstance()

        // Kumpulkan semua poligon dari kecamatan yang dipilih
        val polygons = mutableListOf<List<LatLng>>()
        val targetKecamatan = if (selectedKecamatanList.isNotEmpty()) {
            semuaKecamatan.filter { selectedKecamatanList.contains(it.nama_kecamatan) }
        } else {
            semuaKecamatan
        }

        targetKecamatan.forEach { kec ->
            val parsed = parseGeoJsonToLatLng(kec.polygon_geojson)
            polygons.addAll(parsed)
        }
        currentPolygons = polygons

        // 2. Filter Hotspot
        filteredHotspots = allHotspots.filter { spot ->
            val matchHama = if (selectedHama == "Semua Hama") true else spot.label_ai == selectedHama

            // Cek apakah alamat laporan mengandung salah satu nama kecamatan yang dipilih
            val matchLoc = if (selectedKecamatanList.isEmpty()) true
            else selectedKecamatanList.any { kecName ->
                spot.alamat_lengkap?.contains(kecName, ignoreCase = true) == true
            }

            val spotTime = try {
                val parts = spot.created_at.split("T")[0].split("-")
                Calendar.getInstance().apply { set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt()) }.time
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