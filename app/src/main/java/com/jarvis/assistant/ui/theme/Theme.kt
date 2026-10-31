package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFF2979FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0F7FA),
    surface = Color(0xFF0A0F1A),
    onSurface = Color(0xFFE0F7FA),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisColors, content = content)
}
