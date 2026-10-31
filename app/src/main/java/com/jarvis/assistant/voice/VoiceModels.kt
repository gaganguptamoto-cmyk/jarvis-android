package com.jarvis.assistant.voice

import com.jarvis.assistant.R

enum class VoiceError {
    NoPermission, NotAvailable, NoMatch, Network, Busy, Other, TtsNotReady, TtsLanguageMissing
}

enum class SpeakResult { Ok, NotReady, LanguageMissing }

fun voiceErrorRes(error: VoiceError): Int = when (error) {
    VoiceError.NoPermission -> R.string.voice_err_no_permission
    VoiceError.NotAvailable -> R.string.voice_err_not_available
    VoiceError.NoMatch -> R.string.voice_err_no_match
    VoiceError.Network -> R.string.voice_err_network
    VoiceError.Busy -> R.string.voice_err_busy
    VoiceError.Other -> R.string.voice_err_other
    VoiceError.TtsNotReady -> R.string.voice_err_tts_not_ready
    VoiceError.TtsLanguageMissing -> R.string.voice_err_tts_language
}
