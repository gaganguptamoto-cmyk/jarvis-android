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

    var voiceLanguage by mutableStateOf(prefs.getString("voice_lang", "hi-IN") ?: "hi-IN")
        private set

    var speechRate by mutableStateOf(prefs.getFloat("speech_rate", 1.0f))
        private set

    var speechVolume by mutableStateOf(prefs.getFloat("speech_volume", 1.0f))
        private set

    var speakReplies by mutableStateOf(prefs.getBoolean("speak_replies", true))
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

    fun updateVoiceLanguage(value: String) {
        voiceLanguage = value
        prefs.edit().putString("voice_lang", value).apply()
    }

    fun updateSpeechRate(value: Float) {
        speechRate = value
        prefs.edit().putFloat("speech_rate", value).apply()
    }

    fun updateSpeechVolume(value: Float) {
        speechVolume = value
        prefs.edit().putFloat("speech_volume", value).apply()
    }

    fun updateSpeakReplies(value: Boolean) {
        speakReplies = value
        prefs.edit().putBoolean("speak_replies", value).apply()
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
