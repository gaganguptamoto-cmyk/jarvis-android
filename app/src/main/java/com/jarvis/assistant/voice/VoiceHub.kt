package com.jarvis.assistant.voice

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jarvis.assistant.core.SettingsStore
import com.jarvis.assistant.ui.components.OrbState

class VoiceHub(context: Context, private val settings: SettingsStore) {
    var state by mutableStateOf(OrbState.Idle)
        private set

    var partialText by mutableStateOf("")
        private set

    var lastError by mutableStateOf<VoiceError?>(null)
        private set

    private val input = SpeechInput(context)
    private val output = SpeechOutput(context) { speaking ->
        if (speaking) {
            state = OrbState.Speaking
        } else if (state == OrbState.Speaking) {
            state = OrbState.Idle
        }
    }

    fun listen(onHeard: (String) -> Unit) {
        output.stop()
        lastError = null
        partialText = ""
        state = OrbState.Listening
        input.start(
            languageTag = settings.voiceLanguage,
            onPartial = { partialText = it },
            onResult = { text ->
                partialText = ""
                state = OrbState.Idle
                onHeard(text)
            },
            onFailure = { error ->
                partialText = ""
                state = OrbState.Idle
                lastError = error
            },
        )
    }

    fun stopListening() {
        input.finish()
    }

    fun speak(text: String) {
        lastError = null
        val result = output.speak(
            text = text,
            languageTag = settings.voiceLanguage,
            rate = settings.speechRate,
            volume = settings.speechVolume,
            pitch = settings.speechPitch,
            voiceName = settings.voiceName,
        )
        when (result) {
            SpeakResult.Ok -> state = OrbState.Speaking
            SpeakResult.NotReady -> {
                lastError = VoiceError.TtsNotReady
                state = OrbState.Idle
            }
            SpeakResult.LanguageMissing -> {
                lastError = VoiceError.TtsLanguageMissing
                state = OrbState.Idle
            }
        }
    }

    fun availableVoices(): List<String> = output.voicesFor(settings.voiceLanguage)

    fun stopSpeaking() {
        output.stop()
        state = OrbState.Idle
    }

    fun setThinking(on: Boolean) {
        if (on) {
            state = OrbState.Thinking
        } else if (state == OrbState.Thinking) {
            state = OrbState.Idle
        }
    }

    fun reportError(error: VoiceError) {
        lastError = error
    }

    fun stopAll() {
        input.release()
        output.stop()
        partialText = ""
        state = OrbState.Idle
    }

    fun shutdown() {
        input.release()
        output.shutdown()
    }
}
