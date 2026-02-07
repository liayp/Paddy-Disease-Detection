package amalia.skripsi.deteksipadi.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Ganti dengan Web Client ID dari Google Cloud Console Anda
    private val WEB_CLIENT_ID = "212921453036-bt21jje8evthgbo89tlgsani8a6srl92.apps.googleusercontent.com"

    // Cek status login
    fun isUserLoggedIn(): Boolean {
        return supabase.auth.currentSessionOrNull() != null
    }

    // Ambil Data Profil User (termasuk Role & WKPP)
    suspend fun getUserProfile(): UserProfile? {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return try {
            // Kita ambil data murni dari tabel 'profiles'
            // Gunakan columns = Columns.ALL agar aman
            supabase.postgrest.from("profiles")
                .select(columns = Columns.ALL) {
                    filter { eq("id", userId) }
                }
                .decodeSingle<UserProfile>()

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("UserProfileError", "Gagal ambil data: ${e.message}")
            null
        }
    }

    // Login Email & Password
    suspend fun loginEmail(email: String, pass: String): Result<String> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }
            Result.success("Login Berhasil")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Register Email (Wajib Valid Email - Supabase otomatis kirim link konfirmasi)
    suspend fun registerEmail(email: String, pass: String, name: String, role: String): Result<String> {
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
                data = buildJsonObject {
                    put("full_name", name)
                    put("role", role) // 'petani' atau 'popt'
                }
            }
            Result.success("Registrasi Berhasil! Cek email untuk verifikasi.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Google Sign-In (Modern Approach)
    suspend fun signInWithGoogle(): Result<String> {
        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)

                // Login ke Supabase pakai Token Google
                supabase.auth.signInWith(IDToken) {
                    idToken = googleIdToken.idToken
                    provider = Google
                    // Google otomatis dianggap verified email
                }
                Result.success("Login Google Berhasil")
            } else {
                Result.failure(Exception("Gagal mendapatkan kredensial Google"))
            }
        } catch (e: GetCredentialException) {
            // TAMBAHKAN LOG INI
            Log.e("GoogleLogin", "Error Credential: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        } catch (e: Exception) {
            // TAMBAHKAN LOG INI
            Log.e("GoogleLogin", "Error Umum: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun logout() {
        supabase.auth.signOut()
    }
}