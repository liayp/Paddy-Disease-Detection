package amalia.skripsi.deteksipadi.services

import amalia.skripsi.deteksipadi.MainActivity
import amalia.skripsi.deteksipadi.R
import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.UserProfile
import amalia.skripsi.deteksipadi.data.fetchActiveHotspots
import amalia.skripsi.deteksipadi.data.supabase
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
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class HazardDetectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Data Cache
    private var hotspots: List<HotspotDto> = emptyList()
    private var userProfile: UserProfile? = null
    private var realtimeChannel: RealtimeChannel? = null

    // State Debounce Notifikasi Geofencing
    private var isDangerNotified = false
    private var isWarningNotified = false

    companion object {
        const val CHANNEL_ID_SERVICE = "channel_service_running"
        const val CHANNEL_ID_ALERT = "channel_alert_urgent"
        const val NOTIF_ID_SERVICE = 1
        const val NOTIF_ID_ALERT = 2
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannels()

        // Inisialisasi Data & Role Check
        serviceScope.launch {
            try {
                val authRepo = AuthRepository(this@HazardDetectionService)
                userProfile = authRepo.getUserProfile()

                // Ambil data hotspot untuk geofencing (hanya jika diperlukan)
                if (userProfile?.role != "popt") {
                    hotspots = fetchActiveHotspots()
                }

                // Percabangan Logika Berdasarkan Role
                if (userProfile?.role == "popt") {
                    // POPT: Memantau Database Realtime
                    setupPoptRealtimeListener()
                } else {
                    // PETANI: Memantau Lokasi GPS (Geofencing)
                    startLocationUpdates()
                }
            } catch (e: Exception) {
                Log.e("HazardService", "Initialization failed: ${e.message}")
            }
        }

        // Setup Location Callback (Geofencing)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Double check: POPT tidak butuh kalkulasi jarak ini
                if (userProfile?.role != "popt") {
                    for (location in result.locations) {
                        checkGeofenceForFarmer(location.latitude, location.longitude)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createOngoingNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID_SERVICE, notification)
        }

        // Location updates hanya akan dipicu di onCreate jika role != popt
        return START_STICKY
    }

    private suspend fun setupPoptRealtimeListener() {
        try {
            realtimeChannel = supabase.realtime.channel("public-reports-popt")

            // Menggunakan syntax flow modern dengan Generics <Insert>
            val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "reports"
            }

            realtimeChannel!!.subscribe()

            changeFlow.collect { action ->
                val record = action.record
                val incomingDistrict = record["district"]?.jsonPrimitive?.contentOrNull

                // Filter Personal: Cek apakah kecamatan laporan ada di WKPP POPT ini
                val myWkpp = userProfile?.wkpp_kecamatan ?: emptyList()
                val isRelevant = myWkpp.any { it.equals(incomingDistrict, ignoreCase = true) }

                if (isRelevant) {
                    val label = record["ai_label"]?.jsonPrimitive?.contentOrNull ?: "Hama"
                    sendTargetedNotification(label, incomingDistrict ?: "-")
                }
            }
        } catch (e: Exception) {
            Log.e("HazardService", "Realtime Error: ${e.message}")
        }
    }

    private fun checkGeofenceForFarmer(userLat: Double, userLon: Double) {
        if (hotspots.isEmpty()) return

        var minDistance = Double.MAX_VALUE
        for (spot in hotspots) {
            val dist = LocationUtils.calculateDistance(userLat, userLon, spot.lat, spot.lon)
            if (dist < minDistance) minDistance = dist
        }

        when {
            minDistance <= 20.0 -> {
                if (!isDangerNotified) {
                    sendAlertNotification("BAHAYA! HAMA SANGAT DEKAT!", "Jarak < 20m dari posisi Anda.", true)
                    isDangerNotified = true; isWarningNotified = true
                }
            }
            minDistance <= 300.0 -> {
                if (!isWarningNotified) {
                    sendAlertNotification("Memasuki Area Rawan", "Terdeteksi hama dalam radius 300m.", false)
                    isWarningNotified = true; isDangerNotified = false
                }
            }
            else -> {
                isWarningNotified = false; isDangerNotified = false
            }
        }
    }

    private fun sendTargetedNotification(hamaLabel: String, kecamatan: String) {
        // Intent untuk membuka Daftar Laporan saat diklik
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "popt_reports")
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle("Laporan Baru: $hamaLabel")
            .setContentText("Masuk di wilayah binaan Anda (Kec. $kecamatan).")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Suara & Getar Default
            .build()

        getSystemService(NotificationManager::class.java).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun sendAlertNotification(title: String, content: String, isUrgent: Boolean) {
        val pattern = if (isUrgent) longArrayOf(0, 500, 200, 500) else longArrayOf(0, 200)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(pattern)
            .build()

        getSystemService(NotificationManager::class.java).notify(NOTIF_ID_ALERT, notification)
    }

    private fun createOngoingNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Sistem Deteksi Padi Aktif")
            .setContentText("Memantau wilayah & lokasi di latar belakang...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(CHANNEL_ID_SERVICE, "Status Service", NotificationManager.IMPORTANCE_MIN)
            manager.createNotificationChannel(serviceChannel)

            val alertChannel = NotificationChannel(CHANNEL_ID_ALERT, "Peringatan Hama", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifikasi Urgent Laporan & Geofence"
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun startLocationUpdates() {
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            Log.e("HazardService", "Izin lokasi ditolak")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        CoroutineScope(Dispatchers.IO).launch { realtimeChannel?.unsubscribe() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}