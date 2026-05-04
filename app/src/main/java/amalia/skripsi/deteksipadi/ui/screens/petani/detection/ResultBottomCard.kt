package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.data.MasterHamaDto
import amalia.skripsi.deteksipadi.ml.DetectionResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PadiGreen = Color(0xFF4CB64E)
val TextDark = Color(0xFF2D3E2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSheetContent(
    results: List<DetectionResult>,
    locationStr: String?,
    isLoading: Boolean,
    deskripsi: String,
    onDeskripsiChange: (String) -> Unit,
    isManualMode: Boolean,
    onManualModeChange: (Boolean) -> Unit,
    manualPestSelection: String,
    onManualPestSelect: (String) -> Unit,
    masterHamaList: List<MasterHamaDto>, // <-- Parameter Baru
    onSend: () -> Unit
) {
    val isLocationValid = locationStr != null

    // HAPUS LaunchedEffect(results) YANG LAMA, SUDAH DITANGANI DI processResult()

    val canSend = !isLoading && isLocationValid && deskripsi.trim().isNotBlank() &&
            (if (isManualMode) true else results.isNotEmpty())

    // Cari edukasi berdasarkan hama yang terpilih (AI)
    val currentPestName = if (isManualMode) manualPestSelection else results.maxByOrNull { it.score }?.label
    val edukasiHama = masterHamaList.find { it.nama_hama.equals(currentPestName, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TAB SELECTOR
        TabRow(
            selectedTabIndex = if (isManualMode) 1 else 0,
            containerColor = Color.Transparent,
            contentColor = PadiGreen,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Tab(
                selected = !isManualMode,
                onClick = { if (results.isNotEmpty()) onManualModeChange(false) },
                text = { Text("Hasil AI", fontWeight = FontWeight.Bold) },
                enabled = results.isNotEmpty()
            )
            Tab(
                selected = isManualMode,
                onClick = { onManualModeChange(true) },
                text = { Text("Lapor Manual", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // KONTEN BERDASARKAN MODE
        if (!isManualMode) {
            // MODE AI
            val summary = results.groupingBy { it.label }.eachCount()
            val maxScore = results.maxOfOrNull { it.score } ?: 0f

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Objek: ${results.size}", fontWeight = FontWeight.Bold, color = TextDark)
                        Surface(color = if (maxScore > 0.5f) PadiGreen else Color.Gray, shape = RoundedCornerShape(8.dp)) {
                            Text("Max: ${(maxScore * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha=0.5f))
                    summary.forEach { (label, count) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text("$count titik", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // MODE MANUAL
            LaunchedEffect(true) {
                if (isManualMode) onManualPestSelect("Tidak Tahu")
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Belum Teridentifikasi", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sistem AI tidak dapat mengenali indikasi hama pada gambar. Silakan isi deskripsi selengkap mungkin agar petugas POPT dapat menganalisis laporan Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                }
            }
        }

        // --- INFO PERTOLONGAN PERTAMA (MUNCUL JIKA ADA HAMA TERDETEKSI) ---
        if (edukasiHama != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), // Biru Muda Informasional
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Info & Pertolongan Pertama", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(edukasiHama.deskripsi, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(edukasiHama.pertolongan_pertama, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFB71C1C)) // Merah gelap untuk urgensi
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // LOKASI GPS
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.LocationOn, "Loc", tint = if (isLocationValid) PadiGreen else Color.Red, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(locationStr ?: "Lokasi tidak ditemukan (Wajib GPS)", style = MaterialTheme.typography.bodyMedium, color = if (isLocationValid) Color.DarkGray else Color.Red)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // DESKRIPSI WAJIB
        OutlinedTextField(
            value = deskripsi,
            onValueChange = onDeskripsiChange,
            label = { Text("Deskripsi Gejala Lapangan (Wajib)", fontSize = 14.sp) },
            placeholder = { Text("Contoh: Daun bercak coklat / Ada ulat di batang...", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 3,
            maxLines = 5,
            isError = deskripsi.isBlank(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PadiGreen, unfocusedBorderColor = Color.LightGray)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSend,
            enabled = canSend,
            colors = ButtonDefaults.buttonColors(containerColor = PadiGreen, disabledContainerColor = Color.Gray),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.Send, null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kirim Laporan Peringatan", fontWeight = FontWeight.Bold)
            }
        }
    }
}