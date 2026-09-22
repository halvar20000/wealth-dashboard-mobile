package fr.smarthomeworld.wealth.ui

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

/** The dashboard's own dark blue, with the phone's wallpaper colours
 *  where Android offers them — a household app should look like the
 *  phone it lives on. */
private val Night = darkColorScheme(
    primary = Color(0xFF5B9DFF),
    secondary = Color(0xFFA78BFA),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF0B1020),
    surface = Color(0xFF121A2E),
    surfaceVariant = Color(0xFF1A2336),
)

private val Day = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF7C3AED),
    tertiary = Color(0xFF059669),
)

val Gain = Color(0xFF34D399)
val Loss = Color(0xFFF87171)

@Composable
fun WealthTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Night
        else -> Day
    }
    MaterialTheme(colorScheme = colors, content = content)
}
