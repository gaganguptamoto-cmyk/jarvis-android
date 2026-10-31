package com.jarvis.assistant.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)

    var darkTheme by mutableStateOf(prefs.getBoolean("dark_theme", true))
        private set

    var animations by mutableStateOf(prefs.getBoolean("animations", true))
        private set

    fun updateDarkTheme(value: Boolean) {
        darkTheme = value
        prefs.edit().putBoolean("dark_theme", value).apply()
    }

    fun updateAnimations(value: Boolean) {
        animations = value
        prefs.edit().putBoolean("animations", value).apply()
    }
}
