package amalia.skripsi.deteksipadi.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import amalia.skripsi.deteksipadi.ui.screens.general.peta.LocationUtils

@Singleton
class HazardRepository @Inject constructor() {
    private val _isDanger = MutableStateFlow(false)
    val isDanger = _isDanger.asStateFlow()

    private val _currentDistance = MutableStateFlow(0.0)
    val currentDistance = _currentDistance.asStateFlow()

    fun updateLocation(userLat: Double, userLon: Double, hotspots: List<LaporanDto>) {
        if (hotspots.isEmpty()) {
            _isDanger.value = false
            _currentDistance.value = 0.0
            return
        }

        var minDistance = Double.MAX_VALUE
        var dangerDetected = false

        for (spot in hotspots) {
            val distance = LocationUtils.calculateDistance(userLat, userLon, spot.lat, spot.lon)

            if (distance < minDistance) {
                minDistance = distance
            }

            if (distance <= 300.0) {
                dangerDetected = true
            }
        }

        _isDanger.value = dangerDetected
        _currentDistance.value = minDistance
    }

    fun setDangerStatus(danger: Boolean, distance: Double) {
        _isDanger.value = danger
        _currentDistance.value = distance
    }
}