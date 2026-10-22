package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ReportCorrectionSelector(
    selectedPest: String?,
    otherPest: String,
    currentEffectiveLabel: String?,
    isManualReport: Boolean,
    isSubmitting: Boolean,
    onPestSelected: (String) -> Unit,
    onOtherPestChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val pestOptions = listOf("Hama Putih Palsu", "Blast", "Hawar Daun Bakteri", "Stem Borer", "Lainnya")

    Text("Koreksi Klasifikasi Hama", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth()) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Nama Hama Final (Validasi Ahli)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        selectedPest ?: currentEffectiveLabel?.takeIf(String::isNotBlank)
                            ?: if (isManualReport) "Pilih hama/penyakit (laporan manual)" else "Pilih Hama",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            pestOptions.forEach { pest ->
                DropdownMenuItem(
                    text = { Text(pest) },
                    onClick = { onPestSelected(pest); expanded = false }
                )
            }
        }
    }

    if (selectedPest == "Lainnya") {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = otherPest,
            onValueChange = onOtherPestChanged,
            label = { Text("Nama hama/penyakit lainnya") },
            placeholder = { Text("Contoh: Wereng Batang Cokelat") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isSubmitting
        )
        Text(
            "Isi nama berdasarkan pemeriksaan POPT. Nama ini disimpan sebagai klasifikasi laporan, bukan kelas prediksi AI.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
