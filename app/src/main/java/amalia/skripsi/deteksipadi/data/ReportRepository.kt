@file:Suppress("DEPRECATION")
package amalia.skripsi.deteksipadi.data

import amalia.skripsi.deteksipadi.ml.DetectionResult
import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage

suspend fun fetchActiveLaporan(): List<LaporanDto> {
    return try {
        supabase.from("vw_hotspot_laporan_aktif")
            .select(columns = Columns.raw(LAPORAN_MAP_COLUMNS))
            .decodeList<LaporanDto>()
    } catch (e: Exception) {
        Log.e("LAPORAN_ACTIVE", "Gagal memuat laporan aktif; ini bukan hasil kosong yang terkonfirmasi.", e)
        emptyList()
    }
}


suspend fun fetchLaporanUntukPOPT(poptId: String): List<LaporanDto> {
    return try {
        val wilayahList = supabase.from("popt_wilayah")
            .select(columns = Columns.raw("kecamatan_id")) {
                filter { eq("popt_id", poptId) }
            }.decodeList<PoptWilayahDto>()

        val kecIds = wilayahList.map { it.kecamatan_id }
        if (kecIds.isEmpty()) return emptyList()

        supabase.from("laporan")
            .select(columns = Columns.raw(LAPORAN_COLUMNS)) {
                filter { isIn("kecamatan_id", kecIds) }
            }.decodeList<LaporanDto>()
    } catch (e: Exception) {
        Log.e("LAPORAN_POPT", "Gagal memuat laporan wilayah POPT; periksa kontrak query/RLS/jaringan.", e)
        emptyList()
    }
}


suspend fun fetchLaporanUntukPeta(): List<LaporanPetaDto> {
    return try {
        supabase.from("vw_laporan_peta_public")
            .select()
            .decodeList<LaporanPetaDto>()
    } catch (e: Exception) {
        Log.w("LAPORAN_PETA", "Gagal memuat titik peta: ${e.message}")
        emptyList()
    }
}


suspend fun fetchLaporanById(id: String): LaporanDto? {
    return try {
        supabase.from("laporan")
            .select(columns = Columns.raw(LAPORAN_COLUMNS)) {
                filter { eq("id", id) }
            }
            .decodeList<LaporanDto>()
            .firstOrNull()
    } catch (e: Exception) {
        Log.e("LAPORAN_DETAIL", "Gagal memuat detail laporan $id; periksa kontrak query/RLS/jaringan.", e)
        null
    }
}


suspend fun fetchNamaPelapor(petaniId: String): String {
    return try {
        val profile = supabase.from("profile_directory")
            .select(columns = Columns.raw("full_name")) {
                filter { eq("id", petaniId) }
            }
            .decodeSingleOrNull<ProfileNameOnly>()
        profile?.full_name ?: "Petani (Tidak diketahui)"
    } catch (e: Exception) {
        "Petani"
    }
}


suspend fun fetchNamaPoptByKecamatan(kecId: String?): String {
    if (kecId == null) return "Belum Ada Petugas"
    return try {
        val poptWilayah = supabase.from("popt_wilayah")
            .select(Columns.raw("popt_id")) { filter { eq("kecamatan_id", kecId) } }
            .decodeList<PoptWilayahSimple>()
            .firstOrNull()

        val pId = poptWilayah?.popt_id ?: return "Belum Ada Petugas"
        val profile = supabase.from("profile_directory")
            .select(Columns.raw("full_name")) { filter { eq("id", pId) } }
            .decodeSingleOrNull<ProfileNameOnly>()
        profile?.full_name ?: "Petugas POPT"
    } catch (e: Exception) {
        "Petugas POPT"
    }
}


suspend fun getKecamatanIdByCoordinate(lat: Double, lon: Double): String? {
    return try {
        val result = supabase.postgrest.rpc(
            "get_kecamatan_id_by_koordinat",
            CoordinateParams(p_lat = lat, p_lon = lon)
        ).decodeAsOrNull<String>()
        result?.takeIf { it.isNotBlank() && it != "null" }
    } catch (e: Exception) {
        null
    }
}


suspend fun submitReportToSupabase(
    photoBytes: ByteArray,
    results: List<DetectionResult>,
    lat: Double,
    lon: Double,
    alamatLengkap: String,
    userId: String,
    deskripsiGejala: String,
    jenisPelaporan: String = "AI",
    isManualMode: Boolean,
    manualPestName: String
): Result<String> {
    return try {
        val kecId = getKecamatanIdByCoordinate(lat, lon)
        if (kecId == null) throw Exception("OUT_OF_BOUNDS")

        val fileName = "laporan_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)
        val publicUrl = bucket.publicUrl(fileName)

        val bestResult = results.maxByOrNull { it.score }
        val locationString = "SRID=4326;POINT($lon $lat)"

        val finalLabel = if (isManualMode) {
            if (manualPestName == "Tidak Tahu" || manualPestName.isBlank()) null else manualPestName
        } else {
            bestResult?.label
        }

        val finalConfidence = if (isManualMode) null else bestResult?.score
        val finalTipe = if (isManualMode) "MANUAL" else "AI"
        val finalPrioritas = if (isManualMode) "rendah"
        else if (finalConfidence != null && finalConfidence > 0.5f) "tinggi" else "sedang"

        val laporan = LaporanInsertDto(
            petani_id = userId,
            foto_url = publicUrl,
            label_ai = finalLabel,
            confidence = finalConfidence,
            lokasi = locationString,
            alamat_lengkap = alamatLengkap,
            kecamatan_id = kecId,
            deskripsi_gejala = deskripsiGejala,
            jenis_pelaporan = finalTipe,
            prioritas = finalPrioritas,
            status = "menunggu_verifikasi"
        )

        supabase.from("laporan").insert(laporan)
        Result.success("Berhasil")
    } catch (e: Exception) {
        Result.failure(e)
    }
}


