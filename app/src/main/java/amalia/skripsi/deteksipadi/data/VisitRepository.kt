@file:Suppress("DEPRECATION")
package amalia.skripsi.deteksipadi.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun buatJadwalKunjungan(
    laporanId: String,
    poptId: String,
    waktuKunjungan: String,
    catatan: String?
): Result<Unit> = try {
    supabase.postgrest.rpc("buat_jadwal_kunjungan", buildJsonObject {
        put("p_laporan_id", laporanId)
        put("p_waktu_kunjungan", waktuKunjungan)
        put("p_catatan", catatan?.trim()?.takeIf { it.isNotEmpty() })
    })
    Result.success(Unit)
} catch (e: Exception) { Result.failure(e) }


suspend fun fetchJadwalKunjungan(laporanId: String): List<JadwalKunjunganDto> =
    supabase.from("jadwal_kunjungan").select {
        filter { eq("laporan_id", laporanId) }
        order("waktu_kunjungan", Order.DESCENDING)
    }.decodeList<JadwalKunjunganDto>()


suspend fun updateCatatanKunjungan(jadwalId: String, catatan: String): Result<Unit> = try {
    supabase.postgrest.rpc("update_catatan_kunjungan", buildJsonObject {
        put("p_jadwal_id", jadwalId)
        put("p_catatan", catatan.trim())
    })
    Result.success(Unit)
} catch (e: Exception) {
    Result.failure(e)
}


suspend fun konfirmasiJadwalKunjungan(jadwalId: String): Result<Unit> = try {
    supabase.postgrest.rpc("konfirmasi_jadwal_kunjungan", buildJsonObject { put("p_jadwal_id", jadwalId) })
    Result.success(Unit)
} catch (e: Exception) { Result.failure(e) }


suspend fun usulkanJadwalUlang(jadwalId: String, waktuBaru: String, alasan: String): Result<Unit> = try {
    require(alasan.trim().length >= 5) { "Alasan perubahan jadwal minimal 5 karakter." }
    supabase.postgrest.rpc("usulkan_jadwal_ulang", buildJsonObject {
        put("p_jadwal_id", jadwalId)
        put("p_waktu_baru", waktuBaru)
        put("p_alasan", alasan.trim())
    })
    Result.success(Unit)
} catch (e: Exception) { Result.failure(e) }


suspend fun terimaUsulanJadwalUlang(jadwalId: String): Result<Unit> = try {
    supabase.postgrest.rpc("terima_usulan_jadwal_ulang", buildJsonObject { put("p_jadwal_id", jadwalId) })
    Result.success(Unit)
} catch (e: Exception) { Result.failure(e) }


suspend fun tolakUsulanJadwalUlang(jadwalId: String): Result<Unit> = try {
    supabase.postgrest.rpc("tolak_usulan_jadwal_ulang", buildJsonObject { put("p_jadwal_id", jadwalId) })
    Result.success(Unit)
} catch (e: Exception) { Result.failure(e) }


suspend fun ubahJadwalKunjungan(jadwalId: String, waktuBaru: String, catatan: String?): Result<Unit> = try {
    supabase.postgrest.rpc("ubah_jadwal_kunjungan", buildJsonObject {
        put("p_jadwal_id", jadwalId)
        put("p_waktu_baru", waktuBaru)
        put("p_catatan", catatan?.trim()?.takeIf { it.isNotEmpty() })
    })
    Result.success(Unit)
} catch (e: Exception) { Result.failure(e) }


suspend fun tandaiKunjunganTerlaksana(
    jadwalId: String,
    catatanHasil: String,
    evidenceFotoUrl: String,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double
): Result<Unit> = try {
    require(catatanHasil.trim().length >= 5) { "Catatan hasil kunjungan minimal 5 karakter." }
    require(evidenceFotoUrl.isNotBlank()) { "Foto hasil kunjungan wajib diunggah." }
    supabase.postgrest.rpc("selesaikan_kunjungan", buildJsonObject {
        put("p_jadwal_id", jadwalId)
        put("p_catatan_hasil", catatanHasil.trim())
        put("p_evidence_foto_url", evidenceFotoUrl)
        put("p_latitude", latitude)
        put("p_longitude", longitude)
        put("p_accuracy_m", accuracyMeters)
    })
    Result.success(Unit)
} catch (e: Exception) { Result.failure(e) }


suspend fun uploadVisitEvidence(photoBytes: ByteArray, jadwalId: String): Result<String> = try {
    val fileName = "visit_${jadwalId}_${System.currentTimeMillis()}.jpg"
    val bucket = supabase.storage.from("evidence_photos")
    bucket.upload(fileName, photoBytes)
    Result.success(bucket.publicUrl(fileName))
} catch (e: Exception) { Result.failure(e) }
