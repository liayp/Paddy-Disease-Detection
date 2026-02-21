package amalia.skripsi.deteksipadi.data

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HazardRepository @Inject constructor() {
    private val _isDanger = MutableStateFlow(false)
    val isDanger = _isDanger.asStateFlow()

    private val _currentDistance = MutableStateFlow(0.0)
    val currentDistance = _currentDistance.asStateFlow()

    // Fungsi pusat untuk menghitung bahaya
    fun updateLocation(userLat: Double, userLon: Double, hotspots: List<HotspotDto>) {
        var minDistance = Double.MAX_VALUE
        var dangerDetected = false

        for (spot in hotspots) {
            val results = FloatArray(1)
            Location.distanceBetween(userLat, userLon, spot.lat, spot.lon, results)
            val distance = results[0].toDouble()

            if (distance < minDistance) {
                minDistance = distance
            }

            if (distance <= 300.0) {
                dangerDetected = true
            }
        }

        _isDanger.value = dangerDetected
        _currentDistance.value = if (hotspots.isEmpty()) 0.0 else minDistance
    }

    // Tetap sediakan ini untuk diupdate oleh Background Service jika perlu
    fun setDangerStatus(isDanger: Boolean, distance: Double) {
        _isDanger.value = isDanger
        _currentDistance.value = distance
    }
}