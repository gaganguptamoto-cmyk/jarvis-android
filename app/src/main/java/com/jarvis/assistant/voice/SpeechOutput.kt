package com.jarvis.assistant.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class SpeechOutput(
    context: Context,
    private val onSpeaking: (Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    main.post { onSpeaking(true) }
                }

                override fun onDone(utteranceId: String?) {
                    main.post { onSpeaking(false) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { onSpeaking(false) }
                }
            })
        }
    }

    fun speak(
        text: String,
        languageTag: String,
        rate: Float,
        volume: Float,
        pitch: Float,
        voiceName: String,
    ): SpeakResult {
        val engine = tts
        if (!ready || engine == null) return SpeakResult.NotReady
        val result = engine.setLanguage(Locale.forLanguageTag(languageTag))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return SpeakResult.LanguageMissing
        }
        if (voiceName.isNotBlank()) {
            try {
                engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.setVoice(it) }
            } catch (e: Exception) {
                // keep the automatic voice if the chosen one cannot be loaded
            }
        }
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        engine.speak(clean(text), TextToSpeech.QUEUE_FLUSH, params, "jarvis-" + System.nanoTime())
        return SpeakResult.Ok
    }

    fun voicesFor(languageTag: String): List<String> {
        val engine = tts
        if (!ready || engine == null) return emptyList()
        val wanted = Locale.forLanguageTag(languageTag)
        return try {
            engine.voices.orEmpty()
                .filter { it.locale.language == wanted.language }
                .filter { wanted.language == "hi" || it.locale.country == wanted.country }
                .filter { !it.isNetworkConnectionRequired }
                .sortedByDescending { it.quality }
                .map { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun stop() {
        tts?.stop()
        onSpeaking(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun clean(text: String): String {
        return text
            .replace(Regex("[*#_>~|\u0060]"), " ")
            .replace(Regex("[\\p{So}\\p{Cs}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3500)
    }
}
