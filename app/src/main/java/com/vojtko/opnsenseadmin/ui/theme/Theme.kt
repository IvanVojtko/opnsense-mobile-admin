package com.vojtko.opnsenseadmin.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = HarborBlue,
    secondary = Seafoam,
    tertiary = SignalOrange,
    background = SlateNight,
    surface = SlatePanel,
    surfaceVariant = SlatePanelAlt,
    onPrimary = Cloud,
    onSecondary = SlateNight,
    onTertiary = SlateNight,
    onBackground = Cloud,
    onSurface = Cloud,
    onSurfaceVariant = MistSoft,
    outline = MistSoft
)

private val LightColorScheme = lightColorScheme(
    primary = HarborBlueDark,
    secondary = HarborBlue,
    tertiary = SignalOrange,
    background = Cloud,
    surface = Color.White,
    surfaceVariant = Mist,
    onPrimary = Cloud,
    onSecondary = Cloud,
    onTertiary = SlateNight,
    onBackground = SlateNight,
    onSurface = SlateNight,
    onSurfaceVariant = SlatePanelAlt,
    outline = MistSoft
)

@Composable
fun OPNSenseAdminTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
