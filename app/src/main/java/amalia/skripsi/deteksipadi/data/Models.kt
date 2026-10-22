@file:Suppress("DEPRECATION")
package amalia.skripsi.deteksipadi.data

import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.contentOrNull

@Serializable
data class LaporanDto(
    val id: String,
    val petani_id: String,
    val foto_url: String = "",
    val label_ai: String? = null,
    val label_koreksi_popt: String? = null,
    val confidence: Float? = null,
    val status: String = "menunggu_verifikasi",
    val prioritas: String? = null,
    val termasuk_cluster: Boolean = false,
    val alamat_lengkap: String? = null,
    val created_at: String = "",

    @SerialName("lokasi")
    val lokasi: JsonElement? = null,

    val deskripsi_gejala: String = "",
    val instruksi_popt: String? = null,
    val radius: Double = 0.3,
    val jenis_pelaporan: String = "ai",
    val kecamatan_id: String? = null,
    val alasan_penolakan: String? = null,
    val alasan_kunjungan: String? = null,

    @SerialName("lat")
    private val latitudeFromMap: Double? = null,

    @SerialName("lon")
    private val longitudeFromMap: Double? = null
) {
    val label_efektif: String?
        get() = label_koreksi_popt?.takeIf { it.isNotBlank() } ?: label_ai

    val lat: Double
        get() = latitudeFromMap
            ?: parsePointLocation(lokasi)?.first
            ?: 0.0

    val lon: Double
        get() = longitudeFromMap
            ?: parsePointLocation(lokasi)?.second
            ?: 0.0
}


@Serializable
data class LaporanPetaDto(
    val id: String,
    val petani_id: String? = null,
    val foto_url: String = "",
    val label_ai: String? = null,
    val label_koreksi_popt: String? = null,
    val confidence: Float? = null,
    val status: String = "menunggu_verifikasi",
    val prioritas: String? = null,
    val termasuk_cluster: Boolean = false,
    val created_at: String = "",
    val deskripsi_gejala: String = "",
    val instruksi_popt: String? = null,
    val kecamatan_id: String? = null,
    val alasan_penolakan: String? = null,
    val alasan_kunjungan: String? = null,
    val radius: Double = 0.3,
    val jenis_pelaporan: String = "ai",
    val lat: Double,
    val lon: Double
) {
    val label_efektif: String?
        get() = label_koreksi_popt?.takeIf { it.isNotBlank() } ?: label_ai
}


private fun parsePointLocation(value: JsonElement?): Pair<Double, Double>? {
    if (value == null) return null
    return try {
        when (value) {
            is kotlinx.serialization.json.JsonObject -> {
                val coords = value["coordinates"]?.jsonArray ?: return null
                if (coords.size >= 2) coords[1].jsonPrimitive.double to coords[0].jsonPrimitive.double else null
            }
            is kotlinx.serialization.json.JsonPrimitive -> {
                val raw = value.contentOrNull ?: return null
                val match = Regex("POINT\\s*\\(\\s*(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s*\\)", RegexOption.IGNORE_CASE).find(raw)
                    ?: Regex("(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)").find(raw)
                if (match != null) match.groupValues[2].toDouble() to match.groupValues[1].toDouble() else null
            }
            else -> null
        }
    } catch (_: Exception) { null }
}


@Serializable
data class NotificationItem(
    val id: String,
    val user_id: String,
    val laporan_id: String? = null,
    val jenis: String,
    val judul: String,
    val pesan: String,
    val sudah_dibaca: Boolean,
    val created_at: String,
    val foto_url_hama: String? = null,
    val laporan: LaporanDto? = null
)


@Serializable
data class LaporanUpdateDto(
    val id: String? = null,
    val laporan_id: String,
    val foto_update_url: String,
    val label_ai_update: String? = null,
    val confidence_update: Float? = null,
    val catatan: String? = null,
    val created_at: String = "",
    val user_id: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gps_accuracy_m: Float? = null,
    val captured_at: String? = null
)


