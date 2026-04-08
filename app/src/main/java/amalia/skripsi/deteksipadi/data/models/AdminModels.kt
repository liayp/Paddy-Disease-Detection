package amalia.skripsi.deteksipadi.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Kecamatan(
    val id: String? = null,
    val nama_kecamatan: String,
    val created_at: String? = null
)

@Serializable
data class PegawaiProfile(
    val id: String,
    val full_name: String,
    val role: String, // admin, popt, petani
    val is_active: Boolean,
    val nip: String? = null,
    val phone_number: String? = null,
    val avatar_url: String? = null,
    val wilayah_tugas: List<String> = emptyList() // Nama-nama kecamatan
)

@Serializable
data class AdminStats(
    val total_petani: Int = 0,
    val total_popt: Int = 0,
    val total_laporan: Int = 0,
    val laporan_per_kecamatan: Map<String, Int> = emptyMap()
)