package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.data.MasterHamaDto
import amalia.skripsi.deteksipadi.ml.DetectionResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    masterHamaList: List<MasterHamaDto>,
    onSend: () -> Unit
) {
    val isLocationValid = locationStr != null

    val canSend = !isLoading && isLocationValid && deskripsi.trim().isNotBlank() &&
            (if (isManualMode) true else results.isNotEmpty())

    val currentPestName = if (isManualMode) manualPestSelection else results.maxByOrNull { it.score }?.label
    val edukasiHama = masterHamaList.find { it.nama_hama.equals(currentPestName, ignoreCase = true) }

    // PARENT COLUMN: Menggunakan fillMaxHeight agar BottomSheet bisa merentang dan di-scroll di dalamnya
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f), // Menyisakan sedikit ruang di atas agar gestur tutup sheet tetap mudah
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. STICKY TABS (Tetap terlihat di atas meski konten di-scroll) ---
        TabRow(
            selectedTabIndex = if (isManualMode) 1 else 0,
            containerColor = Color.Transparent,
            contentColor = PadiGreen,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)
        ) {
            Tab(
                selected = !isManualMode,
                onClick = { if (results.isNotEmpty()) onManualModeChange(false) },
                text = { Text("Hasil Deteksi AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                enabled = results.isNotEmpty()
            )
            Tab(
                selected = isManualMode,
                onClick = { onManualModeChange(true) },
                text = { Text("Lapor Manual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            )
        }

        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

        // --- 2. SCROLLABLE CONTENT (Hanya bagian bawah yang bisa di-scroll) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Mengambil sisa ruang tinggi yang ada
                .verticalScroll(rememberScrollState()) // Konten ini yang bisa di-scroll
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // KONTEN BERDASARKAN MODE
            if (!isManualMode) {
                // MODE AI
                val summary = results.groupingBy { it.label }.eachCount()
                val maxScore = results.maxOfOrNull { it.score } ?: 0f

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Objek: ${results.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
                            Surface(color = if (maxScore > 0.5f) PadiGreen else Color.Gray, shape = RoundedCornerShape(8.dp)) {
                                Text("Akurasi Max: ${(maxScore * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha=0.5f))
                        summary.forEach { (label, count) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                Text("$count Temuan", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextDark)
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Belum Teridentifikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Sistem AI tidak dapat mengenali indikasi hama secara otomatis. Silakan isi deskripsi selengkap mungkin agar petugas POPT dapat menganalisis laporan Anda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Justify
                        )
                    }
                }
            }

            // --- EDUKASI & PANDUAN AWAL (DARI DATABASE) ---
            if (edukasiHama != null) {
                Spacer(modifier = Modifier.height(16.dp))
                var isExpanded by remember { mutableStateOf(false) }

                // Modifer Clickable di level Card agar bisa disentuh di mana saja
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Panduan Penanganan Awal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            // Icon Penanda Expand/Collapse
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Disclaimer AI
                            Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Disclaimer: Hasil deteksi AI ini bersifat sebagai deteksi awal dan bukan diagnosis final. Rekomendasi di bawah ini adalah PHT dasar yang aman dilakukan sambil menunggu verifikasi petugas POPT.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE65100),
                                    textAlign = TextAlign.Justify,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Deskripsi Umum
                            Text("Karakteristik Hama/Penyakit:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(edukasiHama.deskripsi, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, textAlign = TextAlign.Justify)

                            Spacer(modifier = Modifier.height(16.dp))

                            // Ciri-Ciri (Diubah menjadi Bullet Points rapi)
                            Text("Ciri-Ciri Fisik Utama:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))

                            // Memecah baris berdasarkan Enter (\n) dan menghapus tanda strip bawaan jika ada
                            val ciriList = edukasiHama.ciri_ciri.split("\n")
                            ciriList.forEach { ciri ->
                                val cleanCiri = ciri.trim().removePrefix("-").trim()
                                if (cleanCiri.isNotBlank()) {
                                    Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                                        Text("•", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(cleanCiri, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, textAlign = TextAlign.Justify)
                                    }
                                }
                            }

                            // List Tindakan Berdasarkan Tabel informasi_hama (Relasi)
                            if (edukasiHama.informasi_hama.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(16.dp))

                                val groupedInfo = edukasiHama.informasi_hama.groupBy { it.kategori }

                                groupedInfo.forEach { (kategori, listInfo) ->
                                    Text(kategori, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    listInfo.sortedBy { it.urutan }.forEach { info ->
                                        Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                                            Text("•", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(info.isi_informasi, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, textAlign = TextAlign.Justify)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Ketuk kembali untuk menyembunyikan panduan", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)

                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text("Ketuk untuk membaca panduan tindakan awal dan edukasi hama.", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LOKASI GPS
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.LocationOn, "Loc", tint = if (isLocationValid) PadiGreen else Color.Red, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(locationStr ?: "Sinyal GPS belum ditemukan (Wajib)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (isLocationValid) Color.DarkGray else Color.Red)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DESKRIPSI WAJIB
            OutlinedTextField(
                value = deskripsi,
                onValueChange = onDeskripsiChange,
                label = { Text("Deskripsi Gejala di Lapangan (Wajib)", style = MaterialTheme.typography.bodyMedium) },
                placeholder = { Text("Contoh: Daun mengering dari ujung...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                minLines = 3,
                maxLines = 5,
                isError = deskripsi.isBlank(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PadiGreen,
                    unfocusedBorderColor = Color.LightGray,
                    focusedLabelColor = PadiGreen
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSend,
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(containerColor = PadiGreen, disabledContainerColor = Color.Gray),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Send, null, tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Kirim Laporan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}