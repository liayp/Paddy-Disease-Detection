package amalia.skripsi.deteksipadi.data
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val email: String?,
    val full_name: String?,
    val avatar_url: String?,
    val role: String, // 'petani' atau 'popt'
    val wkpp_kecamatan: List<String>? = null // Null jika petani
)