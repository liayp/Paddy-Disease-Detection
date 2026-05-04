package amalia.skripsi.deteksipadi.data

import kotlinx.serialization.Serializable

data class UserProfile(
    val id: String,
    val email: String? = null,
    val full_name: String? = null,
    val avatar_url: String? = null,
    val role: String? = null,
    val nip: String? = null,
    val phone_number: String? = null,
    val alamat: String? = null,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val fcm_token: String?,
    val wkpp_kecamatan: List<String>? = null
)

@Serializable
data class ProfileDto(
    val id: String,
    val email: String? = null,
    val full_name: String? = null,
    val avatar_url: String? = null,
    val role: String? = null,
    val nip: String? = null,
    val phone_number: String? = null,
    val alamat: String? = null,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val fcm_token: String? =null
)

@Serializable
data class PoptWilayahDto(
    val kecamatan: KecamatanDto? = null
)