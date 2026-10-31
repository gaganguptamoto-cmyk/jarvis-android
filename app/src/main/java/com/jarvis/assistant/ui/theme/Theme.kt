package com.jarvis.assistant.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFF2979FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0F7FA),
    surface = Color(0xFF0A0F1A),
    onSurface = Color(0xFFE0F7FA),
    surfaceVariant = Color(0xFF111B2B),
    onSurfaceVariant = Color(0xFFB2EBF2),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C80),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF1565C0),
    background = Color(0xFFF2FBFD),
    onBackground = Color(0xFF0A1A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A1A1F),
    surfaceVariant = Color(0xFFD6EEF3),
    onSurfaceVariant = Color(0xFF00363F),
)

@Composable
fun JarvisTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
