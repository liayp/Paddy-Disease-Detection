package amalia.skripsi.deteksipadi.workers

import amalia.skripsi.deteksipadi.MainActivity
import amalia.skripsi.deteksipadi.data.getKecamatanIdByCoordinate
import amalia.skripsi.deteksipadi.data.local.AppDatabase
import amalia.skripsi.deteksipadi.data.submitReportToSupabase
import amalia.skripsi.deteksipadi.data.submitLaporanUpdate
import amalia.skripsi.deteksipadi.data.local.PendingLaporanUpdateQueue
import amalia.skripsi.deteksipadi.data.supabase
import amalia.skripsi.deteksipadi.ml.DetectionResult
import amalia.skripsi.deteksipadi.ui.screens.petani.detection.ImageUtils
import amalia.skripsi.deteksipadi.util.NotificationHelper
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.util.Log
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.jan.supabase.auth.auth
import java.io.File

class UploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private fun sendNotification(title: String, message: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "history")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        NotificationHelper.showNotification(applicationContext, title, message, "upload_channel", "Upload Status", pendingIntent, category = "reports")
    }

    override suspend fun doWork(): Result {
        try {
            if (supabase.auth.currentSessionOrNull() == null) supabase.auth.loadFromStorage()
            if (supabase.auth.currentSessionOrNull() == null) return Result.retry()
        } catch (e: Exception) { return Result.retry() }

        val context = applicationContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "padi-database").build()
        val dao = db.pendingReportDao()
        val pendingReports = dao.getAllReports()
        var isAllSuccess = true
        val activeUserId = try { supabase.auth.currentUserOrNull()?.id } catch (_: Exception) { null }
        if (activeUserId.isNullOrBlank()) {
            db.close()
            return Result.retry()
        }

        for (report in pendingReports) {
            if (report.userId != activeUserId) {
                isAllSuccess = false
                continue
            }
            try {
                // Validasi wilayah membutuhkan respons server; null tidak cukup untuk menyatakan koordinat di luar cakupan.
                val kecId = try {
                    getKecamatanIdByCoordinate(report.lat, report.lon)
                } catch (e: Exception) {
                    isAllSuccess = false
                    continue
                }
                if (kecId == null) {
                    isAllSuccess = false
                    sendNotification("Laporan menunggu pemeriksaan", "Lokasi laporan '${report.label}' belum dapat diverifikasi. Data tetap tersimpan di perangkat dan akan dicoba kembali.")
                    continue
                }

                val file = File(report.imagePath)
                if (file.exists()) {
                    val bytes = file.readBytes()

                    var finalKec = report.kecamatan
                    var finalKel = report.kelurahan
                    var finalAddr = report.addressDetail

                    if (finalKec.isBlank() || finalKec.contains("Tidak", true)) {
                        val addressInfo = ImageUtils.getAddressName(context, report.lat, report.lon)
                        finalKec = addressInfo.first
                        finalKel = addressInfo.second
                        finalAddr = addressInfo.third
                    }

                    val alamatLengkapGabungan = "$finalAddr, $finalKel, Kec. $finalKec"
                    val isManual = report.confidence == 0f

                    val dummyResult = DetectionResult(box = RectF(0f, 0f, 0f, 0f), label = report.label, score = report.confidence, labelIndex = 0)

                    val result = submitReportToSupabase(
                        photoBytes = bytes, results = listOf(dummyResult), lat = report.lat, lon = report.lon,
                        alamatLengkap = alamatLengkapGabungan, userId = report.userId,
                        deskripsiGejala = report.deskripsi_gejala, isManualMode = isManual, manualPestName = report.label
                    )

                    if (result.isSuccess) {
                        dao.deleteReport(report.id)
                        file.delete()
                        sendNotification("Laporan Terkirim! ✅", "Deteksi '$finalKec' berhasil diupload ke sistem peringatan dini.")
                    } else {
                        isAllSuccess = false
                    }
                } else {
                    isAllSuccess = false
                    sendNotification("Foto laporan tidak ditemukan", "Laporan '${report.label}' masih tercatat, tetapi foto lokal tidak ditemukan. Periksa penyimpanan perangkat sebelum mengirim ulang.")
                }
            } catch (e: Exception) {
                isAllSuccess = false
            }
        }

        // Bukti update lahan memiliki antrean lokal tersendiri agar perubahan skema Room
        // yang lama tidak menghapus antrean laporan baru yang sudah ada.
        val updateQueue = PendingLaporanUpdateQueue(context)
        try {
            val pendingUpdates = updateQueue.getAll()
            for (update in pendingUpdates) {
                try {
                    val activeUserId = supabase.auth.currentUserOrNull()?.id
                    if (activeUserId != update.userId) {
                        isAllSuccess = false
                        Log.w("UploadWorker", "Update ${update.id} ditunda karena akun aktif berbeda dari akun pembuat antrean.")
                        continue
                    }
                    val image = File(update.imagePath)
                    if (!image.exists()) {
                        updateQueue.delete(update.id)
                        sendNotification(
                            "Bukti Update Tidak Ditemukan",
                            "Satu foto update lahan tidak ditemukan di penyimpanan perangkat dan tidak dapat dikirim. Silakan ambil foto update kembali."
                        )
                        continue
                    }
                    val result = submitLaporanUpdate(
                        laporanId = update.laporanId,
                        userId = update.userId,
                        photoBytes = image.readBytes(),
                        labelAi = update.labelAi,
                        confidence = update.confidence,
                        catatan = update.catatan,
                        latitude = update.latitude,
                        longitude = update.longitude,
                        gpsAccuracyMeters = update.gpsAccuracyMeters,
                        capturedAt = update.capturedAt
                    )
                    if (result.isSuccess) {
                        updateQueue.delete(update.id)
                        image.delete()
                        sendNotification(
                            "Update Kondisi Lahan Terkirim",
                            "Bukti kondisi lahan untuk laporan berhasil disinkronkan ke server."
                        )
                    } else {
                        isAllSuccess = false
                        Log.e("UploadWorker", "Gagal sinkron update ${update.id}", result.exceptionOrNull())
                    }
                } catch (e: Exception) {
                    isAllSuccess = false
                    Log.e("UploadWorker", "Kesalahan sinkron update ${update.id}", e)
                }
            }
        } finally {
            updateQueue.close()
            db.close()
        }

        return if (isAllSuccess) Result.success() else Result.retry()
    }
}