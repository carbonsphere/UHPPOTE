package com.carbonsphere.uhppote.ui.theme

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

// Teal from the Windows app icon.
private val Teal = Color(0xFF0F6E6E)
private val TealDark = Color(0xFF0B4F4F)
private val TealLight = Color(0xFF7FD3D0)

private val LightColors = lightColorScheme(primary = Teal, secondary = TealDark, tertiary = Color(0xFF2E7D32))
private val DarkColors = darkColorScheme(primary = TealLight, secondary = Color(0xFF9CCFCF), tertiary = Color(0xFF81C784))

/** App theme: follows the system dark/light setting, dynamic colour on Android 12+. */
@Composable
fun UhppoteTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
