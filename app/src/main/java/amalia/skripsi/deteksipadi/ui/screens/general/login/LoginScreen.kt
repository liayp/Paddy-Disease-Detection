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
import androidx.compose.material.icons.filled.Person
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

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Masuk, 1=Daftar

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

    fun validateInputs(): Boolean {
        var isValid = true
        emailError = null
        passwordError = null
        nameError = null

        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Email tidak valid"
            isValid = false
        }
        if (password.length < 6) {
            passwordError = "Password minimal 6 karakter"
            isValid = false
        }
        if (selectedTab == 1 && fullName.isBlank()) {
            nameError = "Nama wajib diisi"
            isValid = false
        }
        return isValid
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (selectedTab == 0) "Selamat Datang" else "Daftar Petani",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Masuk") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Daftar") })
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (generalError != null) {
            Text(generalError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (selectedTab == 1) {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it; nameError = null },
                label = { Text("Nama Lengkap") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                isError = nameError != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = emailError != null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = passwordError != null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (validateInputs()) {
                    scope.launch {
                        isLoading = true
                        if (selectedTab == 0) {
                            authRepo.loginEmail(email, password)
                                .onSuccess { onLoginSuccess() }
                                .onFailure { generalError = "Email/Password salah atau belum terdaftar" }
                        } else {
                            authRepo.registerEmail(email, password, fullName, "petani")
                                .onSuccess {
                                    Toast.makeText(context, "Berhasil! Silakan masuk.", Toast.LENGTH_SHORT).show()
                                    selectedTab = 0
                                }
                                .onFailure { generalError = it.localizedMessage }
                        }
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(if (selectedTab == 0) "Masuk" else "Daftar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    isLoading = true
                    authRepo.signInWithGoogle()
                        .onSuccess { onLoginSuccess() }
                        .onFailure { generalError = "Google Login Gagal" }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(painterResource(R.drawable.ic_google_logo), null, modifier = Modifier.size(24.dp), tint = Color.Unspecified)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Masuk dengan Google")
        }
    }
}