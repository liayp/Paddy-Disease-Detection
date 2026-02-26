package amalia.skripsi.deteksipadi.services

import amalia.skripsi.deteksipadi.MainActivity
import amalia.skripsi.deteksipadi.data.AuthRepository
import amalia.skripsi.deteksipadi.data.HazardRepository
import amalia.skripsi.deteksipadi.data.HotspotDto
import amalia.skripsi.deteksipadi.data.UserProfile
import amalia.skripsi.deteksipadi.data.fetchActiveHotspots
import amalia.skripsi.deteksipadi.data.supabase
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

    private var hotspots: List<HotspotDto> = emptyList()
    private var userProfile: UserProfile? = null
    private var realtimeChannel: RealtimeChannel? = null

    private var isDangerNotified = false
    private var isWarningNotified = false

    companion object {
        const val CHANNEL_ID_SERVICE = "channel_service_running"
        const val CHANNEL_ID_ALERT = "channel_alert_urgent"
        const val NOTIF_ID_SERVICE = 1
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        serviceScope.launch {
            try {
                val authRepo = AuthRepository(this@HazardDetectionService)
                userProfile = authRepo.getUserProfile()

                if (userProfile?.role != "popt") {
                    hotspots = fetchActiveHotspots()
                }

                if (userProfile?.role == "popt") {
                    setupPoptRealtimeListener()
                } else {
                    startLocationUpdates()
                }
            } catch (e: Exception) {
                Log.e("HazardService", "Init failed: ${e.message}")
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (userProfile?.role != "popt") {
                    for (location in result.locations) {
                        checkGeofenceForFarmer(location.latitude, location.longitude)
                    }
                }
            }
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
            message = "Memantau lokasi di latar belakang...",
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

        return START_STICKY
    }

    private suspend fun setupPoptRealtimeListener() {
        try {
            realtimeChannel = supabase.realtime.channel("public-reports-popt")
            val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "reports"
            }
            realtimeChannel!!.subscribe()
            changeFlow.collect { action ->
                val record = action.record
                val incomingDistrict = record["district"]?.jsonPrimitive?.contentOrNull
                val myWkpp = userProfile?.wkpp_kecamatan ?: emptyList()
                val isRelevant = myWkpp.any { it.equals(incomingDistrict, ignoreCase = true) }
                if (isRelevant) {
                    val label = record["ai_label"]?.jsonPrimitive?.contentOrNull ?: "Hama"
                    sendTargetedNotification(label, incomingDistrict ?: "-")
                }
            }
        } catch (e: Exception) { Log.e("Hazard", e.message.toString()) }
    }

    private fun checkGeofenceForFarmer(userLat: Double, userLon: Double) {
        if (hotspots.isEmpty()) return

        var minDistance = Double.MAX_VALUE
        for (spot in hotspots) {
            val dist = LocationUtils.calculateDistance(userLat, userLon, spot.lat, spot.lon)
            if (dist < minDistance) minDistance = dist
        }

        val isDanger = minDistance <= 300.0
        hazardRepository.setDangerStatus(isDanger, minDistance)

        when {
            minDistance <= 20.0 -> {
                if (!isDangerNotified) {
                    sendAlertNotification("BAHAYA! HAMA SANGAT DEKAT!", "Jarak: ${minDistance.toInt()}m.", true)
                    isDangerNotified = true
                    isWarningNotified = true
                }
            }
            minDistance <= 300.0 -> {
                if (!isWarningNotified) {
                    sendAlertNotification("Memasuki Area Waspada", "Hama dalam radius 300m.", false)
                    isWarningNotified = true
                    isDangerNotified = false
                }
            }
            else -> {
                isDangerNotified = false
                isWarningNotified = false
            }
        }
    }

    private fun sendTargetedNotification(label: String, district: String) {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra("navigate_to", "popt_reports") }
        val pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        NotificationHelper.showNotification(this, "Laporan Baru: $label", "Di $district", CHANNEL_ID_ALERT, "Alert", pIntent, true)
    }

    private fun sendAlertNotification(title: String, msg: String, urgent: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        NotificationHelper.showNotification(this, title, msg, CHANNEL_ID_ALERT, "Alert", pIntent, urgent)
    }

    private fun startLocationUpdates() {
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}