package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val PadiGreen = Color(0xFF4CB64E)
val TextDark = Color(0xFF2D3E2E)

@Composable
fun ResultSheetContent(
    label: String,
    confidence: Float,
    locationStr: String?,
    isLoading: Boolean,
    onSend: () -> Unit
) {
    val isLocationValid = locationStr != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Drag Handle (Garis Abu-abu Kecil)
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(Color.Gray.copy(alpha = 0.4f), CircleShape)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Hasil Analisis",
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
                // Label & Score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Terdeteksi: $label",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextDark
                    )
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        color = if (confidence >= 0.8) PadiGreen else Color(0xFFF57C00),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                        text = locationStr ?: "Lokasi tidak ditemukan!",
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
            enabled = !isLoading && isLocationValid,
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
                Text("Mengirim...")
            } else {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kirim Laporan ke POPT", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}