package amalia.skripsi.deteksipadi.ui.screens.general.login

import amalia.skripsi.deteksipadi.R
import amalia.skripsi.deteksipadi.data.AuthRepository
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository(context) }

    // --- STATE DATA ---
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }

    // --- STATE UI ---
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Masuk, 1=Daftar
    var selectedRole by remember { mutableStateOf("petani") }

    // --- STATE ERROR (VALIDASI) ---
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

    // Fungsi Validasi Lokal
    fun validateInputs(): Boolean {
        var isValid = true
        emailError = null
        passwordError = null
        nameError = null
        generalError = null

        if (email.isBlank()) {
            emailError = "Email wajib diisi"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Format email tidak valid"
            isValid = false
        }

        if (password.isBlank()) {
            passwordError = "Password wajib diisi"
            isValid = false
        } else if (password.length < 6) {
            passwordError = "Password minimal 6 karakter"
            isValid = false
        }

        if (selectedTab == 1 && fullName.isBlank()) {
            nameError = "Nama lengkap wajib diisi"
            isValid = false
        }

        return isValid
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()), // Agar bisa discroll di layar kecil
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Judul
        Text(
            text = if (selectedTab == 0) "Selamat Datang" else "Buat Akun Baru",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Aplikasi Deteksi Hama Padi",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tab Switcher
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = {
                selectedTab = 0
                generalError = null // Reset error saat ganti tab
            }, text = { Text("Masuk") })
            Tab(selected = selectedTab == 1, onClick = {
                selectedTab = 1
                generalError = null
            }, text = { Text("Daftar") })
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error Umum (Misal: Login Gagal)
        if (generalError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = generalError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // --- FORM INPUT ---
        if (selectedTab == 1) {
            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    nameError = null
                },
                label = { Text("Nama Lengkap") },
                isError = nameError != null,
                supportingText = { if (nameError != null) Text(nameError!!) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Pilihan Role
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = selectedRole == "petani", onClick = { selectedRole = "petani" })
                Text("Petani")
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = selectedRole == "popt", onClick = { selectedRole = "popt" })
                Text("POPT")
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
            },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = emailError != null,
            supportingText = { if (emailError != null) Text(emailError!!) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
            },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = { if (passwordError != null) Text(passwordError!!) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- TOMBOL AKSI ---
        Button(
            onClick = {
                if (validateInputs()) {
                    scope.launch {
                        isLoading = true
                        generalError = null

                        if (selectedTab == 0) {
                            // LOGIN
                            authRepo.loginEmail(email, password)
                                .onSuccess {
                                    Toast.makeText(context, "Login Berhasil!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                }
                                .onFailure { e ->
                                    // Handle Error Message Supabase yang kadang aneh
                                    val msg = e.message?.lowercase() ?: ""
                                    generalError = when {
                                        msg.contains("invalid login") -> "Email atau password salah."
                                        msg.contains("email not confirmed") -> "Akun sudah dibuat tapi belum aktif. Silakan cek Inbox/Spam email Anda untuk verifikasi."
                                        else -> "Login Gagal: ${e.message}"
                                    }
                                }
                        } else {
                            // REGISTER
                            authRepo.registerEmail(email, password, fullName, selectedRole)
                                .onSuccess {
                                    Toast.makeText(context, "Registrasi Sukses! Silakan Login.", Toast.LENGTH_LONG).show()
                                    selectedTab = 0 // Pindah ke tab Login
                                }
                                .onFailure { e ->
                                    val msg = e.message?.lowercase() ?: ""
                                    if (msg.contains("already registered") || msg.contains("user already exists")) {
                                        emailError = "Email ini sudah terdaftar. Silakan login."
                                    } else {
                                        generalError = "Registrasi Gagal: ${e.message}"
                                    }
                                }
                        }
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(if (selectedTab == 0) "Masuk" else "Daftar Akun")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("atau", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        // Tombol Google
        OutlinedButton(
            onClick = {
                scope.launch {
                    isLoading = true
                    authRepo.signInWithGoogle()
                        .onSuccess { onLoginSuccess() }
                        .onFailure { e ->
                            generalError = "Google Login Gagal: ${e.message}"
                        }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Masuk dengan Google")
        }
    }
}