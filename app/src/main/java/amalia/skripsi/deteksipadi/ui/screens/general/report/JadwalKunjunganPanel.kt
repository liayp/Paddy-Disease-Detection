package amalia.skripsi.deteksipadi.ui.screens.general.report

import amalia.skripsi.deteksipadi.data.JadwalKunjunganDto
import amalia.skripsi.deteksipadi.data.buatJadwalKunjungan
import amalia.skripsi.deteksipadi.data.fetchJadwalKunjungan
import amalia.skripsi.deteksipadi.data.konfirmasiJadwalKunjungan
import amalia.skripsi.deteksipadi.data.tolakUsulanJadwalUlang
import amalia.skripsi.deteksipadi.data.terimaUsulanJadwalUlang
import amalia.skripsi.deteksipadi.data.ubahJadwalKunjungan
import amalia.skripsi.deteksipadi.data.usulkanJadwalUlang
import amalia.skripsi.deteksipadi.data.updateCatatanKunjungan
import amalia.skripsi.deteksipadi.util.VisitReminderScheduler
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Panel jadwal kunjungan yang dipakai Petani dan POPT.
 * Untuk laporan baru, status perlu_kunjungan dan jadwal dibuat secara atomik
 * dari ReportDetailScreen. Tombol pembuatan jadwal di panel hanya menjadi
 * fallback untuk laporan lama yang sudah terlanjur berstatus perlu_kunjungan.
 */
