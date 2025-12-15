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

private val Primary = Color(0xFF719D3C)
private val Background = Color(0xFFF4EFE3)
private val Surface = Color(0xFFD4E6D8)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,

    secondary = Surface,
    onSecondary = Color.Black,

    tertiary = Primary,
    onTertiary = Color.White,

    background = Background,
    onBackground = Color.Black,

    surface = Surface,
    onSurface = Color.Black,

    primaryContainer = Primary,
    onPrimaryContainer = Color.White,

    surfaceVariant = Surface,
    onSurfaceVariant = Color.Black
)


private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,

    secondary = Surface,
    onSecondary = Color.Black,

    tertiary = Primary,
    onTertiary = Color.White,

    background = Background,
    onBackground = Color.Black,

    surface = Surface,
    onSurface = Color.Black,

    primaryContainer = Primary,
    onPrimaryContainer = Color.White,

    surfaceVariant = Surface,
    onSurfaceVariant = Color.Black

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

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
