package com.jarvis.assistant.ai

data class AiMessage(val fromUser: Boolean, val text: String)

enum class AiError {
    NoKey, NoInternet, InvalidKey, RateLimit, Timeout, Server, BadModel, Empty, Unknown
}

sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Failure(val kind: AiError, val detail: String = "") : AiResult()
}

interface AiProvider {
    suspend fun chat(history: List<AiMessage>): AiResult
}