@Composable
fun JadwalKunjunganPanel(
    laporanId: String,
    laporanStatus: String,
    userId: String,
    userRole: String,
    onChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jadwalList by remember { mutableStateOf<List<JadwalKunjunganDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showSchedulePicker by remember { mutableStateOf(false) }
    var showRescheduleDialog by remember { mutableStateOf<JadwalKunjunganDto?>(null) }
    var catatanJadwal by remember { mutableStateOf("") }
    var alasanReschedule by remember { mutableStateOf("") }
    var selectedEpochMillis by remember { mutableStateOf<Long?>(null) }
    var editingScheduleId by remember { mutableStateOf<String?>(null) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var catatanEdit by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            isLoading = true
            runCatching { fetchJadwalKunjungan(laporanId) }
                .onSuccess { visits ->
                    jadwalList = visits
                    VisitReminderScheduler.sync(context, laporanId, visits)
                }
                .onFailure {
                    Toast.makeText(context, "Jadwal kunjungan belum dapat dimuat. Periksa koneksi lalu coba lagi.", Toast.LENGTH_LONG).show()
                }
            isLoading = false
        }
    }

    LaunchedEffect(laporanId) { load() }

    fun isoDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Makassar")
        }.format(Date(millis))

    fun readableDate(value: String): String = try {
        val parsed = try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)
        } catch (_: Exception) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(value.take(19))
        }
        SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", Locale("id", "ID")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Makassar")
        }.format(parsed ?: Date())
    } catch (_: Exception) {
        value.replace("T", " ").take(16)
    }

    fun openDateTimePicker(onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Makassar")).apply { add(Calendar.DAY_OF_MONTH, 1) }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val chosen = Calendar.getInstance(TimeZone.getTimeZone("Asia/Makassar")).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                TimePickerDialog(context, { _, hour, minute ->
                    chosen.set(Calendar.HOUR_OF_DAY, hour)
                    chosen.set(Calendar.MINUTE, minute)
                    chosen.set(Calendar.SECOND, 0)
                    chosen.set(Calendar.MILLISECOND, 0)
                    if (chosen.timeInMillis <= System.currentTimeMillis()) {
                        Toast.makeText(context, "Pilih waktu yang akan datang.", Toast.LENGTH_LONG).show()
                    } else onPicked(chosen.timeInMillis)
                }, 9, 0, true).show()
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
    }


    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Jadwal Kunjungan Lapangan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (laporanStatus == "perlu_kunjungan") "Jadwal, konfirmasi, dan perubahan waktu dikelola dari laporan yang sama."
                else "Jadwal tersedia apabila laporan memerlukan pemeriksaan langsung.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (isLoading) CircularProgressIndicator()
            if (!isLoading && jadwalList.isEmpty()) {
                Text("Belum ada jadwal kunjungan.", style = MaterialTheme.typography.bodyMedium)
            }

            jadwalList.forEach { jadwal ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(readableDate(jadwal.waktu_kunjungan), fontWeight = FontWeight.SemiBold)
                                Text("Status: ${jadwal.status.replace('_', ' ')}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        jadwal.catatan?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(6.dp)); Text("Catatan: $it", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (userRole == "popt" && jadwal.status !in listOf("terlaksana", "dibatalkan")) {
                            Spacer(Modifier.height(8.dp))
                            if (editingNoteId == jadwal.id) {
                                OutlinedTextField(
                                    value = catatanEdit,
                                    onValueChange = { catatanEdit = it },
                                    label = { Text("Catatan kunjungan") },
                                    placeholder = { Text("Catatan tambahan untuk petani sebelum kunjungan") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isSaving
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        enabled = !isSaving,
                                        onClick = {
                                            isSaving = true
                                            scope.launch {
                                                val result = updateCatatanKunjungan(jadwal.id, catatanEdit)
                                                Toast.makeText(context, if (result.isSuccess) "Catatan kunjungan diperbarui" else result.exceptionOrNull()?.message ?: "Gagal memperbarui catatan", Toast.LENGTH_LONG).show()
                                                isSaving = false
                                                if (result.isSuccess) {
                                                    editingNoteId = null
                                                    catatanEdit = ""
                                                    load()
                                                    onChanged()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Simpan Catatan") }
                                    OutlinedButton(
                                        enabled = !isSaving,
                                        onClick = { editingNoteId = null; catatanEdit = "" },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Batal") }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { editingNoteId = jadwal.id; catatanEdit = jadwal.catatan.orEmpty() },
                                    enabled = !isSaving,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (jadwal.catatan.isNullOrBlank()) "Tambah Catatan Kunjungan" else "Ubah Catatan Kunjungan")
                                }
                            }
                        }
                        if (jadwal.status == "usulan_ulang" && jadwal.usulan_waktu != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Usulan petani: ${readableDate(jadwal.usulan_waktu)}", fontWeight = FontWeight.SemiBold)
                            Text("Alasan: ${jadwal.alasan_reschedule.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                        }
                        jadwal.catatan_hasil?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(8.dp)); Text("Hasil kunjungan: $it", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!jadwal.evidence_foto_url.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("✓ Foto bukti kunjungan tersimpan", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(Modifier.height(10.dp))
                        if (userRole == "petani" && jadwal.status == "menunggu_konfirmasi") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !isSaving, onClick = {
                                    isSaving = true
                                    scope.launch {
                                        val result = konfirmasiJadwalKunjungan(jadwal.id)
                                        Toast.makeText(context, if (result.isSuccess) "Jadwal dikonfirmasi" else result.exceptionOrNull()?.message ?: "Gagal mengonfirmasi", Toast.LENGTH_LONG).show()
                                        isSaving = false; load(); onChanged()
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("Konfirmasi") }
                                OutlinedButton(enabled = !isSaving, onClick = { showRescheduleDialog = jadwal; alasanReschedule = "" }) { Text("Jadwal Ulang") }
                            }
                        }
                        if (userRole == "petani" && jadwal.status in listOf("dikonfirmasi", "usulan_ditolak")) {
                            OutlinedButton(onClick = { showRescheduleDialog = jadwal; alasanReschedule = "" }, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) { Text("Usulkan Jadwal Ulang") }
                        }
                        if (userRole == "popt" && jadwal.status == "usulan_ulang") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !isSaving, onClick = {
                                    isSaving = true
                                    scope.launch {
                                        val result = terimaUsulanJadwalUlang(jadwal.id)
                                        Toast.makeText(context, if (result.isSuccess) "Usulan jadwal diterima" else result.exceptionOrNull()?.message ?: "Gagal menerima usulan", Toast.LENGTH_LONG).show()
                                        isSaving = false; load(); onChanged()
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("Setujui") }
                                OutlinedButton(enabled = !isSaving, onClick = {
                                    isSaving = true
                                    scope.launch {
                                        val result = tolakUsulanJadwalUlang(jadwal.id)
                                        Toast.makeText(context, if (result.isSuccess) "Usulan ditolak; jadwal awal tetap berlaku" else result.exceptionOrNull()?.message ?: "Gagal menolak usulan", Toast.LENGTH_LONG).show()
                                        isSaving = false; load(); onChanged()
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("Tolak") }
                            }
                        }
                        if (userRole == "popt" && jadwal.status in listOf("dikonfirmasi", "menunggu_konfirmasi", "usulan_ditolak")) {
                            OutlinedButton(onClick = {
                                openDateTimePicker { selectedEpochMillis = it; editingScheduleId = jadwal.id; catatanJadwal = jadwal.catatan.orEmpty(); showSchedulePicker = true }
                            }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) { Text("Ubah Waktu") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Legacy fallback: laporan lama mungkin sudah berstatus perlu_kunjungan tetapi belum memiliki jadwal.
            if (userRole == "popt" && laporanStatus == "perlu_kunjungan" && jadwalList.none { it.status !in listOf("terlaksana", "dibatalkan") }) {
                Button(onClick = { openDateTimePicker { selectedEpochMillis = it; editingScheduleId = null; catatanJadwal = ""; showSchedulePicker = true } }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("Buat Jadwal untuk Laporan Lama")
                }
            }
        }
    }

    if (showSchedulePicker && selectedEpochMillis != null) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) { showSchedulePicker = false; editingScheduleId = null; selectedEpochMillis = null } },
            title = { Text(if (editingScheduleId == null) "Buat Jadwal Kunjungan" else "Ubah Jadwal Kunjungan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", Locale("id", "ID")).apply {
                        timeZone = TimeZone.getTimeZone("Asia/Makassar")
                    }.format(Date(selectedEpochMillis!!)))
                    OutlinedTextField(value = catatanJadwal, onValueChange = { catatanJadwal = it }, label = { Text("Catatan untuk petani (opsional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = !isSaving, onClick = {
                    isSaving = true
                    scope.launch {
                        val result = if (editingScheduleId == null) {
                            buatJadwalKunjungan(laporanId, userId, isoDate(selectedEpochMillis!!), catatanJadwal)
                        } else {
                            amalia.skripsi.deteksipadi.data.ubahJadwalKunjungan(editingScheduleId!!, isoDate(selectedEpochMillis!!), catatanJadwal)
                        }
                        Toast.makeText(context, if (result.isSuccess) "Jadwal tersimpan" else result.exceptionOrNull()?.message ?: "Gagal menyimpan jadwal", Toast.LENGTH_LONG).show()
                        isSaving = false; showSchedulePicker = false; editingScheduleId = null; selectedEpochMillis = null; load(); onChanged()
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showSchedulePicker = false; editingScheduleId = null; selectedEpochMillis = null }) { Text("Batal") } }
        )
    }

    showRescheduleDialog?.let { jadwal ->
        AlertDialog(
            onDismissRequest = { if (!isSaving) showRescheduleDialog = null },
            title = { Text("Usulkan Jadwal Ulang") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pilih waktu baru dan jelaskan alasannya.")
                    OutlinedButton(onClick = { openDateTimePicker { selectedEpochMillis = it } }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(selectedEpochMillis?.let { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("id", "ID")).format(Date(it)) } ?: "Pilih waktu baru")
                    }
                    OutlinedTextField(value = alasanReschedule, onValueChange = { alasanReschedule = it }, label = { Text("Alasan") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = !isSaving && selectedEpochMillis != null && alasanReschedule.trim().length >= 5, onClick = {
                    isSaving = true
                    scope.launch {
                        val result = usulkanJadwalUlang(jadwal.id, isoDate(selectedEpochMillis!!), alasanReschedule)
                        Toast.makeText(context, if (result.isSuccess) "Usulan jadwal dikirim" else result.exceptionOrNull()?.message ?: "Gagal mengirim usulan", Toast.LENGTH_LONG).show()
                        isSaving = false; showRescheduleDialog = null; selectedEpochMillis = null; load(); onChanged()
                    }
                }) { Text("Kirim Usulan") }
            },
            dismissButton = { TextButton(onClick = { showRescheduleDialog = null; selectedEpochMillis = null }) { Text("Batal") } }
        )
    }

}
