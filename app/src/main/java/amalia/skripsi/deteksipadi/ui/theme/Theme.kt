package amalia.skripsi.deteksipadi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val GreenPalette = Color(0xFF719D3D)  // Green (primary)
private val LightGreen = Color(0xFF8BC34A)    // Soft green (secondary)
private val DarkGreen = Color(0xFF388E3C)     // Stronger green (tertiary)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPalette,                  // Main color (primary)
    secondary = LightGreen,                   // Softer green (secondary)
    tertiary = DarkGreen,                     // Darker green (tertiary)
    background = Color(0xFF121212),           // Dark background
    surface = Color(0xFF1C1B1F),              // Dark surface
    onPrimary = Color.White,                  // Text color for primary
    onSecondary = Color.White,                // Text color for secondary
    onTertiary = Color.White,                 // Text color for tertiary
    onBackground = Color.White,               // Text color for background
    onSurface = Color.White                   // Text color for surface
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPalette,                   // Main color (primary)
    secondary = LightGreen,                    // Softer green (secondary)
    tertiary = DarkGreen,                      // Darker green (tertiary)
    background = Color(0xFFF4EFE3),           // Light background
    surface = Color(0xFFFFFFFF),              // White surface
    onPrimary = Color.Black,                  // Text color for primary
    onSecondary = Color.Black,                // Text color for secondary
    onTertiary = Color.Black,                 // Text color for tertiary
    onBackground = Color.Black,               // Text color for background
    onSurface = Color.Black                   // Text color for surface
)

@Composable
fun DeteksiPadiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme  // Dark mode color scheme
        else -> LightColorScheme     // Light mode color scheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
