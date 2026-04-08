package amalia.skripsi.deteksipadi.ui.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class FontSettingsManager(private val context: Context) {
    companion object {
        val FONT_SIZE_KEY = stringPreferencesKey("font_size")
    }

    // Default ke "Sedang"
    val fontSizeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[FONT_SIZE_KEY] ?: "Sedang"
    }

    suspend fun saveFontSize(size: String) {
        context.dataStore.edit { prefs ->
            prefs[FONT_SIZE_KEY] = size
        }
    }
}