package amalia.skripsi.deteksipadi.ui.screens.general.profile

import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.MasterPoktanDto
import amalia.skripsi.deteksipadi.data.fetchMasterPoktan
import amalia.skripsi.deteksipadi.data.supabase
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val scope = rememberCoroutineScope()

    val userProfile by profileViewModel.userProfile
    val isLoading by profileViewModel.isLoading
    var isUploadingImage by remember { mutableStateOf(false) }

    var nameField by remember { mutableStateOf("") }
    var phoneField by remember { mutableStateOf("") }
    var addressField by remember { mutableStateOf("") }

    // State untuk Image Picker
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // State untuk Custom Searchable Dropdown Poktan
    var poktanSearchQuery by remember { mutableStateOf("") }
    var selectedPoktanId by remember { mutableStateOf<String?>(null) }
    var selectedPoktanName by remember { mutableStateOf("") }
    var showPoktanDialog by remember { mutableStateOf(false) }
    var masterPoktanList by remember { mutableStateOf<List<MasterPoktanDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        profileViewModel.loadUserProfile(authRepo)
        masterPoktanList = fetchMasterPoktan()
    }

    LaunchedEffect(userProfile) {
        userProfile?.let {
            nameField = it.full_name ?: ""
            phoneField = it.phone_number ?: ""
            addressField = it.alamat ?: ""
            it.poktan_id?.let { pId ->
                selectedPoktanId = pId
                selectedPoktanName = it.master_poktan?.nama_poktan ?: "Telah Dipilih"
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Perbarui Profil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                windowInsets = WindowInsets.safeDrawing,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (userProfile == null && isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {

                // --- HEADER: FOTO PROFIL ---
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val imageToDisplay = selectedImageUri ?: userProfile?.avatar_url
                            if (imageToDisplay != null && imageToDisplay.toString().isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(imageToDisplay).crossfade(true).build(),
                                    contentDescription = "Foto Profil", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = userProfile?.full_name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp).clickable {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, shadowElevation = 2.dp
                        ) { Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.padding(8.dp)) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Ketuk ikon kamera untuk mengubah foto", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }

                // --- SECTION 1: INFORMASI AKUN (READ ONLY) ---
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text("INFORMASI IDENTITAS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    ReadOnlyField(label = "Email Terdaftar", value = userProfile?.email ?: "-", icon = Icons.Default.Email)

                    if (userProfile?.role == "popt") {
                        Spacer(modifier = Modifier.height(12.dp))
                        ReadOnlyField(label = "Nomor Induk Pegawai (NIP)", value = userProfile?.nip ?: "Data belum tersedia", icon = Icons.Default.Badge)
                        Spacer(modifier = Modifier.height(12.dp))
                        ReadOnlyField(label = "Wilayah Kerja (WKPP)", value = if (!userProfile?.wkpp_kecamatan.isNullOrEmpty()) userProfile?.wkpp_kecamatan?.joinToString(", ") ?: "" else "Belum ditentukan", icon = Icons.Default.LocationOn)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- SECTION 2: FORM EDITABLE ---
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text("DATA PERSONAL", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    CustomInputField(value = nameField, onValueChange = { nameField = it }, label = "Nama Lengkap", placeholder = "Masukkan nama sesuai KTP", icon = Icons.Default.Person)
                    Spacer(modifier = Modifier.height(16.dp))
                    CustomInputField(value = phoneField, onValueChange = { phoneField = it }, label = "Nomor WhatsApp", placeholder = "Contoh: 081234567890", icon = Icons.Default.Phone)
                    Spacer(modifier = Modifier.height(16.dp))
                    CustomInputField(value = addressField, onValueChange = { addressField = it }, label = "Alamat Domisili", placeholder = "Jalan, Desa, Kecamatan", icon = Icons.Default.Home, isSingleLine = false)

                    // --- CUSTOM SELECTION POKTAN (BEDA DARI TEXTFIELD BIASA) ---
                    if (userProfile?.role == "petani") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Kelompok Tani (Poktan)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp), fontWeight = FontWeight.Bold)

                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { showPoktanDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (selectedPoktanId == null) {
                                        Text("Ketuk untuk memilih Kelompok Tani", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                                    } else {
                                        Text("Poktan Terpilih", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        Text(selectedPoktanName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = {
                            if (nameField.isBlank() || phoneField.isBlank()) {
                                Toast.makeText(context, "Nama dan Nomor WA wajib diisi", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            scope.launch {
                                isUploadingImage = true
                                var finalAvatarUrl = userProfile?.avatar_url

                                if (selectedImageUri != null && userProfile != null) {
                                    try {
                                        val bytes = context.contentResolver.openInputStream(selectedImageUri!!)?.readBytes()
                                        if (bytes != null) {
                                            val fileName = "avatar_${userProfile!!.id}_${System.currentTimeMillis()}.jpg"
                                            val bucket = supabase.storage.from("avatars")
                                            bucket.upload(fileName, bytes)
                                            finalAvatarUrl = bucket.publicUrl(fileName)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Gagal unggah foto", Toast.LENGTH_SHORT).show() }
                                    }
                                }

                                profileViewModel.updateProfile(authRepo, nameField, phoneField, addressField) { success, msg ->
                                    if (!success) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }

                                try {
                                    supabase.from("profiles").update({
                                        if (userProfile?.role == "petani" && selectedPoktanId != null) {
                                            set("poktan_id", selectedPoktanId)
                                        }
                                        if (finalAvatarUrl != null) {
                                            set("avatar_url", finalAvatarUrl)
                                        }
                                    }) { filter { eq("id", userProfile!!.id) } }
                                } catch (e: Exception) {}

                                isUploadingImage = false
                                Toast.makeText(context, "Profil Berhasil Diperbarui", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !isLoading && !isUploadingImage
                    ) {
                        if (isLoading || isUploadingImage) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Simpan Perubahan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // --- DIALOG PENCARIAN POKTAN ELEGAN ---
    if (showPoktanDialog) {
        Dialog(
            onDismissRequest = {
                showPoktanDialog = false
                poktanSearchQuery = ""
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f) // Agak Transparan
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Pilih Kelompok Tani", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, top = 8.dp))

                    OutlinedTextField(
                        value = poktanSearchQuery,
                        onValueChange = { poktanSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("Cari nama poktan atau desa...", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.LightGray
                        ),
                        singleLine = true
                    )

                    Spacer(Modifier.height(16.dp))

                    val filteredPoktanList = masterPoktanList.filter {
                        it.nama_poktan.contains(poktanSearchQuery, ignoreCase = true) ||
                                it.desa.contains(poktanSearchQuery, ignoreCase = true) ||
                                it.kecamatan.contains(poktanSearchQuery, ignoreCase = true)
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (filteredPoktanList.isEmpty()) {
                            item {
                                Text("Kelompok Tani tidak ditemukan", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(16.dp))
                            }
                        } else {
                            items(filteredPoktanList) { poktan ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                        selectedPoktanId = poktan.id
                                        selectedPoktanName = poktan.nama_poktan
                                        poktanSearchQuery = ""
                                        showPoktanDialog = false
                                    },
                                    colors = CardDefaults.cardColors(containerColor = if (selectedPoktanId == poktan.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(poktan.nama_poktan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (selectedPoktanId == poktan.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        Text("${poktan.desa}, Kec. ${poktan.kecamatan}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            showPoktanDialog = false
                            poktanSearchQuery = ""
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) { Text("Batal", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun CustomInputField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, icon: ImageVector, isSingleLine: Boolean = true) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            placeholder = { Text(text = placeholder, style = MaterialTheme.typography.bodyLarge, color = Color.Gray) },
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
            ),
            singleLine = isSingleLine, minLines = if (isSingleLine) 1 else 3, textStyle = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun ReadOnlyField(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), modifier = Modifier.size(20.dp))
    }
}