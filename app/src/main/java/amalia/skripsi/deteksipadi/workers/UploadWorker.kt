package amalia.skripsi.deteksipadi.workers

import amalia.skripsi.deteksipadi.MainActivity
import amalia.skripsi.deteksipadi.data.local.AppDatabase
import amalia.skripsi.deteksipadi.data.submitReportToSupabase
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

    private fun sendSuccessNotification(hamaLabel: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "history")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        NotificationHelper.showNotification(
            context = applicationContext,
            title = "Laporan Terkirim! ✅",
            message = "Deteksi '$hamaLabel' berhasil diupload ke sistem peringatan dini.",
            channelId = "upload_channel",
            channelName = "Laporan Terkirim",
            intent = pendingIntent
        )
    }

    override suspend fun doWork(): Result {
        Log.d("UploadWorker", "--- WORKER DIMULAI ---")

        try {
            if (supabase.auth.currentSessionOrNull() == null) {
                supabase.auth.loadFromStorage()
            }
            if (supabase.auth.currentSessionOrNull() == null) {
                Log.e("UploadWorker", "User logout / token expired.")
                return Result.failure()
            }
        } catch (e: Exception) {
            Log.e("UploadWorker", "Auth Error: ${e.message}")
            return Result.retry()
        }

        val context = applicationContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "padi-database").build()
        val dao = db.pendingReportDao()
        val pendingReports = dao.getAllReports()

        if (pendingReports.isEmpty()) return Result.success()

        var isAllSuccess = true

        for (report in pendingReports) {
            try {
                val file = File(report.imagePath)
                if (file.exists()) {
                    val bytes = file.readBytes()

                    var finalKec = report.kecamatan
                    var finalKel = report.kelurahan
                    var finalAddr = report.addressDetail

                    // Fallback Reverse Geocoding jika lokasi belum valid
                    if (finalKec.isBlank() || finalKec.contains("Tidak", true)) {
                        val addressInfo = ImageUtils.getAddressName(context, report.lat, report.lon)
                        finalKec = addressInfo.first
                        finalKel = addressInfo.second
                        finalAddr = addressInfo.third
                    }

                    // Merakit string Alamat Lengkap sesuai skema DB baru
                    val alamatLengkapGabungan = "$finalAddr, $finalKel, Kec. $finalKec"

                    Log.d("UploadWorker", "Upload ID: ${report.id} User: ${report.userId}")

                    val dummyResult = DetectionResult(
                        box = RectF(0f, 0f, 0f, 0f),
                        label = report.label,
                        score = report.confidence,
                        labelIndex = 0
                    )

                    val result = submitReportToSupabase(
                        photoBytes = bytes,
                        results = listOf(dummyResult),
                        lat = report.lat,
                        lon = report.lon,
                        alamatLengkap = alamatLengkapGabungan,
                        userId = report.userId,
                        namaKecamatanDariGps = finalKec
                    )

                    if (result.isSuccess) {
                        dao.deleteReport(report.id)
                        file.delete()
                        sendSuccessNotification(report.label)
                    } else {
                        Log.e("UploadWorker", "Gagal Upload: ${result.exceptionOrNull()}")
                        isAllSuccess = false
                    }
                } else {
                    dao.deleteReport(report.id) // File hilang, hapus dari antrean
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isAllSuccess = false
            }
        }

        return if (isAllSuccess) Result.success() else Result.retry()
    }
}