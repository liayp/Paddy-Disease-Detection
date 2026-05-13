package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.PoptProfile
import amalia.skripsi.deteksipadi.data.PoptWilayahDto
import amalia.skripsi.deteksipadi.data.ProfileDto
import amalia.skripsi.deteksipadi.data.fetchLaporanUntukPOPT
import amalia.skripsi.deteksipadi.data.supabase
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class PoptReportsUiState(
    val menungguList: List<LaporanDto> = emptyList(),
    val kunjunganList: List<LaporanDto> = emptyList(),
    val terverifikasiList: List<LaporanDto> = emptyList(),
    val selesaiList: List<LaporanDto> = emptyList(),
    val ditolakList: List<LaporanDto> = emptyList(),
    val exportPreviewList: List<LaporanDto> = emptyList(),
    val poptProfile: PoptProfile? = null,
    val isLoading: Boolean = false,

    // REVISI: State untuk filter kecamatan
    val selectedFilterKecamatan: String = "Semua Wilayah",
    val availableKecamatanList: List<String> = emptyList(),

    // Untuk Export PDF
    val selectedMonthLabel: String = ""
)

@Suppress("DEPRECATION")
class PoptReportsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PoptReportsUiState())
    val uiState = _uiState.asStateFlow()

    // MENYIMPAN DATA ASLI TANPA FILTER
    private var allHotspotsForPOPT = mutableListOf<LaporanDto>()
    // MENYIMPAN MAPPING ID KECAMATAN -> NAMA KECAMATAN UNTUK FILTER
    private var kecIdToNameMap = mapOf<String, String>()

    private var realtimeChannel: RealtimeChannel? = null

    init {
        loadPoptInitialData()
        setupRealtimeListener()
    }

    fun loadPoptInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

            try {
                val profileDto = supabase.from("profiles")
                    .select { filter { eq("id", userId) } }
                    .decodeSingle<ProfileDto>()

                val wilayahResponse = supabase.from("popt_wilayah")
                    .select(columns = Columns.raw("kecamatan_id, kecamatan(nama_kecamatan)")) {
                        filter { eq("popt_id", userId) }
                    }
                    .decodeList<PoptWilayahDto>()

                // Buat Map ID -> Nama untuk logika Filter
                kecIdToNameMap = wilayahResponse.associate {
                    it.kecamatan_id to (it.kecamatan?.nama_kecamatan ?: "Tidak Diketahui")
                }
                val wkppList = kecIdToNameMap.values.toList().sorted()

                val profile = PoptProfile(
                    full_name = profileDto.full_name,
                    wkpp_kecamatan = wkppList
                )

                val remoteData = fetchLaporanUntukPOPT(userId)

                allHotspotsForPOPT = remoteData.sortedByDescending { it.created_at }.toMutableList()

                _uiState.value = _uiState.value.copy(
                    poptProfile = profile,
                    availableKecamatanList = listOf("Semua Wilayah") + wkppList,
                    isLoading = false
                )

                updateUiState() // Apply filter awal (Semua Wilayah)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // FUNGSI BARU: Update Filter Kecamatan
    fun updateKecamatanFilter(kecamatanName: String) {
        _uiState.value = _uiState.value.copy(selectedFilterKecamatan = kecamatanName)
        updateUiState()
    }

    private fun setupRealtimeListener() {
        viewModelScope.launch {
            realtimeChannel = supabase.realtime.channel("popt_reports_realtime")

            val changeFlow = realtimeChannel!!
                .postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "laporan"
                }

            realtimeChannel!!.subscribe()

            changeFlow.collect { action ->
                val userId = supabase.auth.currentUserOrNull()?.id ?: return@collect
                val kecIds = kecIdToNameMap.keys

                when (action) {
                    is PostgresAction.Insert -> {
                        val newData = action.decodeRecord<LaporanDto>()
                        if (newData.kecamatan_id in kecIds) {
                            allHotspotsForPOPT.add(0, newData)
                        }
                    }
                    is PostgresAction.Update -> {
                        val updated = action.decodeRecord<LaporanDto>()
                        val index = allHotspotsForPOPT.indexOfFirst { it.id == updated.id }
                        if (index != -1) {
                            allHotspotsForPOPT[index] = updated
                        } else if (updated.kecamatan_id in kecIds) {
                            allHotspotsForPOPT.add(0, updated)
                        }
                    }
                    is PostgresAction.Delete -> {
                        val id = action.oldRecord["id"].toString().replace("\"", "")
                        allHotspotsForPOPT.removeAll { it.id == id }
                    }
                    else -> {}
                }

                allHotspotsForPOPT.sortByDescending { it.created_at }
                updateUiState()
            }
        }
    }

    private fun updateUiState() {
        // Ambil filter yang sedang aktif
        val currentFilterName = _uiState.value.selectedFilterKecamatan

        // Cari ID kecamatan dari nama yang dipilih
        val targetKecId = if (currentFilterName == "Semua Wilayah") null
        else kecIdToNameMap.entries.find { it.value == currentFilterName }?.key

        // Filter list master berdasarkan ID Kecamatan (jika ada filter aktif)
        val filteredList = if (targetKecId == null) {
            allHotspotsForPOPT
        } else {
            allHotspotsForPOPT.filter { it.kecamatan_id == targetKecId }
        }

        _uiState.value = _uiState.value.copy(
            menungguList = filteredList.filter { it.status == "menunggu_verifikasi" },
            kunjunganList = filteredList.filter { it.status == "perlu_kunjungan" },
            terverifikasiList = filteredList.filter { it.status == "terverifikasi" },
            selesaiList = filteredList.filter { it.status == "selesai" },
            ditolakList = filteredList.filter { it.status == "ditolak" }
        )
    }

    fun getLastThreeMonths(): List<Pair<String, Pair<Int, Int>>> {
        val months = mutableListOf<Pair<String, Pair<Int, Int>>>()
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

        repeat(3) {
            months.add(
                sdf.format(cal.time) to (cal.get(Calendar.MONTH) to cal.get(Calendar.YEAR))
            )
            cal.add(Calendar.MONTH, -1)
        }
        return months
    }

    @SuppressLint("DefaultLocale")
    fun prepareExportPreview(month: Int, year: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
        }

        val monthName = SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(cal.time)
        val dbFilterPrefix = String.format("%04d-%02d", year, month + 1)

        // Label disesuaikan dengan filter kecamatan saat ini
        val currentFilter = _uiState.value.selectedFilterKecamatan
        val label = if (currentFilter == "Semua Wilayah") monthName else "$monthName ($currentFilter)"

        // Filter bulan DAN filter kecamatan
        val targetKecId = if (currentFilter == "Semua Wilayah") null
        else kecIdToNameMap.entries.find { it.value == currentFilter }?.key

        val filtered = allHotspotsForPOPT.filter { report ->
            report.created_at.startsWith(dbFilterPrefix) &&
                    (targetKecId == null || report.kecamatan_id == targetKecId)
        }

        _uiState.value = _uiState.value.copy(
            exportPreviewList = filtered,
            selectedMonthLabel = label
        )
    }

    fun downloadPDF(context: Context) {
        val data = _uiState.value.exportPreviewList
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        val label = _uiState.value.selectedMonthLabel

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = 12f
        canvas.drawText("PEMERINTAH PROVINSI GORONTALO", 297f, 40f, paint)
        canvas.drawText("DINAS PERTANIAN", 297f, 55f, paint)
        paint.textSize = 10f
        canvas.drawText("BALAI PERLINDUNGAN TANAMAN PANGAN DAN HORTIKULTURA", 297f, 70f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawLine(50f, 80f, 545f, 80f, paint)
        canvas.drawLine(50f, 82f, 545f, 82f, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 14f
        canvas.drawText("LAPORAN REKAPITULASI DETEKSI HAMA PADI", 297f, 110f, paint)
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Periode: $label", 297f, 125f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f
        canvas.drawText("Nama Petugas : ${_uiState.value.poptProfile?.full_name ?: "-"}", 50f, 160f, paint)

        // REVISI PDF: Tampilkan filter kecamatan di profil dokumen
        val currentFilter = _uiState.value.selectedFilterKecamatan
        val printWilayah = if (currentFilter == "Semua Wilayah") {
            _uiState.value.poptProfile?.wkpp_kecamatan?.joinToString(", ") ?: "-"
        } else {
            currentFilter
        }
        canvas.drawText("Wilayah Kerja  : $printWilayah", 50f, 175f, paint)

        var y = 210f
        paint.isFakeBoldText = true

        val rectPaint = Paint().apply {
            color = Color.LTGRAY
            alpha = 60
        }

        canvas.drawRect(50f, y - 15f, 545f, y + 5f, rectPaint)

        canvas.drawText("No", 55f, y, paint)
        canvas.drawText("Tanggal", 85f, y, paint)
        canvas.drawText("Identifikasi Hama", 185f, y, paint)
        canvas.drawText("Status", 450f, y, paint)

        paint.isFakeBoldText = false

        if (data.isEmpty()) {
            canvas.drawText("TIDAK ADA DATA LAPORAN PADA PERIODE INI", 190f, y + 40f, paint)
        } else {
            data.forEachIndexed { index, it ->
                y += 20f
                canvas.drawText("${index + 1}", 55f, y, paint)
                canvas.drawText(it.created_at.take(10), 85f, y, paint)
                it.label_ai?.let { text -> canvas.drawText(text, 185f, y, paint) }
                canvas.drawText(it.status.replace("_", " ").uppercase(), 450f, y, paint)
                canvas.drawLine(50f, y + 5f, 545f, y + 5f, Paint().apply {
                    color = Color.LTGRAY
                    strokeWidth = 0.5f
                })
            }
        }

        y += 60f

        canvas.drawText(
            "Gorontalo, ${SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())}",
            380f,
            y,
            paint
        )

        canvas.drawText("Petugas POPT,", 380f, y + 15f, paint)

        canvas.drawText(
            _uiState.value.poptProfile?.full_name ?: "________________",
            380f,
            y + 65f,
            paint.apply { isFakeBoldText = true }
        )

        pdfDocument.finishPage(page)

        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Laporan_${label.replace(" ", "_")}.pdf"
        )

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(context, "Laporan PDF Berhasil Diunduh", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    fun downloadCSV(context: Context) {
        val data = _uiState.value.exportPreviewList
        val fileName = "Laporan_${_uiState.value.selectedMonthLabel.replace(" ", "_")}.csv"

        val csvHeader = "No,Tanggal,Hama,Alamat,Status\n"

        val csvContent =
            if (data.isEmpty()) "DATA TIDAK DITEMUKAN"
            else data.mapIndexed { index, it ->
                val cleanAlamat = it.alamat_lengkap?.replace(",", " ") ?: "-"
                "${index + 1},${it.created_at.take(10)},${it.label_ai},${cleanAlamat},${it.status}"
            }.joinToString("\n")

        try {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            file.writeText(csvHeader + csvContent)
            Toast.makeText(context, "CSV Berhasil Diunduh", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Gagal simpan CSV", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            realtimeChannel?.unsubscribe()
        }
    }
}