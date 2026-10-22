@file:Suppress("DEPRECATION")
package amalia.skripsi.deteksipadi.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp

val supabase = createSupabaseClient(
    supabaseUrl = "https://rhlixmoadeexgvrbxkmo.supabase.co",
    supabaseKey = "sb_publishable_dV8oRP92tnXhqDantHWkkg_RTgO3V4P"
) {
    httpEngine = OkHttp.create()
    install(Postgrest)
    install(Storage)
    install(Auth)
    install(Realtime)
}

// Gunakan computed fields `lat` dan `lon` dari fungsi Postgres agar koordinat tidak bergantung pada format serialisasi geography PostGIS.

const val LAPORAN_COLUMNS = "id, petani_id, foto_url, label_ai, confidence, status, prioritas, termasuk_cluster, lat, lon, alamat_lengkap, created_at, deskripsi_gejala, instruksi_popt, radius, jenis_pelaporan, kecamatan_id, alasan_penolakan, alasan_kunjungan, label_koreksi_popt"

// View peta memang menyediakan lat/lon hasil ekstraksi dari `lokasi`.

const val LAPORAN_MAP_COLUMNS = "id, petani_id, foto_url, label_ai, confidence, status, prioritas, termasuk_cluster, created_at, deskripsi_gejala, instruksi_popt, kecamatan_id, alasan_penolakan, alasan_kunjungan, radius, jenis_pelaporan, lat, lon, alamat_lengkap, label_koreksi_popt"
