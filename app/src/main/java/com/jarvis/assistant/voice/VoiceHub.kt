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

    var trace: ((String) -> Unit)? = null
    private fun trc(msg: String) {
        trace?.invoke(msg)
    }

    private val main = Handler(Looper.getMainLooper())
    private var handler: ((String) -> Unit)? = null
    private var silentRounds = 0
    private var errorRounds = 0
    private var idleTicks = 0
    private var speakingTicks = 0
    private val relisten = Runnable { relistenNow() }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!handsFree) {
                idleTicks = 0
                speakingTicks = 0
                return
            }
            if (state == OrbState.Idle) {
                speakingTicks = 0
                idleTicks++
                if (idleTicks >= 2) {
                    idleTicks = 0
                    relistenNow()
                }
            } else if (state == OrbState.Speaking) {
                idleTicks = 0
                speakingTicks++
                if (speakingTicks >= 30) {
                    speakingTicks = 0
                    state = OrbState.Idle
                }
            } else {
                idleTicks = 0
                speakingTicks = 0
            }
            main.postDelayed(this, 1500)
        }
    }

    private val input = SpeechInput(context)
    private val output = SpeechOutput(context) { speaking ->
        if (speaking) {
            state = OrbState.Speaking
        } else if (state == OrbState.Speaking) {
            state = OrbState.Idle
            scheduleRelisten(600)
        }
    }

    fun listen(onHeard: (String) -> Unit) {
        handsFree = false
        main.removeCallbacks(relisten)
        listenOnce(onHeard)
    }

    fun startHandsFree(onHeard: (String) -> Unit) {
        handler = onHeard
        silentRounds = 0
        errorRounds = 0
        idleTicks = 0
        speakingTicks = 0
        handsFree = true
        main.removeCallbacks(relisten)
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, 1500)
        listenOnce { heard(it) }
    }

    private fun heard(text: String) {
        silentRounds = 0
        errorRounds = 0
        val t = text.trim().lowercase().trimEnd('.', '!', '?', '।').trim()
        if (t in EXIT) {
            handsFree = false
            handler = null
            trc("exit word")
            return
        }
        trc("heard: $text")
        handler?.invoke(text)
        scheduleRelisten(900)
    }

    private fun listenOnce(onHeard: (String) -> Unit) {
        output.stop()
        lastError = null
        partialText = ""
        state = OrbState.Listening
        trc("listening")
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
                trc("error: $error")
                onListenFailed(error)
            },
        )
    }

    private fun onListenFailed(error: VoiceError) {
        if (!handsFree) return
        when (error) {
            VoiceError.NoMatch -> {
                silentRounds++
                if (silentRounds >= 1000000) {
                    handsFree = false
                    trc("stopped: silence")
                } else {
                    lastError = null
                    scheduleRelisten(300)
                }
            }
            VoiceError.Busy, VoiceError.Other -> {
                errorRounds++
                if (errorRounds >= 1000000) {
                    handsFree = false
                    trc("stopped: errors")
                } else {
                    lastError = null
                    scheduleRelisten(900)
                }
            }
            else -> {
                handsFree = false
                trc("stopped: $error")
            }
        }
    }

    private fun relistenNow() {
        if (!handsFree || handler == null) return
        if (state != OrbState.Idle) {
            trc("relisten skipped: $state")
            return
        }
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

    fun speakNow(text: String) {
        if (state == OrbState.Listening) {
            input.release()
            state = OrbState.Idle
        }
        speak(text)
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
        main.removeCallbacks(watchdog)
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
