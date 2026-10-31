package com.jarvis.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
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

    var handsFree by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var handler: ((String) -> Unit)? = null
    private var silentRounds = 0
    private val relisten = Runnable { relistenNow() }

    private val input = SpeechInput(context)
    private val output = SpeechOutput(context) { speaking ->
        if (speaking) {
            state = OrbState.Speaking
        } else if (state == OrbState.Speaking) {
            state = OrbState.Idle
            scheduleRelisten(600)
        }
    }

    /** One-shot listen (old behaviour). */
    fun listen(onHeard: (String) -> Unit) {
        handsFree = false
        main.removeCallbacks(relisten)
        listenOnce(onHeard)
    }

    /** Listen, auto-submit on silence, reply, then listen again until stopped. */
    fun startHandsFree(onHeard: (String) -> Unit) {
        handler = onHeard
        silentRounds = 0
        handsFree = true
        main.removeCallbacks(relisten)
        listenOnce { heard(it) }
    }

    private fun heard(text: String) {
        silentRounds = 0
        val t = text.trim().lowercase().trimEnd('.', '!', '?', '।').trim()
        if (t in EXIT) {
            handsFree = false
            handler = null
            return
        }
        handler?.invoke(text)
        scheduleRelisten(900)
    }

    private fun listenOnce(onHeard: (String) -> Unit) {
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
                onListenFailed(error)
            },
        )
    }

    private fun onListenFailed(error: VoiceError) {
        if (!handsFree) return
        if (error == VoiceError.NoMatch) {
            silentRounds++
            if (silentRounds >= 2) {
                handsFree = false
                lastError = null
            } else {
                lastError = null
                scheduleRelisten(300)
            }
        } else {
            handsFree = false
        }
    }

    private fun relistenNow() {
        if (!handsFree || handler == null) return
        if (state != OrbState.Idle) return
        listenOnce { heard(it) }
    }

    private fun scheduleRelisten(delayMs: Long) {
        if (!handsFree) return
        main.removeCallbacks(relisten)
        main.postDelayed(relisten, delayMs)
    }

    fun stopListening() {
        handsFree = false
        main.removeCallbacks(relisten)
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
                scheduleRelisten(600)
            }
            SpeakResult.LanguageMissing -> {
                lastError = VoiceError.TtsLanguageMissing
                state = OrbState.Idle
                scheduleRelisten(600)
            }
        }
    }

    fun availableVoices(): List<String> = output.voicesFor(settings.voiceLanguage)

    fun stopSpeaking() {
        handsFree = false
        main.removeCallbacks(relisten)
        output.stop()
        state = OrbState.Idle
    }

    fun setThinking(on: Boolean) {
        if (on) {
            state = OrbState.Thinking
        } else if (state == OrbState.Thinking) {
            state = OrbState.Idle
            scheduleRelisten(500)
        }
    }

    fun reportError(error: VoiceError) {
        lastError = error
    }

    fun stopAll() {
        handsFree = false
        main.removeCallbacks(relisten)
        input.release()
        output.stop()
        partialText = ""
        state = OrbState.Idle
    }

    fun shutdown() {
        handsFree = false
        main.removeCallbacks(relisten)
        input.release()
        output.shutdown()
    }

    private companion object {
        val EXIT = setOf(
            "stop", "bas", "bas karo", "band karo", "band", "stop listening",
            "बस", "बस करो", "बंद करो", "बंद", "स्टॉप",
        )
    }
}
