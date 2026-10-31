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

    var geminiModel by mutableStateOf(
        prefs.getString("gemini_model", DEFAULT_MODEL) ?: DEFAULT_MODEL,
    )
        private set

    fun updateDarkTheme(value: Boolean) {
        darkTheme = value
        prefs.edit().putBoolean("dark_theme", value).apply()
    }

    fun updateAnimations(value: Boolean) {
        animations = value
        prefs.edit().putBoolean("animations", value).apply()
    }

    fun updateGeminiModel(value: String) {
        val name = value.trim().ifEmpty { DEFAULT_MODEL }
        geminiModel = name
        prefs.edit().putString("gemini_model", name).apply()
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
