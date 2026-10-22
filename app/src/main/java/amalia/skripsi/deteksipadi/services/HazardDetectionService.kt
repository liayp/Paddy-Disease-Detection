package amalia.skripsi.deteksipadi.services

import amalia.skripsi.deteksipadi.util.LocationAccessPreference

import amalia.skripsi.deteksipadi.MainActivity
import amalia.skripsi.deteksipadi.data.*
import amalia.skripsi.deteksipadi.ui.screens.general.peta.LocationUtils
import amalia.skripsi.deteksipadi.util.NotificationHelper
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@AndroidEntryPoint
class HazardDetectionService : Service() {

    @Inject
    lateinit var hazardRepository: HazardRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isLocationTracking = false

    // SEKARANG MENGGUNAKAN LaporanDto
    private var hotspots: List<LaporanDto> = emptyList()
    private var userProfile: UserProfile? = null
    private var realtimeChannel: RealtimeChannel? = null

    private var isDangerNotified = false
    private var isWarningNotified = false

    companion object {
        const val CHANNEL_ID_SERVICE = NotificationHelper.CHANNEL_SERVICE
        const val CHANNEL_ID_ALERT = NotificationHelper.CHANNEL_URGENT
        const val NOTIF_ID_SERVICE = 1
        const val ACTION_DISABLE_LOCATION = "amalia.skripsi.deteksipadi.action.DISABLE_LOCATION"
        const val ACTION_ENABLE_LOCATION = "amalia.skripsi.deteksipadi.action.ENABLE_LOCATION"
        @Volatile var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (userProfile?.role != "popt" && LocationAccessPreference.canUsePreciseLocation(this@HazardDetectionService)) {
                    result.lastLocation?.let { location ->
                        checkGeofenceForFarmer(location.latitude, location.longitude)
                    }
                }
            }
        }

        serviceScope.launch {
            try {
                val authRepo = AuthRepository(this@HazardDetectionService)
                userProfile = authRepo.getUserProfile()
                if (userProfile?.role != "popt") refreshHotspots()
                if (userProfile?.role == "popt") setupPoptRealtimeListener() else startLocationUpdates()
            } catch (e: Exception) {
                Log.e("HazardService", "Init failed: ${e.message}")
            }
        }
    }

    private suspend fun refreshHotspots() {
        try {
            // EWS hanya memakai hotspot aktif yang memang dinyatakan cluster oleh server.
            hotspots = supabase.from("vw_hotspot_laporan_aktif")
                .select(columns = Columns.raw(LAPORAN_COLUMNS))
                .decodeList<LaporanDto>()
        } catch (e: Exception) {
            Log.e("HazardService", "Fetch hotspots failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationHelper.buildNotification(
            context = this,
            title = "Sistem Deteksi Padi Aktif",
            message = if (LocationAccessPreference.canUsePreciseLocation(this)) "Memantau radius ancaman hama..." else "Pemantauan radius memerlukan izin lokasi presisi dan pengaturan lokasi aktif.",
            channelId = CHANNEL_ID_SERVICE,
            channelName = "Service Berjalan",
            intent = pendingIntent,
            isOngoing = true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID_SERVICE, notification)
        }

        when (intent?.action) {
            ACTION_DISABLE_LOCATION -> {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                isLocationTracking = false
            }
            ACTION_ENABLE_LOCATION -> if (LocationAccessPreference.canUsePreciseLocation(this)) startLocationUpdates()
        }
        return START_STICKY
    }

    private suspend fun setupPoptRealtimeListener() {
        try {
            realtimeChannel = supabase.realtime.channel("laporan-popt-realtime")
            val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "laporan"
            }
            realtimeChannel!!.subscribe()
            changeFlow.collect { action ->
                val record = action.record
                val label = record["label_ai"]?.jsonPrimitive?.contentOrNull ?: "Hama"
                val prioritas = record["prioritas"]?.jsonPrimitive?.contentOrNull ?: "rendah"
                val alamat = record["alamat_lengkap"]?.jsonPrimitive?.contentOrNull ?: "Lokasi Baru"

                sendTargetedNotification(label, alamat, prioritas)
            }
        } catch (e: Exception) { Log.e("Hazard", e.message.toString()) }
    }

    private fun checkGeofenceForFarmer(userLat: Double, userLon: Double) {
        if (hotspots.isEmpty()) return

        var minDistance = Double.MAX_VALUE
        var closestRadiusMeters = 300.0

        for (spot in hotspots) {
            val dist = LocationUtils.calculateDistance(userLat, userLon, spot.lat, spot.lon)
            if (dist < minDistance) {
                minDistance = dist
                closestRadiusMeters = ((spot.radius ?: 0.3) * 1000.0).coerceAtLeast(50.0)
            }
        }

        val isDanger = minDistance <= closestRadiusMeters
        hazardRepository.setDangerStatus(isDanger, minDistance)

        val criticalRadiusMeters = minOf(50.0, closestRadiusMeters)
        if (minDistance <= criticalRadiusMeters) {
            if (!isDangerNotified) {
                sendAlertNotification("BAHAYA!", "Hama sangat dekat", true)
                isDangerNotified = true
                isWarningNotified = true
            }
        } else if (minDistance <= closestRadiusMeters) {
            if (!isWarningNotified) {
                sendAlertNotification("Waspada", "Hama berada dalam radius peringatan.", false)
                isWarningNotified = true
                isDangerNotified = false
            }
        } else {
            isDangerNotified = false
            isWarningNotified = false
        }
    }

    private fun sendTargetedNotification(label: String, lokasi: String, prioritas: String) {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra("navigate_to", "popt_reports") }
        val pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val title = if(prioritas == "tinggi") "🚨 Laporan Cluster Baru: $label" else "Laporan Baru: $label"
        NotificationHelper.showNotification(this, title, lokasi, CHANNEL_ID_ALERT, "Laporan baru", pIntent, prioritas == "tinggi", category = "reports")
    }

    private fun sendAlertNotification(title: String, msg: String, urgent: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra("navigate_to", "peta") }
        val pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        NotificationHelper.showNotification(this, title, msg, CHANNEL_ID_ALERT, "Peringatan lokasi", pIntent, urgent, category = "geofence")
    }

    private fun startLocationUpdates() {
        if (!LocationAccessPreference.canUsePreciseLocation(this) || isLocationTracking) return
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            isLocationTracking = true
        } catch (_: SecurityException) {
            isLocationTracking = false
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        serviceScope.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationTracking = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}