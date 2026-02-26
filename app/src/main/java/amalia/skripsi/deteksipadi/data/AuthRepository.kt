package amalia.skripsi.deteksipadi.data

import android.content.Context
import android.content.SharedPreferences
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
import androidx.core.content.edit

class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val WEB_CLIENT_ID = "212921453036-bt21jje8evthgbo89tlgsani8a6srl92.apps.googleusercontent.com"

    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun saveUserRole(role: String) {
        prefs.edit { putString("saved_role", role) }
    }

    fun getSavedRole(): String {
        return prefs.getString("saved_role", "petani") ?: "petani"
    }

    suspend fun isUserLoggedIn(): Boolean {
        try {
            supabase.auth.awaitInitialization()
        } catch (e: Exception) {
            Log.e("AuthRepo", "Gagal inisialisasi sesi: ${e.message}")
        }
        return supabase.auth.currentSessionOrNull() != null
    }

    suspend fun getUserProfile(): UserProfile? {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return try {
            supabase.postgrest.from("profiles")
                .select(columns = Columns.ALL) {
                    filter { eq("id", userId) }
                }
                .decodeSingle<UserProfile>()
        } catch (e: Exception) {
            Log.e("UserProfileError", "Gagal ambil data (Mungkin Offline): ${e.message}")
            null
        }
    }

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

    suspend fun registerEmail(email: String, pass: String, name: String, role: String): Result<String> {
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
                data = buildJsonObject {
                    put("full_name", name)
                    put("role", role)
                }
            }
            Result.success("Registrasi Berhasil! Cek email untuk verifikasi.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                supabase.auth.signInWith(IDToken) {
                    idToken = googleIdToken.idToken
                    provider = Google
                }
                Result.success("Login Google Berhasil")
            } else {
                Result.failure(Exception("Gagal mendapatkan kredensial Google"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try {
            supabase.auth.signOut()
        } catch (_: Exception) {
            Log.e("AuthRepo", "Logout secara offline: Dihapus dari memori lokal saja.")
        }
        prefs.edit { clear() } // Hapus role lokal
    }
}