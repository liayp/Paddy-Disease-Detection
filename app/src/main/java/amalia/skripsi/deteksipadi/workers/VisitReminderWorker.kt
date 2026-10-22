package amalia.skripsi.deteksipadi.workers

import amalia.skripsi.deteksipadi.MainActivity
import amalia.skripsi.deteksipadi.util.NotificationHelper
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class VisitReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.failure()
        val visitId = inputData.getString(KEY_VISIT_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Pengingat jadwal kunjungan"
        val message = inputData.getString(KEY_MESSAGE) ?: "Jadwal kunjungan Anda akan segera berlangsung."
        val preferences = applicationContext.getSharedPreferences("visit_reminder_settings", Context.MODE_PRIVATE)
        val enabled = when (kind) {
            KIND_DAY -> preferences.getBoolean("day_before", true)
            KIND_HOUR -> preferences.getBoolean("hour_before", true)
            else -> false
        }
        if (!enabled || !NotificationHelper.areNotificationsEnabled(applicationContext) ||
            !NotificationHelper.isCategoryEnabled(applicationContext, "schedule")) return Result.success()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "notifications")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, ("$visitId-$kind").hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        NotificationHelper.showNotification(
            applicationContext, title, message, NotificationHelper.CHANNEL_NORMAL,
            "Jadwal Kunjungan", pendingIntent
        )
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "reminder_kind"
        const val KEY_VISIT_ID = "visit_id"
        const val KEY_TITLE = "reminder_title"
        const val KEY_MESSAGE = "reminder_message"
        const val KIND_DAY = "day_before"
        const val KIND_HOUR = "hour_before"
    }
}
