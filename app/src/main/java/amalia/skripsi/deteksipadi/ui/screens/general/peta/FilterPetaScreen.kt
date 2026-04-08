package amalia.skripsi.deteksipadi.ui.screens.general.peta

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPetaScreen(
    navController: NavController,
    viewModel: PetaViewModel
) {
    val labelsHama = listOf("Semua Hama", "Blast", "Hama Putih Palsu", "Hawar Daun Bakteri", "Stem Borer")
    val timeOptions = listOf("Semua", "Hari ini", "7 Hari Terakhir", "Pilih Bulan", "Pilih Tanggal")

    var showHamaSheet by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filter Peta Sebaran", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetFilter() }) {
                        Text("Reset", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    viewModel.applyFilter()
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Terapkan Filter", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
        ) {
            // --- RENTANG WAKTU ---
            Text("Rentang Waktu Laporan", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            Column(modifier = Modifier.background(Color.Transparent)) {
                timeOptions.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.selectedTimeRange = option
                            if (option == "Pilih Tanggal") showDatePicker = true
                        }.padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(option, modifier = Modifier.weight(1f), fontSize = 16.sp)
                        RadioButton(selected = viewModel.selectedTimeRange == option, onClick = {
                            viewModel.selectedTimeRange = option
                            if (option == "Pilih Tanggal") showDatePicker = true
                        })
                    }
                    if (option != timeOptions.last()) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- LOKASI KECAMATAN ---
            Text("Pencarian Area (Kecamatan/Desa)", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            OutlinedTextField(
                value = viewModel.selectedKecamatan,
                onValueChange = { viewModel.selectedKecamatan = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Ketik nama wilayah...") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
            )

            Spacer(Modifier.height(16.dp))

            // --- JENIS HAMA ---
            Text("Jenis Hama / Penyakit", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { showHamaSheet = true },
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.Gray),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(viewModel.selectedHama, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.startDateMillis = dateRangePickerState.selectedStartDateMillis
                    viewModel.endDateMillis = dateRangePickerState.selectedEndDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DateRangePicker(state = dateRangePickerState, modifier = Modifier.height(450.dp))
        }
    }

    if (showHamaSheet) {
        ModalBottomSheet(onDismissRequest = { showHamaSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text("Pilih Jenis Hama", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                labelsHama.forEach { hama ->
                    ListItem(
                        headlineContent = { Text(hama) },
                        modifier = Modifier.clickable { viewModel.selectedHama = hama; showHamaSheet = false }
                    )
                }
            }
        }
    }
}