@Serializable
data class LaporanUpdateInsertDto(
    val laporan_id: String,
    val foto_update_url: String,
    val label_ai_update: String,
    val confidence_update: Float,
    val catatan: String,
    val user_id: String,
    val latitude: Double,
    val longitude: Double,
    val gps_accuracy_m: Float,
    val captured_at: String
)


@Serializable
data class LaporanInsertDto(
    val petani_id: String,
    val foto_url: String,
    val label_ai: String?,
    val confidence: Float?,
    val lokasi: String,
    val alamat_lengkap: String,
    val kecamatan_id: String?,
    val deskripsi_gejala: String,
    val jenis_pelaporan: String,
    val prioritas: String,
    val status: String = "menunggu_verifikasi"
)


@Serializable
data class KecamatanDto(
    val id: String,
    val nama_kecamatan: String,
    @SerialName("polygon_geojson") val polygon_geojson: String? = null
)


@Serializable
data class InfoHamaDto(
    val id: String? = null,
    val kategori: String,
    val isi_informasi: String,
    val urutan: Int
)


@Serializable
data class MasterHamaDto(
    val id: String,
    val nama_hama: String,
    val deskripsi: String,
    val ciri_ciri: String,
    val informasi_hama: List<InfoHamaDto> = emptyList()
)


@Serializable
data class MasterPoktanDto(
    val id: String,
    val nama_poktan: String,
    val kecamatan: String,
    val desa: String
)


@Serializable
data class KecamatanSimpleDto(
    val nama_kecamatan: String
)


@Serializable
data class CoordinateParams(
    val p_lat: Double,
    val p_lon: Double
)


@Serializable
data class ProfileNameOnly(val full_name: String? = null)


@Serializable
data class ProfileDirectoryDto(
    val id: String,
    val full_name: String? = null
)


@Serializable
data class PoptWilayahSimple(val popt_id: String? = null)


@Serializable
data class PerluKunjunganParams(
    val p_laporan_id: String,
    val p_alasan: String,
    val p_waktu_kunjungan: String,
    val p_catatan_jadwal: String? = null,
    val p_instruksi: String? = null,
    val p_radius: Double? = null,
    val p_corrected_label: String? = null
)


@Serializable
data class LocationValidationDto(
    @SerialName("is_in_sawah") val isInSawah: Boolean,
    @SerialName("kecamatan_id") val kecId: String?,
    @SerialName("nama_kecamatan") val kecName: String?
)


@Serializable
data class LaporanStatusRiwayatDto(
    val id: String,
    val laporan_id: String,
    val status_sebelumnya: String? = null,
    val status_baru: String,
    val jenis_peristiwa: String,
    val catatan: String? = null,
    val instruksi_popt: String? = null,
    val alasan_kunjungan: String? = null,
    val alasan_penolakan: String? = null,
    val label_ai: String? = null,
    val label_koreksi_popt: String? = null,
    val confidence: Double? = null,
    val foto_url: String? = null,
    val deskripsi_gejala: String? = null,
    val alamat_lengkap: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val actor_id: String? = null,
    val created_at: String = ""
)


data class LaporanTimelineEventDto(
    val history: LaporanStatusRiwayatDto,
    val actorName: String?
)


@Serializable
data class JadwalKunjunganDto(
    val id: String = "",
    val laporan_id: String,
    val popt_id: String,
    val waktu_kunjungan: String,
    val catatan: String? = null,
    val status: String = "menunggu_konfirmasi",
    val usulan_waktu: String? = null,
    val alasan_reschedule: String? = null,
    val catatan_hasil: String? = null,
    val evidence_foto_url: String? = null,
    val checkin_latitude: Double? = null,
    val checkin_longitude: Double? = null,
    val checkin_accuracy_m: Double? = null,
    val checkin_at: String? = null,
    val dibuat_pada: String = "",
    val diperbarui_pada: String = ""
)


@Serializable
data class JadwalKunjunganInsertDto(
    val laporan_id: String,
    val popt_id: String,
    val waktu_kunjungan: String,
    val catatan: String? = null,
    val status: String = "menunggu_konfirmasi"
)
