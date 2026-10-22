package amalia.skripsi.deteksipadi.services

import amalia.skripsi.deteksipadi.MainActivity
import amalia.skripsi.deteksipadi.data.supabase
import amalia.skripsi.deteksipadi.util.NotificationHelper
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FCMService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val judul = remoteMessage.data["judul"] ?: "Notifikasi Baru"
        val pesan = remoteMessage.data["pesan"] ?: ""
        val jenis = remoteMessage.data["jenis"] ?: "laporan_masuk"
        val laporanId = remoteMessage.data["laporan_id"]

        val isUrgent = (jenis == "geofence_alert")
        val category = when {
            jenis.equals("geofence_alert", ignoreCase = true) || jenis.contains("lokasi", ignoreCase = true) -> "geofence"
            jenis.contains("jadwal", ignoreCase = true) || jenis.contains("kunjungan", ignoreCase = true) -> "schedule"
            else -> "reports"
        }
        val channelId = if (isUrgent) NotificationHelper.CHANNEL_URGENT else NotificationHelper.CHANNEL_NORMAL

        // FIX DEEP LINKING: Menggunakan pola URI agar NavHost Compose langsung membuka halaman terkait
        val intent = if (laporanId != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("agriguard://report_detail/$laporanId"), this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to", "notifications")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        NotificationHelper.showNotification(
            context = applicationContext,
            title = judul,
            message = pesan,
            channelId = channelId,
            channelName = "Deteksi Padi",
            intent = pIntent,
            isUrgent = isUrgent,
            category = category
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "Token baru: $token")

        val user = supabase.auth.currentUserOrNull()
        if (user != null) {
            scope.launch {
                try {
                    supabase.from("profiles").update(
                        buildJsonObject { put("fcm_token", token) }
                    ) {
                        filter { eq("id", user.id) }
                    }
                } catch (e: Exception) {
                    Log.e("FCM_SERVICE", "Gagal update token baru")
                }
            }
        }
    }
}