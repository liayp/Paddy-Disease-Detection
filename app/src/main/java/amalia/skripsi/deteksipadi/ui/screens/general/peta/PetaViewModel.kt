package amalia.skripsi.deteksipadi.ui.screens.general.peta

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import amalia.skripsi.deteksipadi.data.HotspotDto
import androidx.compose.runtime.mutableIntStateOf
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class PetaViewModel @Inject constructor() : ViewModel() {
    // Sumber data asli dari server
    private var allHotspots = listOf<HotspotDto>()

    // Data yang akan dibaca oleh Google Maps (Hasil Filter)
    var filteredHotspots by mutableStateOf<List<HotspotDto>>(emptyList())

    // State Filter
    var selectedTimeRange by mutableStateOf("Semua")
    var selectedHama by mutableStateOf("Semua Hama")
    var selectedKecamatan by mutableStateOf("")

    // State Tanggal
    var startDateMillis by mutableStateOf<Long?>(null)
    var endDateMillis by mutableStateOf<Long?>(null)

    // State Bulan & Tahun (Untuk filter "Pilih Bulan")
    var selectedMonth by mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH))
    var selectedYear by mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR))

    fun setInitialData(list: List<HotspotDto>) {
        allHotspots = list
        applyFilter() // Langsung jalankan filter saat data masuk
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
            // 1. Filter Jenis Hama
            val matchHama = selectedHama == "Semua Hama" || spot.ai_label == selectedHama

            // 2. Filter Kecamatan
            val matchLoc = selectedKecamatan.isEmpty() ||
                    (spot.kecamatan?.trim()?.contains(selectedKecamatan.trim(), ignoreCase = true) == true)

            // 3. Filter Waktu
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
                else -> true // Opsi "Semua"
            }
            matchHama && matchLoc && matchTime
        }
    }

    private fun isSameDay(date1: Date?, date2: Date): Boolean {
        if (date1 == null) return false
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isWithinDays(date: Date?, days: Int): Boolean {
        if (date == null) return false
        val limit = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        return date.after(limit.time)
    }

    private fun isSameMonth(date: Date?, month: Int, year: Int): Boolean {
        if (date == null) return false
        val cal = Calendar.getInstance().apply { time = date }
        return cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
    }
}