package amalia.skripsi.deteksipadi.ui.screens.general.profile

import amalia.skripsi.deteksipadi.data.AuthRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

    // Load Data saat layar dibuka
    LaunchedEffect(Unit) {
        profileViewModel.loadUserProfile(authRepo)
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()) // Agar bisa discroll di HP kecil
        ) {
            // HEADER SECTION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            ),
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                )

                // Foto Profil (Floating di tengah)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (!userProfile?.avatar_url.isNullOrEmpty()) {
                        // Load Foto dari Google URL
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(userProfile.avatar_url)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Foto Profil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Fallback Inisial (Jika tidak ada foto)
                        Text(
                            text = userProfile?.full_name?.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // IDENTITAS USER
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = userProfile?.full_name ?: "Pengguna",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = if (userProfile?.role == "popt") Color(0xFFE3F2FD) else Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(50),
                        border = null
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (userProfile?.role == "popt") Icons.Default.VerifiedUser else Icons.Default.Agriculture,
                                contentDescription = null,
                                tint = if (userProfile?.role == "popt") Color(0xFF1565C0) else Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (userProfile?.role == "popt") "PETUGAS POPT" else "MITRA PETANI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (userProfile?.role == "popt") Color(0xFF1565C0) else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // DETAIL INFORMASI (KARTU)
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {

                Text(
                    text = "Informasi Akun",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Kartu Email
                ProfileInfoCard(
                    icon = Icons.Default.Email,
                    label = "Email Terdaftar",
                    value = userProfile?.email ?: "-",
                    iconColor = Color(0xFFFB8C00) // Orange
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Kartu User ID
                ProfileInfoCard(
                    icon = Icons.Default.Person,
                    label = "ID Pengguna",
                    value = userProfile?.id?.take(8)?.uppercase() ?: "-",
                    iconColor = Color(0xFF8E24AA) // Ungu
                )

                // Khusus POPT: Kartu Wilayah Kerja
                if (userProfile?.role == "popt") {
                    Spacer(modifier = Modifier.height(12.dp))
                    ProfileInfoCard(
                        icon = Icons.Default.LocationOn,
                        label = "Wilayah Binaan (WKPP)",
                        value = userProfile.wkpp_kecamatan?.joinToString(", ") ?: "Belum Ditentukan",
                        iconColor = Color(0xFFD32F2F) // Merah
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // TOMBOL LOGOUT
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Keluar dari Aplikasi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer Version
                Text(
                    text = "Versi Aplikasi 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(80.dp)) // Extra space untuk BottomNav
            }
        }
    }
}

// KOMPONEN KARTU INFORMASI (Reusable)
@Composable
fun ProfileInfoCard(
    icon: ImageVector,
    label: String,
    value: String,
    iconColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon dengan background bulat transparan
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        }
    }
}