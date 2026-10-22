@file:Suppress("DEPRECATION")
package amalia.skripsi.deteksipadi.data

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double

suspend fun fetchMasterPoktan(): List<MasterPoktanDto> {
    return try {
        supabase.from("master_poktan").select().decodeList<MasterPoktanDto>()
    } catch (e: Exception) {
        emptyList()
    }
}


suspend fun fetchSemuaKecamatan(): List<KecamatanDto> {
    // Baca view GeoJSON, bukan tabel kecamatan langsung.
    // Biarkan exception diteruskan agar ViewModel dapat menampilkan error yang nyata.
    return supabase.from("vw_kecamatan_geojson")
        .select(columns = Columns.raw("id, nama_kecamatan, polygon_geojson"))
        .decodeList<KecamatanDto>()
}


fun parseGeoJsonToLatLng(geoJsonString: String?): List<List<LatLng>> {
    if (geoJsonString.isNullOrBlank()) return emptyList()
    val polygons = mutableListOf<List<LatLng>>()
    try {
        val geoJson = Json.parseToJsonElement(geoJsonString).jsonObject
        val type = geoJson["type"]?.jsonPrimitive?.content
        val coordinates = geoJson["coordinates"]?.jsonArray ?: return emptyList()

        when (type) {
            "MultiPolygon" -> {
                for (multiPolygonElement in coordinates) {
                    val polygonArray = multiPolygonElement.jsonArray
                    if (polygonArray.isNotEmpty()) {
                        val outerRing = polygonArray[0].jsonArray
                        val latLngs = outerRing.mapNotNull { coord ->
                            val pt = coord.jsonArray
                            if (pt.size >= 2) LatLng(pt[1].jsonPrimitive.double, pt[0].jsonPrimitive.double) else null
                        }
                        if (latLngs.size >= 3) polygons.add(latLngs)
                    }
                }
            }
            "Polygon" -> {
                if (coordinates.isNotEmpty()) {
                    val outerRing = coordinates[0].jsonArray
                    val latLngs = outerRing.mapNotNull { coord ->
                        val pt = coord.jsonArray
                        if (pt.size >= 2) LatLng(pt[1].jsonPrimitive.double, pt[0].jsonPrimitive.double) else null
                    }
                    if (latLngs.size >= 3) polygons.add(latLngs)
                }
            }
        }
    } catch (e: Exception) {
        Log.w("KECAMATAN_GEOJSON", "Gagal parsing GeoJSON kecamatan: ${e.message}")
    }
    return polygons
}


suspend fun fetchMasterHama(): List<MasterHamaDto> {
    return try {
        supabase.from("master_hama")
            .select(columns = Columns.raw("*, informasi_hama(*)"))
            .decodeList<MasterHamaDto>()
    } catch (e: Exception) {
        emptyList()
    }
}
