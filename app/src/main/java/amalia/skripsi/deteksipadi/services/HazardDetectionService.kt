package amalia.skripsi.deteksipadi.services

import amalia.skripsi.deteksipadi.R
import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.fetchActiveHotspots
import amalia.skripsi.deteksipadi.ui.screens.general.peta.LocationUtils
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HazardDetectionService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var hotspots: List<HotspotDto> = emptyList()

    // Logic state untuk mencegah notifikasi spam (bunyi ting-ting tiap detik)
    private var isDangerNotified = false
    private var isWarningNotified = false

    companion object {
        const val CHANNEL_ID_SERVICE = "channel_service_popt"
        const val CHANNEL_ID_ALERT = "channel_alert_popt"
        const val NOTIF_ID_SERVICE = 1
        const val NOTIF_ID_ALERT = 2
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannels()

        // 1. Ambil data saat service mulai
        CoroutineScope(Dispatchers.IO).launch {
            try {
                hotspots = fetchActiveHotspots()
            } catch (e: Exception) {
                Log.e("HazardService", "Gagal ambil data: ${e.message}")
            }
        }

        // 2. Setup callback lokasi
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    checkGeofence(location.latitude, location.longitude)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createOngoingNotification()

        // Kompatibilitas Android 14
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID_SERVICE, notification)
        }

        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        try {
            // Update tiap 3 detik untuk keseimbangan baterai & akurasi
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateDistanceMeters(2f)
                .build()

            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("HazardService", "Izin lokasi tidak diberikan")
            stopSelf()
        }
    }

    private fun checkGeofence(userLat: Double, userLon: Double) {
        if (hotspots.isEmpty()) return

        var minDistance = Double.MAX_VALUE

        for (spot in hotspots) {
            val dist = LocationUtils.calculateDistance(userLat, userLon, spot.lat, spot.lon)
            if (dist < minDistance) minDistance = dist
        }

        // --- LOGIKA NOTIFIKASI BERTINGKAT ---

        // LEVEL 1: BAHAYA AKUT (< 10 Meter) - Saya naikkan sedikit dari 5m untuk toleransi GPS
        if (minDistance <= 10.0) {
            if (!isDangerNotified) { // Supaya tidak spam jika diam di tempat
                sendAlertNotification(
                    "BAHAYA! HAMA SANGAT DEKAT!",
                    "Jarak hanya ${minDistance.toInt()}m. Segera cek kondisi padi!",
                    isUrgent = true
                )
                isDangerNotified = true // Flag aktif
                isWarningNotified = true // Anggap warning juga sudah lewat
            }
        }
        // LEVEL 2: WASPADA (< 300 Meter)
        else if (minDistance <= 300.0) {
            if (!isWarningNotified) {
                sendAlertNotification(
                    "Memasuki Zona Rawan",
                    "Terdeteksi hama dalam radius 300m.",
                    isUrgent = false
                )
                isWarningNotified = true
                isDangerNotified = false // Reset danger jika menjauh
            }
        }
        // LEVEL 3: AMAN (> 300 Meter)
        else {
            // Reset semua flag jika keluar zona
            isWarningNotified = false
            isDangerNotified = false
        }
    }

    private fun sendAlertNotification(title: String, content: String, isUrgent: Boolean) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Pola getar:
        // Warning: Getar pendek
        // Urgent: Getar panjang, jeda, getar panjang (SOS style)
        val vibrationPattern = if (isUrgent) longArrayOf(0, 1000, 500, 1000, 500, 1000) else longArrayOf(0, 500, 200, 500)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Pastikan ganti dengan icon warning/app Anda
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(vibrationPattern)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIF_ID_ALERT, notification)
    }

    private fun createOngoingNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Si-POPT Security Berjalan")
            .setContentText("Menjaga sawah Anda di latar belakang...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE, "Service Berjalan", NotificationManager.IMPORTANCE_MIN
            )

            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERT, "Peringatan Hama", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi Keras Bahaya Hama"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}