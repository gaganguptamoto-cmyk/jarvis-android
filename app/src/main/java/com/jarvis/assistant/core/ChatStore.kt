package com.jarvis.assistant.core

import android.content.Context
import com.jarvis.assistant.ai.AiMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatStore(context: Context) {
    private val file = File(context.filesDir, "chat_history.json")

    fun load(): List<AiMessage> {
        return try {
            if (!file.exists()) {
                emptyList()
            } else {
                val arr = JSONArray(file.readText())
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    AiMessage(o.getBoolean("user"), o.getString("text"))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(messages: List<AiMessage>) {
        try {
            val arr = JSONArray()
            messages.forEach {
                arr.put(JSONObject().put("user", it.fromUser).put("text", it.text))
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // history is a convenience, a failed save must not crash the app
        }
    }

    fun clear() {
        file.delete()
    }
}
