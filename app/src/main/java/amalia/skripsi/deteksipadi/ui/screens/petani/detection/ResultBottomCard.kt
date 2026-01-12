package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import amalia.skripsi.deteksipadi.ml.DetectionResult

val PadiGreen = Color(0xFF4CB64E)
val TextDark = Color(0xFF2D3E2E)

@Composable
fun ResultSheetContent(
    results: List<DetectionResult>, // Terima LIST, bukan single string
    locationStr: String?,
    isLoading: Boolean,
    onSend: () -> Unit
) {
    val isLocationValid = locationStr != null

    // Grouping hasil: Hitung jumlah setiap hama
    // Contoh: { "Blas" : 3, "HDB" : 1 }
    val summary = results.groupingBy { it.label }.eachCount()

    // Ambil skor tertinggi untuk display header
    val maxScore = results.maxOfOrNull { it.score } ?: 0f
    val isHighRisk = maxScore > 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Hasil Analisis Deteksi",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Header: Total Objek
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Objek: ${results.size}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextDark
                    )

                    // Badge Score Tertinggi
                    Surface(
                        color = if (isHighRisk) PadiGreen else Color.Gray,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Max: ${(maxScore * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha=0.5f))

                // List Rincian (Looping Summary)
                summary.forEach { (label, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        Text(text = "$count titik", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lokasi
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "Loc",
                        tint = if (isLocationValid) Color.Gray else Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = locationStr ?: "Lokasi tidak ditemukan (Wajib GPS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLocationValid) Color.Gray else Color.Red
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tombol Kirim
        Button(
            onClick = onSend,
            enabled = !isLoading && isLocationValid && results.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PadiGreen,
                disabledContainerColor = Color.Gray
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mengirim Laporan...")
            } else {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Laporkan ke Sistem & POPT", fontWeight = FontWeight.Bold)
            }
        }

        if (maxScore > 0.5f) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Info: Skor tinggi (>50%) akan langsung muncul di Peta.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}