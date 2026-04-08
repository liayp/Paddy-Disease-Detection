package amalia.skripsi.deteksipadi.ui.screens.general.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import amalia.skripsi.deteksipadi.data.AuthRepository
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }

    val userProfile by profileViewModel.userProfile
    val isLoading by profileViewModel.isLoading

    var nameField by remember { mutableStateOf("") }
    var phoneField by remember { mutableStateOf("") }
    var addressField by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        profileViewModel.loadUserProfile(authRepo)
    }

    LaunchedEffect(userProfile) {
        userProfile?.let {
            nameField = it.full_name ?: ""
            phoneField = it.phone_number ?: ""
            addressField = it.alamat ?: ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Perbarui Profil",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (userProfile == null && isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // --- HEADER: FOTO PROFIL ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!userProfile?.avatar_url.isNullOrEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(userProfile?.avatar_url)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Foto Profil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = userProfile?.full_name?.firstOrNull()?.toString() ?: "?",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { /* Logic Picker Image */ },
                            shadowElevation = 2.dp
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                null,
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Ketuk ikon kamera untuk mengubah foto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                // --- SECTION 1: INFORMASI AKUN (READ ONLY) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        "INFORMASI IDENTITAS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ReadOnlyField(
                        label = "Email Terdaftar",
                        value = userProfile?.email ?: "-",
                        icon = Icons.Default.Email
                    )

                    if (userProfile?.role == "popt") {
                        Spacer(modifier = Modifier.height(12.dp))
                        ReadOnlyField(
                            label = "Nomor Induk Pegawai (NIP)",
                            value = userProfile?.nip ?: "Data belum tersedia",
                            icon = Icons.Default.Badge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ReadOnlyField(
                            label = "Wilayah Kerja (WKPP)",
                            value = if (!userProfile?.wkpp_kecamatan.isNullOrEmpty())
                                userProfile?.wkpp_kecamatan?.joinToString(", ") ?: ""
                            else "Belum ditentukan oleh admin",
                            icon = Icons.Default.LocationOn
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- SECTION 2: FORM EDITABLE ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        "DATA PERSONAL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    CustomInputField(
                        value = nameField,
                        onValueChange = { nameField = it },
                        label = "Nama Lengkap",
                        placeholder = "Masukkan nama lengkap Anda sesuai KTP",
                        icon = Icons.Default.Person
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    CustomInputField(
                        value = phoneField,
                        onValueChange = { phoneField = it },
                        label = "Nomor WhatsApp",
                        placeholder = "Contoh: 081234567890",
                        icon = Icons.Default.Phone
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    CustomInputField(
                        value = addressField,
                        onValueChange = { addressField = it },
                        label = "Alamat Domisili saat ini",
                        placeholder = "Masukkan detail alamat (Jalan, No. Rumah, Desa/Kecamatan)",
                        icon = Icons.Default.Home,
                        isSingleLine = false
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = {
                            if (nameField.isBlank() || phoneField.isBlank()) {
                                Toast.makeText(context, "Nama dan Nomor WA wajib diisi", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            profileViewModel.updateProfile(authRepo, nameField, phoneField, addressField) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) navController.popBackStack()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Simpan Perubahan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Pastikan data yang Anda masukkan sudah benar untuk mempermudah koordinasi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun CustomInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    isSingleLine: Boolean = true
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    style = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                )
            },
            leadingIcon = {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f), // Outline lebih tegas
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            singleLine = isSingleLine,
            minLines = if (isSingleLine) 1 else 3
        )
    }
}

@Composable
fun ReadOnlyField(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                label,
                style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), letterSpacing = 0.5.sp)
            )
            Text(
                value,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
            modifier = Modifier.size(16.dp)
        )
    }
}