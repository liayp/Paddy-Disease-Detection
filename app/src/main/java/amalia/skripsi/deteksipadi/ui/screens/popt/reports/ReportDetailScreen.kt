package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import amalia.skripsi.deteksipadi.data.LaporanDto
import amalia.skripsi.deteksipadi.data.LaporanUpdateDto
import amalia.skripsi.deteksipadi.data.MasterHamaDto
import amalia.skripsi.deteksipadi.data.fetchLaporanById
import amalia.skripsi.deteksipadi.data.fetchLaporanUpdates
import amalia.skripsi.deteksipadi.data.fetchMasterHama
import amalia.skripsi.deteksipadi.data.fetchNamaPelapor
import amalia.skripsi.deteksipadi.data.markLaporanSelesai
import amalia.skripsi.deteksipadi.data.rejectLaporan
import amalia.skripsi.deteksipadi.data.updateLaporanStatus
import amalia.skripsi.deteksipadi.data.setLaporanPerluKunjungan
import amalia.skripsi.deteksipadi.ui.components.ZoomableImageDialog
import amalia.skripsi.deteksipadi.ui.screens.general.report.JadwalKunjunganPanel
import amalia.skripsi.deteksipadi.ui.screens.general.report.LaporanTimelineCard
import amalia.skripsi.deteksipadi.data.supabase
import io.github.jan.supabase.auth.auth
import amalia.skripsi.deteksipadi.ui.screens.petani.report.DetailItemRow
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.net.toUri
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    navController: NavController,
    reportData: LaporanDto?,
    reportId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentReport by remember { mutableStateOf(reportData) }
    var namaPetani by remember { mutableStateOf("Memuat nama...") }
    var updateHistory by remember { mutableStateOf<List<LaporanUpdateDto>>(emptyList()) }
    var masterHamaList by remember { mutableStateOf<List<MasterHamaDto>>(emptyList()) }

    var instruksiInput by remember { mutableStateOf("") }
    var radiusInput by remember { mutableDoubleStateOf(0.3) }
    var selectedCorrectedPest by remember { mutableStateOf<String?>(null) }
    var otherPestInput by remember { mutableStateOf("") }
    val effectiveCorrectedPest = when (selectedCorrectedPest) {
        "Lainnya" -> otherPestInput.trim().takeIf { it.isNotBlank() }
        else -> selectedCorrectedPest?.trim()?.takeIf { it.isNotBlank() }
    }
    var alasanKunjunganInput by remember { mutableStateOf("") }
    var showRejectDialog by remember { mutableStateOf(false) }
    var alasanPenolakanInput by remember { mutableStateOf("") }
    var showVisitScheduleDialog by remember { mutableStateOf(false) }
    var selectedVisitEpochMillis by remember { mutableStateOf<Long?>(null) }
    var visitScheduleNote by remember { mutableStateOf("") }

    var timelineRefreshKey by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isImageFullscreen by remember { mutableStateOf(false) }

    // Ambil data jika diakses via deep link ID
    LaunchedEffect(reportData?.id, reportId) {
        val targetId = reportData?.id ?: reportId
        if (targetId != null) {
            val latest = fetchLaporanById(targetId)
            if (latest != null) {
                currentReport = latest
                instruksiInput = latest.instruksi_popt.orEmpty()
                radiusInput = latest.radius
                selectedCorrectedPest = latest.label_efektif
                alasanKunjunganInput = latest.alasan_kunjungan.orEmpty()
                namaPetani = fetchNamaPelapor(latest.petani_id)
            }
            updateHistory = fetchLaporanUpdates(targetId)
            masterHamaList = fetchMasterHama()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, reportData?.id, reportId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val targetId = reportData?.id ?: reportId
                if (targetId != null) {
                    scope.launch {
                        val latest = fetchLaporanById(targetId)
                        if (latest != null) {
                            currentReport = latest
                            instruksiInput = latest.instruksi_popt.orEmpty()
                            radiusInput = latest.radius
                            selectedCorrectedPest = latest.label_efektif
                            alasanKunjunganInput = latest.alasan_kunjungan.orEmpty()
                        }
                        updateHistory = fetchLaporanUpdates(targetId)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (currentReport == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val statusColor = when(currentReport!!.status) {
        "ditolak" -> Color(0xFFD32F2F)
        "selesai", "penanganan_mandiri" -> Color(0xFF388E3C)
        "perlu_kunjungan" -> Color(0xFF7B1FA2)
        else -> Color(0xFFF57C00)
    }

    if (isImageFullscreen) {
        ZoomableImageDialog(imageUrl = currentReport!!.foto_url, onDismiss = { isImageFullscreen = false })
    }

    // Kalkulasi Durasi SLA (Service Level Agreement) dari Created At hingga Selesai/Sekarang
    val durasiPenangananText = remember(currentReport, updateHistory) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val start = sdf.parse(currentReport!!.created_at.take(19)) ?: Date()
            val end = if (currentReport!!.status == "selesai" && updateHistory.isNotEmpty()) {
                sdf.parse(updateHistory.last().created_at.take(19)) ?: Date()
            } else {
                Date()
            }
            val diffMs = end.time - start.time
            val diffHours = diffMs / (1000 * 60 * 60)
            val diffDays = diffHours / 24
            when {

                diffDays > 0 -> "$diffDays Hari ${diffHours % 24} Jam"
                diffHours > 0 -> "$diffHours Jam"
                else -> "Kurang dari 1 Jam"
            }
        } catch (_: Exception) {
            "Tidak dapat dihitung"
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showRejectDialog = false },
            title = { Text("Tolak laporan") },
            text = {
                Column {
                    Text("Laporan yang ditolak tetap disimpan sebagai riwayat. Tuliskan alasan agar petani memahami keputusan POPT.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = alasanPenolakanInput,
                        onValueChange = { alasanPenolakanInput = it },
                        label = { Text("Alasan penolakan") },
                        placeholder = { Text("Contoh: Foto bukan tanaman padi") },
                        minLines = 2,
                        enabled = !isSubmitting
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSubmitting && alasanPenolakanInput.trim().length >= 5,
                    onClick = {
                        isSubmitting = true
                        scope.launch {
                            val result = rejectLaporan(currentReport!!.id, alasanPenolakanInput.trim())
                            if (result.isSuccess) {
                                Toast.makeText(context, "Laporan ditolak dan riwayat tetap disimpan", Toast.LENGTH_LONG).show()
                                showRejectDialog = false
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, result.exceptionOrNull()?.message ?: "Gagal menolak laporan", Toast.LENGTH_LONG).show()
                            }
                            isSubmitting = false
                        }
                    }
                ) { Text(if (isSubmitting) "Memproses..." else "Konfirmasi Tolak") }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }, enabled = !isSubmitting) { Text("Batal") }
            }
        )
    }

    fun openVisitDateTimePicker() {
        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Makassar"))
        val initial = selectedVisitEpochMillis?.let {
            Calendar.getInstance(TimeZone.getTimeZone("Asia/Makassar")).apply { timeInMillis = it }
        } ?: now
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val dateCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Makassar")).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, initial.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, initial.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        dateCalendar.set(Calendar.HOUR_OF_DAY, hour)
                        dateCalendar.set(Calendar.MINUTE, minute)
                        selectedVisitEpochMillis = dateCalendar.timeInMillis
                    },
                    initial.get(Calendar.HOUR_OF_DAY),
                    initial.get(Calendar.MINUTE),
                    true
                ).show()
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun formatVisitDateTime(epochMillis: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("Asia/Makassar") }.format(Date(epochMillis))

    if (showVisitScheduleDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showVisitScheduleDialog = false },
            title = { Text("Jadwalkan Kunjungan POPT") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pilih tanggal dan jam kunjungan sebelum mengubah status laporan.")
                    OutlinedButton(
                        onClick = { openVisitDateTimePicker() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            selectedVisitEpochMillis?.let {
                                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).apply {
                                    timeZone = TimeZone.getTimeZone("Asia/Makassar")
                                }.format(Date(it))
                            } ?: "Pilih tanggal dan jam"
                        )
                    }
                    OutlinedTextField(
                        value = alasanKunjunganInput,
                        onValueChange = { alasanKunjunganInput = it },
                        label = { Text("Alasan kunjungan *") },
                        placeholder = { Text("Jelaskan mengapa pemeriksaan langsung diperlukan") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        enabled = !isSubmitting
                    )
                    OutlinedTextField(
                        value = visitScheduleNote,
                        onValueChange = { visitScheduleNote = it },
                        label = { Text("Catatan jadwal (opsional)") },
                        placeholder = { Text("Contoh: jangan dulu semprot pestisida") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        enabled = !isSubmitting
                    )
                    Text("Alasan minimal 5 karakter. Waktu menggunakan zona Asia/Makassar.", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmitting && selectedVisitEpochMillis != null && alasanKunjunganInput.trim().length >= 5 &&
                            (selectedCorrectedPest != "Lainnya" || otherPestInput.isNotBlank()),
                    onClick = {
                        val target = currentReport ?: return@Button
                        val chosenTime = selectedVisitEpochMillis ?: return@Button
                        isSubmitting = true
                        scope.launch {
                            val result = setLaporanPerluKunjungan(
                                laporanId = target.id,
                                alasan = alasanKunjunganInput,
                                waktuKunjungan = formatVisitDateTime(chosenTime),
                                catatanJadwal = visitScheduleNote,
                                instruksi = instruksiInput,
                                radius = radiusInput,
                                correctedLabel = effectiveCorrectedPest
                            )
                            if (result.isSuccess) {
                                showVisitScheduleDialog = false
                                Toast.makeText(context, "Jadwal kunjungan berhasil disimpan", Toast.LENGTH_LONG).show()
                                currentReport = fetchLaporanById(target.id) ?: currentReport
                                updateHistory = fetchLaporanUpdates(target.id)
                                timelineRefreshKey += 1
                            } else {
                                Toast.makeText(context, result.exceptionOrNull()?.message ?: "Gagal menyimpan jadwal kunjungan", Toast.LENGTH_LONG).show()
                            }
                            isSubmitting = false
                        }
                    }
                ) { Text(if (isSubmitting) "Menyimpan..." else "Simpan Jadwal") }
            },
            dismissButton = {
                TextButton(onClick = { showVisitScheduleDialog = false }, enabled = !isSubmitting) { Text("Batal") }
            }
        )
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // --- HEADER GAMBAR ---
            Box(modifier = Modifier.fillMaxWidth().height(320.dp).clickable { isImageFullscreen = true }) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(currentReport!!.foto_url).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))

                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.padding(top = 40.dp, start = 16.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }

                Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    val acc = (currentReport!!.confidence?.times(100))?.toInt()
                    if (acc != null) {
                        AssistChip(
                            onClick = {},
                            label = { Text("CONFIDENCE AI: $acc%", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary),
                            border = null,
                            shape = RoundedCornerShape(50)
                        )
                    }
                    Text(
                        text = currentReport!!.label_efektif ?: "Belum Teridentifikasi",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Icon(Icons.Default.ZoomIn, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center).size(48.dp))
            }

            // --- PANEL INFORMASI & INTERACTIVE TIMELINE ---
            Column(
                modifier = Modifier.offset(y = (-20).dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(24.dp)
            ) {
                Text("Validasi & Riwayat Investigasi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))

                LaporanTimelineCard(
                    laporanId = currentReport!!.id,
                    refreshKey = "${currentReport!!.status}|${currentReport!!.instruksi_popt}|${currentReport!!.alasan_kunjungan}|$timelineRefreshKey",
                    durationText = durasiPenangananText
                )

                Spacer(modifier = Modifier.height(20.dp))

                DetailItemRow(Icons.Default.Info, "Status Saat Ini", currentReport!!.status.replace("_", " ").uppercase(), statusColor)
                DetailItemRow(Icons.Default.LocationOn, "Lokasi Lapangan", currentReport!!.alamat_lengkap ?: "-", Color(0xFF1976D2))
                DetailItemRow(Icons.Default.Person, "Nama Pelapor", namaPetani, Color(0xFF388E3C))

                if (currentReport!!.status == "perlu_kunjungan") {
                    Spacer(Modifier.height(16.dp))
                    JadwalKunjunganPanel(
                        laporanId = currentReport!!.id,
                        laporanStatus = currentReport!!.status,
                        userId = supabase.auth.currentUserOrNull()?.id.orEmpty(),
                        userRole = "popt",
                        onChanged = { timelineRefreshKey += 1; scope.launch { currentReport = fetchLaporanById(currentReport!!.id) } }
                    )
                }
                if (currentReport!!.status == "perlu_kunjungan" && !currentReport!!.alasan_kunjungan.isNullOrBlank()) {
                    DetailItemRow(Icons.Default.AssignmentLate, "Alasan Perlu Kunjungan", currentReport!!.alasan_kunjungan, Color(0xFF7B1FA2))
                }
                if (currentReport!!.status == "ditolak") {
                    DetailItemRow(Icons.Default.Info, "Alasan Penolakan", currentReport!!.alasan_penolakan ?: "Alasan belum tercatat.", Color(0xFFD32F2F))
                }

                Spacer(modifier = Modifier.height(20.dp))

                ReportCorrectionSelector(
                    selectedPest = selectedCorrectedPest,
                    otherPest = otherPestInput,
                    currentEffectiveLabel = currentReport!!.label_efektif,
                    isManualReport = currentReport!!.jenis_pelaporan.equals("MANUAL", ignoreCase = true),
                    isSubmitting = isSubmitting,
                    onPestSelected = { pest ->
                        selectedCorrectedPest = pest
                        if (pest != "Lainnya") otherPestInput = ""
                    },
                    onOtherPestChanged = { otherPestInput = it }
                )

                ReportUpdateHistorySection(updates = updateHistory)

                Spacer(modifier = Modifier.height(24.dp))

                // --- PANEL INTERVENSI POPT ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Panel Intervensi Ahli", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (currentReport!!.status != "selesai" && currentReport!!.status != "ditolak") {
                            OutlinedTextField(
                                value = instruksiInput,
                                onValueChange = { instruksiInput = it },
                                label = { Text("Instruksi Penanganan Mandiri") },
                                placeholder = { Text("Misal: Semprotkan pestisida dosis 2ml/L...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 3,
                                enabled = !isSubmitting
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Radius Peringatan EWS: ${(radiusInput * 1000).toInt()} Meter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Slider(
                                value = radiusInput.toFloat(),
                                onValueChange = { radiusInput = it.toDouble() },
                                valueRange = 0.1f..2.0f,
                                steps = 19
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            if (currentReport!!.status == "menunggu_verifikasi" || currentReport!!.status == "menunggu") {
                                OutlinedTextField(
                                    value = alasanKunjunganInput,
                                    onValueChange = { alasanKunjunganInput = it },
                                    label = { Text("Alasan perlu kunjungan (jika dipilih)") },
                                    placeholder = { Text("Jelaskan mengapa pemeriksaan langsung diperlukan") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    minLines = 2,
                                    enabled = !isSubmitting
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            isSubmitting = true
                                            scope.launch {
                                                val res = updateLaporanStatus(
                                                    laporanId = currentReport!!.id,
                                                    status = "penanganan_mandiri",
                                                    instruksi = instruksiInput,
                                                    radius = radiusInput,
                                                    correctedLabel = effectiveCorrectedPest
                                                )
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "Rekomendasi penanganan mandiri disimpan", Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                } else {
                                                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal memverifikasi laporan", Toast.LENGTH_LONG).show()
                                                }
                                                isSubmitting = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(50),
                                        enabled = !isSubmitting && instruksiInput.isNotBlank() &&
                                                (selectedCorrectedPest != "Lainnya" || otherPestInput.isNotBlank())
                                    ) { Text("Verifikasi", style = MaterialTheme.typography.labelLarge) }

                                    Button(
                                        onClick = {
                                            selectedVisitEpochMillis = null
                                            visitScheduleNote = ""
                                            showVisitScheduleDialog = true
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                        enabled = !isSubmitting && alasanKunjunganInput.trim().length >= 5
                                    ) { Text("Perlu Kunjungan", style = MaterialTheme.typography.labelLarge) }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { showRejectDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    enabled = !isSubmitting,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, Color(0xFFD32F2F)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Tolak Laporan")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        isSubmitting = true
                                        scope.launch {
                                            val targetStatus = currentReport!!.status
                                            val res = updateLaporanStatus(
                                                laporanId = currentReport!!.id,
                                                status = targetStatus,
                                                instruksi = instruksiInput,
                                                radius = radiusInput,
                                                correctedLabel = effectiveCorrectedPest,
                                                alasanKunjungan = if (targetStatus == "perlu_kunjungan") alasanKunjunganInput else null
                                            )
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Perubahan disimpan", Toast.LENGTH_SHORT).show()
                                                navController.popBackStack()
                                            } else {
                                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal menyimpan perubahan", Toast.LENGTH_LONG).show()
                                            }
                                            isSubmitting = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(50),
                                    enabled = !isSubmitting &&
                                            (selectedCorrectedPest != "Lainnya" || otherPestInput.isNotBlank())
                                ) { Text("Simpan Instruksi / Koreksi", style = MaterialTheme.typography.labelLarge) }

                                if (currentReport!!.status == "penanganan_mandiri") {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = alasanKunjunganInput,
                                        onValueChange = { alasanKunjunganInput = it },
                                        label = { Text("Alasan perlu kunjungan") },
                                        placeholder = { Text("Jelaskan perubahan kondisi atau kebutuhan pemeriksaan langsung") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        enabled = !isSubmitting
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            selectedVisitEpochMillis = null
                                            visitScheduleNote = ""
                                            showVisitScheduleDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        enabled = !isSubmitting && alasanKunjunganInput.trim().length >= 5,
                                        border = BorderStroke(1.dp, Color(0xFF7B1FA2)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7B1FA2))
                                    ) { Text("Ubah Menjadi Perlu Kunjungan") }
                                }

                                if (currentReport!!.status == "perlu_kunjungan") {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            navController.navigate("geofence_update/${currentReport!!.id}/${currentReport!!.lat}/${currentReport!!.lon}")
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(50),
                                        enabled = !isSubmitting
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Catat Foto Hasil Kunjungan")
                                    }
                                }

                                if (currentReport!!.status == "penanganan_mandiri" || currentReport!!.status == "perlu_kunjungan") {
                                    val requiredUpdateExists = if (currentReport!!.status == "penanganan_mandiri") {
                                        updateHistory.any { it.user_id == currentReport!!.petani_id }
                                    } else {
                                        updateHistory.any { it.user_id != currentReport!!.petani_id }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isSubmitting = true
                                                if (!requiredUpdateExists) {
                                                    val message = if (currentReport!!.status == "penanganan_mandiri") {
                                                        "Petani perlu mengirim foto pembaruan kondisi lahan sebelum laporan selesai."
                                                    } else {
                                                        "POPT perlu mencatat foto hasil kunjungan sebelum laporan selesai."
                                                    }
                                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                                    isSubmitting = false
                                                } else {
                                                    val res = markLaporanSelesai(currentReport!!.id)
                                                    if (res.isSuccess) {
                                                        Toast.makeText(context, "Penanganan laporan ditandai selesai", Toast.LENGTH_SHORT).show()
                                                        navController.popBackStack()
                                                    } else {
                                                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Laporan gagal diselesaikan", Toast.LENGTH_LONG).show()
                                                        isSubmitting = false
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                        enabled = !isSubmitting
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Tandai Penanganan Selesai", style = MaterialTheme.typography.labelLarge)
                                    }
                                    Text(
                                        "Selesai berarti tindak lanjut laporan telah dinilai cukup, bukan jaminan tanaman pulih sepenuhnya.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                        } else {
                            val terminalMessage = if (currentReport!!.status == "ditolak") {
                                "Laporan ditolak. Alasan: ${currentReport!!.alasan_penolakan ?: "Tidak ada alasan yang tercatat."}"
                            } else {
                                "Penanganan laporan telah selesai. Riwayat laporan tetap tersimpan."
                            }
                            Text(
                                terminalMessage,
                                color = if (currentReport!!.status == "ditolak") Color(0xFFD32F2F) else Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = {
                        val report = currentReport ?: return@OutlinedButton
                        if (!report.lat.isFinite() || !report.lon.isFinite() ||
                            (report.lat == 0.0 && report.lon == 0.0)) {
                            Toast.makeText(context, "Koordinat laporan tidak tersedia.", Toast.LENGTH_LONG).show()
                        } else {
                            val mapsUri = "https://www.google.com/maps/dir/?api=1&destination=${report.lat},${report.lon}".toUri()
                            val mapIntent = Intent(Intent.ACTION_VIEW, mapsUri)
                            try {
                                context.startActivity(mapIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Aplikasi peta tidak dapat dibuka.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    Text("Buka Lokasi di Google Maps", color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