suspend fun fetchLaporanUpdates(laporanId: String): List<LaporanUpdateDto> {
    return try {
        supabase.from("laporan_updates")
            .select {
                filter { eq("laporan_id", laporanId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<LaporanUpdateDto>()
    } catch (e: Exception) {
        emptyList()
    }
}


suspend fun submitLaporanUpdate(
    laporanId: String,
    userId: String,
    photoBytes: ByteArray,
    labelAi: String,
    confidence: Float,
    catatan: String,
    latitude: Double,
    longitude: Double,
    gpsAccuracyMeters: Float,
    capturedAt: String
): Result<Unit> {
    return try {
        val fileName = "update_${laporanId}_${System.currentTimeMillis()}.jpg"
        val bucket = supabase.storage.from("evidence_photos")
        bucket.upload(fileName, photoBytes)
        val publicUrl = bucket.publicUrl(fileName)

        val updateData = LaporanUpdateInsertDto(
            laporan_id = laporanId,
            foto_update_url = publicUrl,
            label_ai_update = labelAi,
            confidence_update = confidence,
            catatan = catatan,
            user_id = userId,
            latitude = latitude,
            longitude = longitude,
            gps_accuracy_m = gpsAccuracyMeters,
            captured_at = capturedAt
        )

        supabase.from("laporan_updates").insert(updateData)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}


suspend fun setLaporanPerluKunjungan(
    laporanId: String,
    alasan: String,
    waktuKunjungan: String,
    catatanJadwal: String?,
    instruksi: String?,
    radius: Double?,
    correctedLabel: String?
): Result<Unit> = try {
    require(alasan.trim().length >= 5) { "Alasan kunjungan wajib diisi minimal 5 karakter." }
    require(waktuKunjungan.isNotBlank()) { "Tanggal dan jam kunjungan wajib dipilih." }
    supabase.postgrest.rpc(
        "set_laporan_perlu_kunjungan",
        PerluKunjunganParams(
            p_laporan_id = laporanId,
            p_alasan = alasan.trim(),
            p_waktu_kunjungan = waktuKunjungan,
            p_catatan_jadwal = catatanJadwal?.trim()?.takeIf { it.isNotEmpty() },
            p_instruksi = instruksi?.trim()?.takeIf { it.isNotEmpty() },
            p_radius = radius,
            p_corrected_label = correctedLabel?.trim()?.takeIf { it.isNotEmpty() }
        )
    )
    Result.success(Unit)
} catch (e: Exception) {
    Result.failure(e)
}

// Aturan transisi status yang otoritatif tetap ditegakkan oleh trigger/RLS di database.

suspend fun updateLaporanStatus(
    laporanId: String,
    status: String,
    instruksi: String? = null,
    radius: Double? = null,
    correctedLabel: String? = null,
    alasanPenolakan: String? = null,
    alasanKunjungan: String? = null
): Result<Unit> {
    return try {
        val statusDiizinkan = setOf(
            "menunggu_verifikasi",
            "penanganan_mandiri",
            "perlu_kunjungan",
            "selesai",
            "ditolak"
        )
        require(status in statusDiizinkan) { "Status laporan tidak dikenal." }

        if (status == "ditolak") {
            require(!alasanPenolakan.isNullOrBlank() && alasanPenolakan.trim().length >= 5) {
                "Alasan penolakan wajib diisi minimal 5 karakter."
            }
        }

        supabase.from("laporan").update({
            set("status", status)
            if (instruksi != null) set("instruksi_popt", instruksi)
            if (radius != null) set("radius", radius)
            if (correctedLabel != null) set("label_koreksi_popt", correctedLabel)
            if (status == "ditolak") set("alasan_penolakan", alasanPenolakan!!.trim())
            if (status == "perlu_kunjungan" && !alasanKunjungan.isNullOrBlank()) {
                set("alasan_kunjungan", alasanKunjungan.trim())
            }
        }) {
            filter { eq("id", laporanId) }
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}


suspend fun rejectLaporan(laporanId: String, alasanPenolakan: String): Result<Unit> {
    return updateLaporanStatus(
        laporanId = laporanId,
        status = "ditolak",
        alasanPenolakan = alasanPenolakan
    )
}


suspend fun markLaporanSelesai(laporanId: String): Result<Unit> {
    return updateLaporanStatus(laporanId = laporanId, status = "selesai")
}
