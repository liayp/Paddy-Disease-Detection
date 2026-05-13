package amalia.skripsi.deteksipadi.ui.screens.general.profile

import amalia.skripsi.deteksipadi.data.AuthRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val userProfile = profileViewModel.userProfile.value
    val isLoading = profileViewModel.isLoading.value

    LaunchedEffect(Unit) {
        profileViewModel.loadUserProfile(authRepo)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // HEADER SECTION
            Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                            ),
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (!userProfile?.avatar_url.isNullOrEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(userProfile?.avatar_url).crossfade(true).build(),
                            contentDescription = "Foto Profil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = userProfile?.full_name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // TOMBOL EDIT FLOATING
                SmallFloatingActionButton(
                    onClick = { navController.navigate("edit_profile") },
                    modifier = Modifier.align(Alignment.BottomCenter).offset(x = 50.dp, y = 0.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // IDENTITAS USER
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Text(
                        text = userProfile?.full_name ?: "Pengguna Baru",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = if (userProfile?.role == "popt") Color(0xFFE3F2FD) else Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (userProfile?.role == "popt") Icons.Default.VerifiedUser else Icons.Default.Agriculture,
                                contentDescription = null,
                                tint = if (userProfile?.role == "popt") Color(0xFF1565C0) else Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (userProfile?.role == "popt") "PETUGAS POPT" else "MITRA PETANI",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (userProfile?.role == "popt") Color(0xFF1565C0) else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            // DETAIL INFORMASI
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)) {
                Text(text = "Informasi Akun", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))

                ProfileInfoCard(Icons.Default.Email, "Email Terdaftar", userProfile?.email ?: "-", Color(0xFFFB8C00))
                Spacer(modifier = Modifier.height(16.dp))
                ProfileInfoCard(Icons.Default.Phone, "Nomor WhatsApp", userProfile?.phone_number ?: "Belum Diatur", Color(0xFF388E3C))
                Spacer(modifier = Modifier.height(16.dp))
                ProfileInfoCard(Icons.Default.Home, "Alamat Domisili", userProfile?.alamat ?: "Belum Diatur", Color(0xFF1976D2))

                if (userProfile?.role == "popt") {
                    Spacer(modifier = Modifier.height(16.dp))
                    ProfileInfoCard(Icons.Default.Badge, "NIP Pegawai", userProfile.nip ?: "-", Color(0xFFD32F2F))
                    Spacer(modifier = Modifier.height(16.dp))
                    ProfileInfoCard(Icons.Default.LocationOn, "Wilayah Binaan (WKPP)", if (!userProfile.wkpp_kecamatan.isNullOrEmpty()) userProfile.wkpp_kecamatan.joinToString(", ") else "Belum Ditentukan", Color(0xFFD32F2F))
                }

                Spacer(modifier = Modifier.height(40.dp))

                // BUTTONS
                OutlinedButton(
                    onClick = { navController.navigate("change_password") },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.Lock, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Ganti Kata Sandi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.Outlined.ExitToApp, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Keluar dari Aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(32.dp))
                Text(text = "Sistem Peringatan Dini v2.0.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun ProfileInfoCard(icon: ImageVector, label: String, value: String, iconColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).background(iconColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}