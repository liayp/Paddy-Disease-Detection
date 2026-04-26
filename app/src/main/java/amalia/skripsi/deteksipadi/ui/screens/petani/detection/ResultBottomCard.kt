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
import androidx.compose.ui.unit.sp

val PadiGreen = Color(0xFF4CB64E)
val TextDark = Color(0xFF2D3E2E)

@Composable
fun ResultSheetContent(
    results: List<DetectionResult>,
    locationStr: String?,
    isLoading: Boolean,
    deskripsi: String,
    onDeskripsiChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val isLocationValid = locationStr != null

    val summary = results.groupingBy { it.label }.eachCount()
    val maxScore = results.maxOfOrNull { it.score } ?: 0f
    val isHighRisk = maxScore > 0.5f

    //validasi data yang harus ada agar bisa mengirim laporan
    val canSend = !isLoading && isLocationValid && results.isNotEmpty() && deskripsi.trim().isNotBlank()

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
                    Text(
                        text = "Total Objek: ${results.size}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextDark
                    )

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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha=0.5f))

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

        Spacer(modifier = Modifier.height(16.dp))

        // FIELD DESKRIPSI GEJALA
        OutlinedTextField(
            value = deskripsi,
            onValueChange = onDeskripsiChange,
            label = { Text("Deskripsi Gejala Lapangan", fontSize = 14.sp) },
            placeholder = { Text("Contoh: Daun terlihat bercak coklat dan mulai layu...", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PadiGreen,
                unfocusedBorderColor = Color.LightGray
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSend,
            enabled = canSend,
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
                Text("Mengirim Peringatan Dini...")
            } else {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kirim ke Sistem Peringatan Dini", fontWeight = FontWeight.Bold)
            }
        }

        if (maxScore > 0.5f) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Info: Skor >50% akan langsung memicu radius bahaya di Peta EWS.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }

        if (!canSend && results.isNotEmpty() && isLocationValid && deskripsi.isBlank()) {
            Text(
                text = "*Wajib mengisi deskripsi gejala sebelum mengirim",
                color = Color.Red,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}