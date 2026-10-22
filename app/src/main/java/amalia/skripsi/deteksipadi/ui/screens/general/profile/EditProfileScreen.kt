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
    profileViewModel: ProfileViewModel,
    isProfileCompletion: Boolean = false,
    onProfileCompleted: (() -> Unit)? = null
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
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var poktanSearchQuery by remember { mutableStateOf("") }
    var selectedPoktanId by remember { mutableStateOf<String?>(null) }
    var selectedPoktanName by remember { mutableStateOf("") }
    var showPoktanDialog by remember { mutableStateOf(false) }
    var masterPoktanList by remember { mutableStateOf<List<MasterPoktanDto>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) selectedImageUri = uri
    }

    LaunchedEffect(Unit) {
        profileViewModel.loadUserProfile(authRepo)
        masterPoktanList = fetchMasterPoktan()
    }

    LaunchedEffect(userProfile) {
        userProfile?.let {
            nameField = it.full_name ?: ""
            phoneField = it.phone_number ?: ""
            addressField = it.alamat ?: ""
            selectedPoktanId = it.poktan_id
            selectedPoktanName = it.master_poktan?.nama_poktan ?: if (it.poktan_id != null) "Telah Dipilih" else ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isProfileCompletion) "Lengkapi Profil" else "Perbarui Profil", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (!isProfileCompletion) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (userProfile == null && isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (userProfile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Data profil tidak dapat dimuat.")
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                if (isProfileCompletion) {
                    Text(
                        "Lengkapi data berikut terlebih dahulu agar sistem dapat menggunakan akun Anda dengan benar.",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val imageToDisplay = selectedImageUri ?: userProfile?.avatar_url
                            if (imageToDisplay != null && imageToDisplay.toString().isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(imageToDisplay).crossfade(true).build(),
                                    contentDescription = "Foto Profil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    userProfile?.full_name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp).clickable {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            shadowElevation = 2.dp
                        ) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.padding(8.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Ketuk ikon kamera untuk mengubah foto", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text("INFORMASI IDENTITAS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    ReadOnlyField("Email Terdaftar", userProfile?.email ?: "-", Icons.Default.Email)

                    if (userProfile?.role == "popt") {
                        Spacer(Modifier.height(12.dp))
                        ReadOnlyField("Nomor Induk Pegawai (NIP)", userProfile?.nip ?: "Data belum tersedia", Icons.Default.Badge)
                        Spacer(Modifier.height(12.dp))
                        ReadOnlyField(
                            "Wilayah Kerja (WKPP)",
                            if (!userProfile?.wkpp_kecamatan.isNullOrEmpty()) userProfile?.wkpp_kecamatan?.joinToString(", ") ?: "" else "Belum ditentukan",
                            Icons.Default.LocationOn
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text("DATA PERSONAL", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    CustomInputField(nameField, { nameField = it }, "Nama Lengkap", "Masukkan nama sesuai KTP", Icons.Default.Person)
                    Spacer(Modifier.height(16.dp))
                    CustomInputField(phoneField, { phoneField = it }, "Nomor WhatsApp", "Contoh: 081234567890", Icons.Default.Phone)
                    Spacer(Modifier.height(16.dp))
                    CustomInputField(addressField, { addressField = it }, "Alamat Domisili", "Jalan, Desa, Kecamatan", Icons.Default.Home, false)

                    if (userProfile?.role == "petani") {
                        Spacer(Modifier.height(16.dp))
                        Text("Kelompok Tani (Poktan)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { showPoktanDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    if (selectedPoktanId == null) {
                                        Text("Ketuk untuk memilih Kelompok Tani", color = Color.Gray)
                                    } else {
                                        Text("Poktan Terpilih", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        Text(selectedPoktanName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                            }
                        }
                    }

                    Spacer(Modifier.height(36.dp))

                    Button(
                        onClick = {
                            if (nameField.isBlank() || phoneField.isBlank() || addressField.isBlank()) {
                                Toast.makeText(context, "Nama, Nomor WA, dan alamat wajib diisi", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (userProfile?.role == "petani" && selectedPoktanId == null) {
                                Toast.makeText(context, "Kelompok Tani wajib dipilih", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (userProfile?.role == "popt" && userProfile?.wkpp_kecamatan.isNullOrEmpty()) {
                                Toast.makeText(context, "WKPP belum ditentukan administrator", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            scope.launch {
                                isUploadingImage = true
                                var finalAvatarUrl = userProfile?.avatar_url

                                if (selectedImageUri != null) {
                                    try {
                                        val bytes = context.contentResolver.openInputStream(selectedImageUri!!)?.readBytes()
                                        if (bytes != null) {
                                            val fileName = "avatar_${userProfile!!.id}_${System.currentTimeMillis()}.jpg"
                                            val bucket = supabase.storage.from("avatars")
                                            bucket.upload(fileName, bytes)
                                            finalAvatarUrl = bucket.publicUrl(fileName)
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal mengunggah foto profil", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                profileViewModel.updateProfile(authRepo, nameField, phoneField, addressField) { success, msg ->
                                    if (!success) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }

                                try {
                                    supabase.from("profiles").update({
                                        if (userProfile?.role == "petani") set("poktan_id", selectedPoktanId)
                                        if (finalAvatarUrl != null) set("avatar_url", finalAvatarUrl)
                                    }) { filter { eq("id", userProfile!!.id) } }

                                    isUploadingImage = false
                                    Toast.makeText(context, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()

                                    if (isProfileCompletion) {
                                        onProfileCompleted?.invoke()
                                    } else {
                                        navController.popBackStack()
                                    }
                                } catch (e: Exception) {
                                    isUploadingImage = false
                                    Toast.makeText(context, "Gagal menyimpan data tambahan profil", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading && !isUploadingImage
                    ) {
                        if (isLoading || isUploadingImage) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                        else Text(if (isProfileCompletion) "Selesaikan Profil" else "Simpan Perubahan", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    if (showPoktanDialog) {
        Dialog(
            onDismissRequest = { showPoktanDialog = false; poktanSearchQuery = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f), RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Pilih Kelompok Tani", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = poktanSearchQuery,
                        onValueChange = { poktanSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("Cari nama poktan atau desa...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))

                    val filteredPoktanList = masterPoktanList.filter {
                        it.nama_poktan.contains(poktanSearchQuery, true) ||
                                it.desa.contains(poktanSearchQuery, true) ||
                                it.kecamatan.contains(poktanSearchQuery, true)
                    }

                    LazyColumn(Modifier.weight(1f)) {
                        items(filteredPoktanList) { poktan ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    selectedPoktanId = poktan.id
                                    selectedPoktanName = poktan.nama_poktan
                                    poktanSearchQuery = ""
                                    showPoktanDialog = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedPoktanId == poktan.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(poktan.nama_poktan, fontWeight = FontWeight.Bold)
                                    Text("${poktan.desa}, Kec. ${poktan.kecamatan}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showPoktanDialog = false; poktanSearchQuery = "" },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("Batal") }
                }
            }
        }
    }
}

@Composable
fun CustomInputField(
    value: String, onValueChange: (String) -> Unit, label: String, placeholder: String,
    icon: ImageVector, isSingleLine: Boolean = true
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), placeholder = { Text(placeholder) },
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = isSingleLine, minLines = if (isSingleLine) 1 else 3
        )
    }
}

@Composable
fun ReadOnlyField(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}