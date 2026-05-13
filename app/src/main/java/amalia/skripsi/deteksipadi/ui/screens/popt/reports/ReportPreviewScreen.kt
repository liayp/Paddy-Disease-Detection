@file:Suppress("DEPRECATION")
package amalia.skripsi.deteksipadi.ui.screens.popt.reports

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportPreviewScreen(
    navController: NavController,
    viewModel: PoptReportsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val today = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview Laporan Bulanan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, tint = MaterialTheme.colorScheme.onPrimary, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        bottomBar = {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.downloadCSV(context) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.TableChart, "Download CSV"); Spacer(Modifier.width(8.dp)); Text("Ekspor CSV", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.downloadPDF(context) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Unduh PDF", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {

            // INDIKATOR FILTER AKTIF
            if (state.selectedFilterKecamatan != "Semua Wilayah") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Laporan ini telah disaring khusus untuk wilayah: ${state.selectedFilterKecamatan}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // HEADER FORMAL
                    Text("PEMERINTAH PROVINSI GORONTALO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("DINAS PERTANIAN", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("BALAI PERLINDUNGAN TANAMAN PANGAN DAN HORTIKULTURA", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(thickness = 2.dp, color = Color.Black)
                    HorizontalDivider(Modifier.padding(top = 1.dp), thickness = 0.5.dp, color = Color.Black)

                    Spacer(Modifier.height(24.dp))
                    Text("LAPORAN REKAPITULASI DETEKSI HAMA PADI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("Periode: ${state.selectedMonthLabel}", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)

                    Spacer(Modifier.height(28.dp))
                    Text("Nama Petugas : ${state.poptProfile?.full_name ?: "-"}", style = MaterialTheme.typography.bodyMedium)

                    val printWilayah = if (state.selectedFilterKecamatan == "Semua Wilayah") {
                        state.poptProfile?.wkpp_kecamatan?.joinToString(", ") ?: "-"
                    } else {
                        state.selectedFilterKecamatan
                    }
                    Text("Wilayah Kerja   : $printWilayah", style = MaterialTheme.typography.bodyMedium)

                    Spacer(Modifier.height(20.dp))

                    // GRID TABLE (Formal Style)
                    Column(Modifier.border(0.5.dp, Color.Black)) {
                        Row(Modifier.background(Color(0xFFF2F2F2)).padding(8.dp)) {
                            Text("No", Modifier.width(30.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Text("Tanggal", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Text("Identifikasi Hama", Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Text("Status", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }

                        if (state.exportPreviewList.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text("Data laporan tidak ditemukan pada filter ini.", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Color.Gray)
                            }
                        } else {
                            state.exportPreviewList.forEachIndexed { index, report ->
                                HorizontalDivider(thickness = 0.5.dp, color = Color.Black)
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}", Modifier.width(30.dp), style = MaterialTheme.typography.labelMedium)
                                    Text(report.created_at.take(10), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                    Text(report.label_ai ?: "-", Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium)

                                    val isPending = report.status == "menunggu_verifikasi" || report.status == "perlu_kunjungan"
                                    val statusColor = if(isPending) Color.Red else MaterialTheme.colorScheme.primary

                                    Text(report.status.replace("_", " ").uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = statusColor)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(50.dp))
                    Column(modifier = Modifier.align(Alignment.End), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gorontalo, $today", style = MaterialTheme.typography.bodyMedium)
                        Text("Petugas POPT,", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(60.dp))
                        Text(state.poptProfile?.full_name ?: "________________", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}