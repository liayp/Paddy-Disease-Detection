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
                title = { Text("Preview Laporan", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                windowInsets = WindowInsets(0),
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        contentDescription = "Kembali"
                    ) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        bottomBar = {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.downloadCSV(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.TableChart, "Download CSV"); Spacer(Modifier.width(8.dp)); Text("CSV")
                }
                Button(onClick = { viewModel.downloadPDF(context) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Download PDF")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
            Card(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(2.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // HEADER FORMAL
                    Text("PEMERINTAH PROVINSI GORONTALO", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("DINAS PERTANIAN", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("BALAI PERLINDUNGAN TANAMAN PANGAN DAN HORTIKULTURA", fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(thickness = 2.dp, color = Color.Black)
                    HorizontalDivider(Modifier.padding(top = 1.dp), thickness = 0.5.dp, color = Color.Black)

                    Spacer(Modifier.height(20.dp))
                    Text("LAPORAN REKAPITULASI DETEKSI HAMA PADI", fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("Periode: ${state.selectedMonthLabel}", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 10.sp)

                    Spacer(Modifier.height(24.dp))
                    Text("Nama Petugas : ${state.poptProfile?.full_name ?: "-"}", fontSize = 10.sp)
                    Text("Wilayah Kerja : ${state.poptProfile?.wkpp_kecamatan?.joinToString(", ") ?: "-"}", fontSize = 10.sp)

                    Spacer(Modifier.height(16.dp))

                    // GRID TABLE (Formal Style)
                    Column(Modifier.border(0.5.dp, Color.Black)) {
                        Row(Modifier.background(Color(0xFFF2F2F2)).padding(6.dp)) {
                            Text("No", Modifier.width(25.dp), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            Text("Tanggal", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            Text("Identifikasi Hama", Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            Text("Status", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        }

                        if (state.exportPreviewList.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text("Data laporan tidak ditemukan.", fontSize = 9.sp, fontStyle = FontStyle.Italic)
                            }
                        } else {
                            state.exportPreviewList.forEachIndexed { index, report ->
                                HorizontalDivider(thickness = 0.5.dp, color = Color.Black)
                                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}", Modifier.width(25.dp), fontSize = 9.sp)
                                    Text(report.created_at.take(10), Modifier.weight(1f), fontSize = 9.sp)
                                    Text(report.label_ai, Modifier.weight(1.5f), fontSize = 9.sp)

                                    val isPending = report.status == "menunggu_verifikasi" || report.status == "perlu_kunjungan"
                                    val statusColor = if(isPending) Color.Red else MaterialTheme.colorScheme.primary

                                    Text(report.status.replace("_", " ").uppercase(), Modifier.weight(1f), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(40.dp))
                    Column(modifier = Modifier.align(Alignment.End), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gorontalo, $today", fontSize = 10.sp)
                        Text("Petugas POPT,", fontSize = 10.sp)
                        Spacer(Modifier.height(50.dp))
                        Text(state.poptProfile?.full_name ?: "________________", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}