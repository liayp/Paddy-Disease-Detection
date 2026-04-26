package amalia.skripsi.deteksipadi.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

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

    suspend fun syncFcmToken() {
        var retryCount = 0
        val maxRetries = 3

        while (true) {
            try {
                val user = supabase.auth.currentUserOrNull() ?: return

                // REVISI FIX: Cek secara eksplisit apakah FirebaseApp sudah siap
                val apps = FirebaseApp.getApps(context)
                if (apps.isEmpty()) {
                    Log.w("FCM_SYNC", "FirebaseApp belum siap, mencoba initialize manual...")
                    FirebaseApp.initializeApp(context)
                }

                // Ambil token dengan await()
                val token = FirebaseMessaging.getInstance().token.await()

                if (!token.isNullOrEmpty()) {
                    Log.d("FCM_SYNC", "Token didapat: $token")
                    supabase.from("profiles").update(
                        buildJsonObject { put("fcm_token", token) }
                    ) {
                        filter { eq("id", user.id) }
                    }
                    return // Berhasil, keluar dari loop
                }
            } catch (e: Exception) {
                retryCount++
                Log.e("FCM_SYNC_ERROR", "Percobaan $retryCount gagal: ${e.message}")
                if (retryCount >= maxRetries) break
                delay(3000) // Beri waktu lebih lama (3 detik) untuk inisialisasi internal
            }
        }
    }

    suspend fun getUserProfile(): UserProfile? {
        val user = supabase.auth.currentUserOrNull() ?: return null
        val userEmail = user.email

        try {
            delay(500)

            var profileDto = supabase.from("profiles")
                .select()
                .decodeList<ProfileDto>()
                .firstOrNull { it.id == user.id }

            if (profileDto == null && userEmail != null) {
                val profileByEmail = supabase.from("profiles")
                    .select()
                    .decodeList<ProfileDto>()
                    .firstOrNull { it.email?.lowercase() == userEmail.lowercase() }

                if (profileByEmail != null) {
                    try {
                        supabase.from("profiles").update(
                            buildJsonObject { put("id", user.id) }
                        ) {
                            filter { eq("email", userEmail) }
                        }
                        profileDto = profileByEmail.copy(id = user.id)
                    } catch (eUpdate: Exception) {
                        profileDto = profileByEmail
                    }
                }
            }

            if (profileDto == null) {
                val newProfile = ProfileDto(
                    id = user.id,
                    full_name = user.userMetadata?.get("full_name")?.toString() ?: "User Baru",
                    email = userEmail,
                    role = user.userMetadata?.get("role")?.toString() ?: "petani"
                )

                try {
                    supabase.from("profiles").insert(newProfile)
                    profileDto = newProfile
                } catch (eInsert: Exception) {
                    Log.e("AuthRepo", "Gagal auto-insert profile: ${eInsert.message}")
                }
            }

            var wkppList: List<String>? = null
            if (profileDto?.role == "popt") {
                try {
                    val wilayahResponse = supabase.from("popt_wilayah")
                        .select(columns = Columns.raw("kecamatan(nama_kecamatan)")) {
                            filter { eq("popt_id", user.id) }
                        }.decodeList<PoptWilayahDto>()

                    wkppList = wilayahResponse.mapNotNull { it.kecamatan?.nama_kecamatan }
                } catch (eRel: Exception) {
                    Log.e("AuthRepo", "Error query wilayah POPT: ${eRel.message}")
                }
            }

            return UserProfile(
                id = profileDto?.id ?: user.id,
                email = userEmail,
                full_name = profileDto?.full_name,
                avatar_url = profileDto?.avatar_url,
                role = profileDto?.role ?: "petani",
                phone_number = profileDto?.phone_number,
                alamat = profileDto?.alamat,
                nip = profileDto?.nip,
                fcm_token = profileDto?.fcm_token,
                wkpp_kecamatan = wkppList
            )

        } catch (e: Exception) {
            Log.e("UserProfileError", "Crash: ${e.message}")
            return UserProfile(
                id = user.id,
                email = userEmail,
                full_name = "User",
                avatar_url = null,
                role = "petani",
                fcm_token = null,
                wkpp_kecamatan = null
            )
        }
    }

    suspend fun updateProfile(fullName: String, phoneNumber: String, alamat: String): Boolean {
        return try {
            val user = supabase.auth.currentUserOrNull() ?: return false
            supabase.from("profiles").update(
                buildJsonObject {
                    put("full_name", fullName)
                    put("phone_number", phoneNumber)
                    put("alamat", alamat)
                }
            ) {
                filter { eq("id", user.id) }
            }
            true
        } catch (e: Exception) {
            Log.e("AuthRepo", "Update Profile Error: ${e.message}")
            false
        }
    }

    suspend fun updatePassword(newPass: String): Boolean {
        return try {
            supabase.auth.updateUser {
                password = newPass
            }
            true
        } catch (e: Exception) {
            Log.e("AuthRepo", "Update Password Error: ${e.message}")
            false
        }
    }

    suspend fun loginEmail(email: String, pass: String): Result<String> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }
            // REVISI: Ambil token perangkat segera setelah login berhasil
            syncFcmToken()
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
            // REVISI: Sinkronisasi token setelah pendaftaran
            syncFcmToken()
            Result.success("Registrasi Berhasil! Silakan Login.")
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
                // REVISI: Sinkronisasi token setelah masuk via Google
                syncFcmToken()
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
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                // REVISI: Set token ke null di DB saat logout agar HP ini tidak dikirimi notif akun ini lagi
                supabase.from("profiles").update(
                    buildJsonObject { put("fcm_token", null as String?) }
                ) {
                    filter { eq("id", user.id) }
                }
            }
            supabase.auth.signOut()
        } catch (_: Exception) {}
        prefs.edit { clear() }
    }
}