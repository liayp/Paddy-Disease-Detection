package amalia.skripsi.deteksipadi.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit

object LocationAccessPreference {
    private const val PREFS = "app_location_settings"
    private const val KEY_ENABLED = "location_usage_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, enabled)
        }
    }

    fun hasPermission(context: Context): Boolean =
        hasFinePermission(context) ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasFinePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun canUseLocation(context: Context): Boolean = isEnabled(context) && hasPermission(context)

    fun canUsePreciseLocation(context: Context): Boolean = isEnabled(context) && hasFinePermission(context)
}
