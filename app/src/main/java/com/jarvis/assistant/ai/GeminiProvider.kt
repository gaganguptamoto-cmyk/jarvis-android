package com.jarvis.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class GeminiProvider(
    val apiKey: () -> String?,
    val model: () -> String,
) : AiProvider {

    override suspend fun chat(history: List<AiMessage>): AiResult = withContext(Dispatchers.IO) {
        val key = apiKey()
        if (key.isNullOrBlank()) {
            AiResult.Failure(AiError.NoKey)
        } else {
            request(key, history)
        }
    }

    private fun request(key: String, history: List<AiMessage>): AiResult {
        var conn: HttpURLConnection? = null
        return try {
            val body = JSONObject()
            body.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT + EXTRA))),
            )
            val contents = JSONArray()
            history.forEach { m ->
                contents.put(
                    JSONObject()
                        .put("role", if (m.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", m.text))),
                )
            }
            body.put("contents", contents)

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${model().trim()}:generateContent")
            val c = url.openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = c.responseCode
            if (code in 200..299) {
                parse(c.inputStream.bufferedReader().use { it.readText() })
            } else {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                fail(code, err)
            }
        } catch (e: UnknownHostException) {
            AiResult.Failure(AiError.NoInternet)
        } catch (e: ConnectException) {
            AiResult.Failure(AiError.NoInternet)
        } catch (e: SocketTimeoutException) {
            AiResult.Failure(AiError.Timeout)
        } catch (e: Exception) {
            AiResult.Failure(AiError.Unknown, e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(raw: String): AiResult {
        val parts = JSONObject(raw)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text"))
            }
        }
        val text = sb.toString().trim()
        return if (text.isEmpty()) AiResult.Failure(AiError.Empty) else AiResult.Success(text)
    }

    private fun fail(code: Int, err: String): AiResult {
        val message = try {
            JSONObject(err).optJSONObject("error")?.optString("message").orEmpty().take(160)
        } catch (e: Exception) {
            ""
        }
        return when {
            code == 401 || code == 403 -> AiResult.Failure(AiError.InvalidKey, message)
            code == 400 && message.contains("API key", ignoreCase = true) ->
                AiResult.Failure(AiError.InvalidKey, message)
            code == 404 -> AiResult.Failure(AiError.BadModel, message)
            code == 429 -> AiResult.Failure(AiError.RateLimit, message)
            code >= 500 -> AiResult.Failure(AiError.Server, message)
            else -> AiResult.Failure(AiError.Unknown, "HTTP $code $message")
        }
    }

    private companion object {
        const val EXTRA = " You cannot control the phone yourself. Never say you opened an app, went home or pressed anything. If the user wants a phone action, tell them briefly to say a short command like 'YouTube kholo', 'home', 'back', 'scroll down' or 'Search dabao'."
        const val SYSTEM_PROMPT =
            "You are JARVIS, a helpful personal assistant on the user's Android phone. " +
                "Reply in the language the user writes in. If the user writes Hinglish " +
                "(Hindi in Roman script), reply in natural Hinglish. Keep answers short " +
                "and clear unless the user asks for detail. If you are not sure, say so."
    }
}
