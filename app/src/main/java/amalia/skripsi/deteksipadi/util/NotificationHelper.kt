package amalia.skripsi.deteksipadi.util

import amalia.skripsi.deteksipadi.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.provider.Settings
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlin.random.Random
import androidx.core.content.edit

object NotificationHelper {
    const val CHANNEL_URGENT = "channel_alert_urgent_v2"
    const val CHANNEL_NORMAL = "channel_notification_general_v2"
    const val CHANNEL_SERVICE = "channel_service_running_v2"
    private const val PREFS = "notification_settings"
    private const val KEY_ENABLED = "push_notifications_enabled"
    private const val CATEGORY_PREFS = "notification_category_settings"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(createChannel(CHANNEL_NORMAL, "Notifikasi Deteksi Padi", NotificationManager.IMPORTANCE_HIGH, alerting = true))
        manager.createNotificationChannel(createChannel(CHANNEL_URGENT, "Peringatan Penting", NotificationManager.IMPORTANCE_HIGH, alerting = true))
        manager.createNotificationChannel(createChannel(CHANNEL_SERVICE, "Layanan Berjalan", NotificationManager.IMPORTANCE_LOW, alerting = false))
    }

    private fun createChannel(id: String, name: String, importance: Int, alerting: Boolean): NotificationChannel =
        NotificationChannel(id, name, importance).apply {
            description = if (alerting) "Pemberitahuan dan peringatan Deteksi Padi" else "Status layanan aplikasi"
            enableVibration(alerting)
            if (alerting) {
                vibrationPattern = longArrayOf(0, 250, 120, 250)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                )
            }
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(true)
        }

    fun isCategoryEnabled(context: Context, category: String): Boolean =
        context.getSharedPreferences(CATEGORY_PREFS, Context.MODE_PRIVATE).getBoolean(category, true)

    fun setCategoryEnabled(context: Context, category: String, enabled: Boolean) {
        context.getSharedPreferences(CATEGORY_PREFS, Context.MODE_PRIVATE).edit { putBoolean(category, enabled) }
    }

    fun hasExplicitNotificationPreference(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_ENABLED)

    fun areNotificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, enabled)
        }
        if (!enabled) {
            // Hapus notifikasi aplikasi yang sudah tampil tanpa menghilangkan notifikasi
            // foreground service yang wajib dipertahankan Android.
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.activeNotifications
                    .filter { it.notification.channelId != CHANNEL_SERVICE }
                    .forEach { manager.cancel(it.tag, it.id) }
            } else {
                manager.cancelAll()
            }
        }
    }

    @SuppressLint("FullScreenIntentPolicy")
    fun buildNotification(
        context: Context, title: String, message: String, channelId: String, channelName: String,
        intent: PendingIntent? = null, isUrgent: Boolean = false, isOngoing: Boolean = false
    ): Notification {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (isOngoing) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
            notificationManager.createNotificationChannel(
                createChannel(channelId, channelName, importance, alerting = !isOngoing)
            )
        }
        val largeIconBitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        val colorInt = if (isUrgent) Color.RED else ContextCompat.getColor(context, R.color.primary)
        return NotificationCompat.Builder(context, channelId)
            .setPriority(if (isOngoing) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setDefaults(if (isOngoing) 0 else Notification.DEFAULT_ALL)
            .setCategory(if (isUrgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setSmallIcon(R.drawable.ic_stat_padi)
            .setGroup("laporan_padi_group")
            .setLargeIcon(largeIconBitmap)
            .setColor(colorInt)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(false)
            .setAutoCancel(!isOngoing)
            .apply { if (intent != null) setContentIntent(intent) }
            .build()
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        channelId: String,
        channelName: String,
        intent: PendingIntent? = null,
        isUrgent: Boolean = false,
        category: String? = null
    ) {
        if (category != null && !isCategoryEnabled(context, category)) return
        if (!areNotificationsEnabled(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(Random.nextInt(), buildNotification(context, title, message, channelId, channelName, intent, isUrgent))
    }
}
