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
    import android.os.Build
    import androidx.core.app.NotificationCompat
    import androidx.core.content.ContextCompat
    import kotlin.random.Random

    object NotificationHelper {

        const val CHANNEL_URGENT = "channel_alert_urgent"
        const val CHANNEL_NORMAL = "channel_service_running"

        @SuppressLint("FullScreenIntentPolicy")
        fun buildNotification(
            context: Context,
            title: String,
            message: String,
            channelId: String,
            channelName: String,
            intent: PendingIntent? = null,
            isUrgent: Boolean = false,
            isOngoing: Boolean = false
        ): Notification {

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = when {
                    isOngoing -> NotificationManager.IMPORTANCE_LOW
                    else -> NotificationManager.IMPORTANCE_HIGH
                }
                val channel = NotificationChannel(channelId, channelName, importance).apply {
                    description = "Notifikasi Deteksi Padi"
                    enableVibration(true)
                    setSound(
                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val largeIconBitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

            val colorInt = if (isUrgent) Color.RED else ContextCompat.getColor(context, R.color.primary) // Pastikan warna ini ada di colors.xml atau ganti Color.GREEN

            val builder = NotificationCompat.Builder(context, channelId)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.drawable.ic_stat_padi)
                .setFullScreenIntent(intent, isUrgent)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setGroup("laporan_padi_group")
                .setLargeIcon(largeIconBitmap)
                .setColor(colorInt)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setOngoing(isOngoing)
                .setAutoCancel(!isOngoing)

            builder.priority = if (isUrgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

            if (intent != null) {
                builder.setContentIntent(intent)
            }

            return builder.build()
        }

        fun showNotification(
            context: Context,
            title: String,
            message: String,
            channelId: String,
            channelName: String,
            intent: PendingIntent? = null,
            isUrgent: Boolean = false
        ) {
            val notification = buildNotification(
                context, title, message, channelId, channelName, intent, isUrgent, isOngoing = false
            )

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(Random.nextInt(), notification)
        }
    }