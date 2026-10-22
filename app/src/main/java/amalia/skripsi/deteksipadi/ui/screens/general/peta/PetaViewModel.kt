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
    var polygonLoadError by mutableStateOf<String?>(null)

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
            try {
                semuaKecamatan = fetchSemuaKecamatan()
                polygonLoadError = if (semuaKecamatan.isEmpty()) {
                    "Data kecamatan kosong dari vw_kecamatan_geojson. Periksa view dan hak SELECT."
                } else null
                applyFilter()
            } catch (e: Exception) {
                polygonLoadError = "Gagal memuat polygon kecamatan: ${e.message ?: "kesalahan tidak diketahui"}"
                android.util.Log.e("PetaViewModel", "Gagal memuat vw_kecamatan_geojson", e)
            }
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

        // Pilih polygon berdasarkan nama yang dipilih pada UI; titik difilter memakai FK kecamatan_id.
        val targetKecamatan = if (selectedKecamatanList.isNotEmpty()) {
            semuaKecamatan.filter { it.nama_kecamatan in selectedKecamatanList }
        } else {
            semuaKecamatan
        }
        val polygonsByKecamatanId = targetKecamatan.associate { kec ->
            kec.id to parseGeoJsonToLatLng(kec.polygon_geojson)
        }
        currentPolygons = polygonsByKecamatanId.values.flatten()
        val selectedKecamatanIds = targetKecamatan.map { it.id }.toSet()

        filteredHotspots = allHotspots.filter { spot ->
            val matchHama = selectedHama == "Semua Hama" || spot.label_efektif == selectedHama
            val matchLoc = if (selectedKecamatanList.isEmpty()) {
                true
            } else if (!spot.kecamatan_id.isNullOrBlank()) {
                spot.kecamatan_id in selectedKecamatanIds
            } else {
                // Fallback untuk laporan lama tanpa FK kecamatan: uji koordinat terhadap polygon terpilih.
                val selectedPolygons = polygonsByKecamatanId.values.flatten()
                selectedPolygons.any { polygon -> pointInPolygon(spot.lat, spot.lon, polygon) }
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

    private fun pointInPolygon(lat: Double, lon: Double, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3 || !lat.isFinite() || !lon.isFinite()) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude
            val crosses = (yi > lat) != (yj > lat) &&
                    lon < (xj - xi) * (lat - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-12) + xi
            if (crosses) inside = !inside
            j = i
        }
        return inside
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