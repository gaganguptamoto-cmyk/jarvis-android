package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechInput(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var session = 0

    fun start(
        languageTag: String,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onFailure: (VoiceError) -> Unit,
    ) {
        release()
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onFailure(VoiceError.NotAvailable)
            return
        }
        val mySession = ++session
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (mySession != session) return
                val text = firstText(partialResults)
                if (text.isNotBlank()) onPartial(text)
            }

            override fun onResults(results: Bundle?) {
                if (mySession != session) return
                val text = firstText(results)
                if (text.isBlank()) onFailure(VoiceError.NoMatch) else onResult(text)
            }

            override fun onError(error: Int) {
                if (mySession != session) return
                onFailure(mapError(error))
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        r.startListening(intent)
    }

    fun finish() {
        recognizer?.stopListening()
    }

    fun release() {
        session++
        recognizer?.destroy()
        recognizer = null
    }

    private fun firstText(bundle: Bundle?): String {
        return bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
    }

    private fun mapError(code: Int): VoiceError = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NoMatch
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> VoiceError.Network
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.Busy
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NoPermission
        else -> VoiceError.Other
    }
}
