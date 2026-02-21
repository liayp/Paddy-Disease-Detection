package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import amalia.skripsi.deteksipadi.data.HotspotDto
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
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@Serializable
data class PoptProfile(
    val full_name: String? = null,
    val wkpp_kecamatan: List<String>? = null
)

data class PoptReportsUiState(
    val processList: List<HotspotDto> = emptyList(),
    val finishedList: List<HotspotDto> = emptyList(),
    val exportPreviewList: List<HotspotDto> = emptyList(),
    val poptProfile: PoptProfile? = null,
    val isLoading: Boolean = false,
    val selectedMonthLabel: String = ""
)

@Suppress("DEPRECATION")
class PoptReportsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PoptReportsUiState())
    val uiState = _uiState.asStateFlow()

    private var allHotspotsForPOPT = listOf<HotspotDto>()

    init {
        loadPoptInitialData()
    }

    fun loadPoptInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            try {
                val profile = supabase.from("profiles").select { filter { eq("id", userId) } }.decodeSingle<PoptProfile>()
                val allData = supabase.from("reports").select { order("created_at", Order.DESCENDING) }.decodeList<HotspotDto>()
                val wkppList = profile.wkpp_kecamatan ?: emptyList()

                allHotspotsForPOPT = allData.filter { report ->
                    wkppList.any { wkpp -> report.kecamatan.trim().equals(wkpp.trim(), ignoreCase = true) }
                }

                _uiState.value = _uiState.value.copy(
                    poptProfile = profile,
                    processList = allHotspotsForPOPT.filter { it.status.lowercase() == "pending" },
                    finishedList = allHotspotsForPOPT.filter { it.status.lowercase() != "pending" },
                    isLoading = false
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun getLastThreeMonths(): List<Pair<String, Pair<Int, Int>>> {
        val months = mutableListOf<Pair<String, Pair<Int, Int>>>()
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        repeat(3) {
            months.add(sdf.format(cal.time) to (cal.get(Calendar.MONTH) to cal.get(Calendar.YEAR)))
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
        val label = SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(cal.time)

        // PERBAIKAN: month + 1 karena database format 01-12, sedangkan Calendar 0-11
        val dbFilterPrefix = String.format("%04d-%02d", year, month + 1)

        val filtered = allHotspotsForPOPT.filter {
            it.created_at.startsWith(dbFilterPrefix)
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

        // PDF FORMAL (Plek ketiplek Preview)
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
        canvas.drawText("Wilayah Kerja  : ${_uiState.value.poptProfile?.wkpp_kecamatan?.joinToString(", ") ?: "-"}", 50f, 175f, paint)

        var y = 210f
        paint.isFakeBoldText = true
        // Header Table Background
        val rectPaint = Paint().apply { color = Color.LTGRAY; alpha = 60 }
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
                canvas.drawText(it.ai_label, 185f, y, paint)
                canvas.drawText(it.status.uppercase(), 450f, y, paint)
                canvas.drawLine(50f, y + 5f, 545f, y + 5f, Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f })
            }
        }

        y += 60f
        canvas.drawText("Gorontalo, ${SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())}", 380f, y, paint)
        canvas.drawText("Petugas POPT,", 380f, y + 15f, paint)
        canvas.drawText(_uiState.value.poptProfile?.full_name ?: "________________", 380f, y + 65f, paint.apply { isFakeBoldText = true })

        pdfDocument.finishPage(page)
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Laporan_${label.replace(" ", "_")}.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(context, "Laporan PDF Berhasil Diunduh", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally { pdfDocument.close() }
    }

    fun downloadCSV(context: Context) {
        val data = _uiState.value.exportPreviewList
        val fileName = "Laporan_${_uiState.value.selectedMonthLabel.replace(" ", "_")}.csv"
        val csvHeader = "No,Tanggal,Hama,Kecamatan,Kelurahan,Status\n"
        val csvContent = if (data.isEmpty()) "DATA TIDAK DITEMUKAN" else data.mapIndexed { index, it ->
            "${index + 1},${it.created_at.take(10)},${it.ai_label},${it.kecamatan},${it.kelurahan},${it.status}"
        }.joinToString("\n")

        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            file.writeText(csvHeader + csvContent)
            Toast.makeText(context, "CSV Berhasil Diunduh", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Gagal simpan CSV", Toast.LENGTH_SHORT).show()
        }
    }
}