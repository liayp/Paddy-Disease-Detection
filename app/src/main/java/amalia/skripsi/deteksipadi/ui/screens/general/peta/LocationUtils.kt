package amalia.skripsi.deteksipadi.ui.screens.general.peta

import amalia.skripsi.deteksipadi.data.HotspotDto
import kotlin.math.*

object LocationUtils {

    // Rumus Haversine: Menghitung jarak akurat dalam METER antara dua koordinat bumi
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // Radius bumi (meter)
        val phi1 = lat1 * PI / 180
        val phi2 = lat2 * PI / 180
        val deltaPhi = (lat2 - lat1) * PI / 180
        val deltaLambda = (lon2 - lon1) * PI / 180

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c // Hasil dalam meter
    }

    // Logic Geofencing: Cek apakah user ada di radius 300m dari SALAH SATU titik hama
    fun isUserInDangerZone(userLat: Double, userLon: Double, hotspots: List<HotspotDto>): Boolean {
        for (spot in hotspots) {
            val distance = calculateDistance(userLat, userLon, spot.lat, spot.lon)
            if (distance <= 300.0) { // Radius Bahaya 300 Meter
                return true
            }
        }
        return false
    }
}