package amalia.skripsi.deteksipadi.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.core.content.edit
import amalia.skripsi.deteksipadi.util.NotificationHelper
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class AuthRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val webClientId = "212921453036-bt21jje8evthgbo89tlgsani8a6srl92.apps.googleusercontent.com"
    private val prefs = context.getSharedPreferences("auth_ui_prefs", Context.MODE_PRIVATE)
    private val profilePrefs = context.getSharedPreferences("verified_profile_cache", Context.MODE_PRIVATE)
    private val profileJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun saveCachedProfile(profile: UserProfile) {
        runCatching { profilePrefs.edit { putString("profile_${profile.id}", profileJson.encodeToString(profile)) } }
    }

    fun getCachedProfile(userId: String): UserProfile? = runCatching {
        profilePrefs.getString("profile_$userId", null)?.let { profileJson.decodeFromString<UserProfile>(it) }
    }.getOrNull()

    suspend fun isUserLoggedIn(): Boolean {
        return try {
            supabase.auth.awaitInitialization()
            supabase.auth.currentSessionOrNull() != null
        } catch (e: Exception) {
            Log.w("AuthRepo", "Inisialisasi sesi belum dapat dipastikan: ${e.message}")
            supabase.auth.currentSessionOrNull() != null
        }
    }

    suspend fun syncFcmToken() {
        var retryCount = 0
        while (retryCount < 3) {
            try {
                val user = supabase.auth.currentUserOrNull() ?: return
                if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
                val token = FirebaseMessaging.getInstance().token.await()
                if (token.isNotEmpty()) {
                    supabase.from("profiles").update(buildJsonObject { put("fcm_token", token) }) {
                        filter { eq("id", user.id) }
                    }
                }
                val pref = supabase.from("profiles")
                    .select(columns = Columns.raw("push_notifications_enabled")) { filter { eq("id", user.id) } }
                    .decodeList<NotificationPreferenceDto>()
                    .firstOrNull()
                pref?.push_notifications_enabled?.let { NotificationHelper.setNotificationsEnabled(context, it) }
                return
            } catch (e: Exception) {
                retryCount++
                if (retryCount < 3) delay(3000)
            }
        }
    }

    suspend fun getUserProfile(): UserProfile? {
        val user = supabase.auth.currentUserOrNull() ?: return null
        repeat(3) { attempt ->
            try {
                val profile = supabase.from("profiles")
                    .select(columns = Columns.raw("id,email,full_name,avatar_url,role,nip,phone_number,alamat,is_active,created_at,fcm_token,push_notifications_enabled,poktan_id")) {
                        filter { eq("id", user.id) }
                    }.decodeList<ProfileDto>().firstOrNull()
                if (profile == null) return null

                if (profile != null) {
                    var wkppList: List<String>? = null
                    if (profile.role == "popt") {
                        wkppList = try {
                            supabase.from("popt_wilayah")
                                .select(columns = Columns.raw("kecamatan_id, kecamatan(nama_kecamatan)")) {
                                    filter { eq("popt_id", user.id) }
                                }.decodeList<PoptWilayahDto>()
                                .mapNotNull { it.kecamatan?.nama_kecamatan }
                        } catch (e: Exception) {
                            Log.e("AuthRepo", "Gagal mengambil WKPP: ${e.message}")
                            emptyList()
                        }
                    }

                    val cached = getCachedProfile(user.id)
                    val result = UserProfile(
                        id = profile.id,
                        email = profile.email ?: user.email,
                        full_name = profile.full_name,
                        avatar_url = profile.avatar_url,
                        role = profile.role,
                        nip = profile.nip,
                        phone_number = profile.phone_number,
                        alamat = profile.alamat,
                        is_active = profile.is_active,
                        created_at = profile.created_at,
                        fcm_token = profile.fcm_token,
                        push_notifications_enabled = profile.push_notifications_enabled,
                        poktan_id = profile.poktan_id,
                        master_poktan = profile.master_poktan,
                        wkpp_kecamatan = if (profile.role == "popt" && wkppList.isNullOrEmpty()) cached?.wkpp_kecamatan else wkppList
                    )
                    saveCachedProfile(result)
                    return result
                }
                // Profil tidak ditemukan pada respons server yang berhasil; cache tidak dipakai untuk menutupi data yang hilang.
            } catch (e: Exception) {
                Log.e("AuthRepo", "Gagal membaca profile, percobaan ${attempt + 1}: ${e.message}")
            }
            if (attempt < 2) delay(700)
        }
        return getCachedProfile(user.id)
    }

    fun isProfileComplete(profile: UserProfile): Boolean {
        if (!profile.is_active) return false
        if (profile.full_name.isNullOrBlank() || profile.phone_number.isNullOrBlank() || profile.alamat.isNullOrBlank()) return false
        return when (profile.role) {
            "petani" -> !profile.poktan_id.isNullOrBlank()
            "popt" -> !profile.wkpp_kecamatan.isNullOrEmpty()
            else -> true
        }
    }

    suspend fun loginEmail(email: String, pass: String): Result<String> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = pass
            }

            val profile = getUserProfile()
            when {
                profile == null -> {
                    Result.failure(Exception("Login berhasil, tetapi profil belum dapat dimuat. Periksa koneksi internet lalu coba lagi."))
                }
                !profile.is_active -> {
                    supabase.auth.signOut()
                    Result.failure(Exception("Akun Anda sedang tidak aktif. Silakan hubungi administrator."))
                }
                else -> {
                    syncFcmToken()
                    Result.success("Login berhasil")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(): Result<String> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.failure(Exception("Gagal mendapatkan kredensial Google"))
            }

            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
            supabase.auth.signInWith(IDToken) {
                idToken = googleIdToken.idToken
                provider = Google
            }

            val profile = getUserProfile()
            when {
                profile == null -> {
                    Result.failure(Exception("Login Google berhasil, tetapi profil belum dapat dimuat. Periksa koneksi internet lalu coba lagi."))
                }
                !profile.is_active -> {
                    supabase.auth.signOut()
                    Result.failure(Exception("Akun Anda sedang tidak aktif."))
                }
                else -> {
                    syncFcmToken()
                    Result.success("Login Google berhasil")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun getPushNotificationsEnabled(): Boolean {
        return getUserProfile()?.push_notifications_enabled ?: true
    }

    suspend fun setPushNotificationsEnabled(enabled: Boolean): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull() ?: return Result.failure(Exception("Sesi pengguna tidak ditemukan."))
            supabase.from("profiles").update(buildJsonObject {
                put("push_notifications_enabled", enabled)
            }) { filter { eq("id", user.id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(fullName: String, phoneNumber: String, alamat: String): Boolean {
        return try {
            val user = supabase.auth.currentUserOrNull() ?: return false
            supabase.from("profiles").update(buildJsonObject {
                put("full_name", fullName.trim())
                put("phone_number", phoneNumber.trim())
                put("alamat", alamat.trim())
            }) { filter { eq("id", user.id) } }
            true
        } catch (e: Exception) {
            Log.e("AuthRepo", "Update profile gagal: ${e.message}")
            false
        }
    }

    suspend fun updatePassword(newPass: String): Boolean {
        return try {
            supabase.auth.updateUser { password = newPass }
            true
        } catch (e: Exception) {
            Log.e("AuthRepo", "Update password gagal: ${e.message}")
            false
        }
    }

    fun shouldShowPoptPasswordReminder(userId: String): Boolean {
        return !prefs.getBoolean("popt_password_reminder_$userId", false)
    }

    fun markPoptPasswordReminderShown(userId: String) {
        prefs.edit { putBoolean("popt_password_reminder_$userId", true) }
    }

    suspend fun logout() {
        val user = supabase.auth.currentUserOrNull()

        // Kegagalan network/DB tidak boleh mencegah signOut lokal.
        if (user != null) {
            runCatching {
                supabase.from("profiles").update(
                    buildJsonObject { put("fcm_token", null as String?) }
                ) {
                    filter { eq("id", user.id) }
                }
            }.onFailure { e ->
                Log.w("AuthRepo", "Gagal menghapus FCM token saat logout: ${e.message}")
            }
        }

        try {
            supabase.auth.signOut()
        } catch (e: Exception) {
            Log.e("AuthRepo", "Logout/signOut error: ${e.message}")
        }
    }
} 