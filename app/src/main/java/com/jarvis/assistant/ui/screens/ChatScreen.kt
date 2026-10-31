package com.jarvis.assistant.ui.screens

import com.jarvis.assistant.automation.CommandRouter
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.ai.AiError
import com.jarvis.assistant.ai.AiMessage
import com.jarvis.assistant.ai.AiResult
import com.jarvis.assistant.ai.GeminiProvider
import com.jarvis.assistant.core.ChatStore
import com.jarvis.assistant.core.SecureKeyStore
import com.jarvis.assistant.core.SettingsStore
import com.jarvis.assistant.ui.components.OrbState
import com.jarvis.assistant.voice.VoiceError
import com.jarvis.assistant.voice.VoiceHub
import com.jarvis.assistant.voice.voiceErrorRes
import kotlinx.coroutines.launch

private const val HINT_HI =
    "\n\n[Voice reply: keep it short and natural. Write Hindi words in Devanagari and " +
        "English words in Roman letters. No markdown, no emojis, no bullet symbols.]"
private const val HINT_EN =
    "\n\n[Voice reply: keep it short and natural, in simple English. " +
        "No markdown, no emojis, no bullet symbols.]"

private data class UiMessage(
    val text: String,
    val fromUser: Boolean,
    val error: AiError? = null,
    val detail: String = "",
)

private fun errorRes(error: AiError): Int = when (error) {
    AiError.NoKey -> R.string.ai_err_no_key
    AiError.NoInternet -> R.string.ai_err_no_internet
    AiError.InvalidKey -> R.string.ai_err_invalid_key
    AiError.RateLimit -> R.string.ai_err_rate_limit
    AiError.Timeout -> R.string.ai_err_timeout
    AiError.Server -> R.string.ai_err_server
    AiError.BadModel -> R.string.ai_err_bad_model
    AiError.Empty -> R.string.ai_err_empty
    AiError.Unknown -> R.string.ai_err_unknown
}

@Composable
fun ChatScreen(store: SettingsStore, hub: VoiceHub) {
    val context = LocalContext.current
    val chatStore = remember { ChatStore(context.applicationContext) }
    val keyStore = remember { SecureKeyStore(context.applicationContext) }
    val provider = remember {
        GeminiProvider(apiKey = { keyStore.loadApiKey() }, model = { store.geminiModel })
    }
    val messages = remember {
        mutableStateListOf<UiMessage>().also { list ->
            chatStore.load().forEach { list.add(UiMessage(it.text, it.fromUser)) }
        }
    }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send(text: String, spoken: Boolean) {
        val clean = text.trim()
        if (clean.isEmpty() || loading) return
        messages.add(UiMessage(clean, true))
    val local = CommandRouter.handle(context, clean)
    if (local != null) {
        messages.add(UiMessage(local, false))
        if (spoken && store.speakReplies) hub.speak(local)
        return
    }
        loading = true
        hub.setThinking(true)
        val base = messages
            .filter { it.error == null }
            .takeLast(20)
            .map { AiMessage(it.fromUser, it.text) }
            .dropWhile { !it.fromUser }
        val history = if (spoken && base.isNotEmpty()) {
            val hint = if (store.voiceLanguage.startsWith("hi")) HINT_HI else HINT_EN
            base.dropLast(1) + AiMessage(true, base.last().text + hint)
        } else {
            base
        }
        scope.launch {
            when (val result = provider.chat(history)) {
                is AiResult.Success -> {
                    messages.add(UiMessage(result.text, false))
                    if (spoken && store.speakReplies) {
                        hub.speak(result.text)
                    } else {
                        hub.setThinking(false)
                    }
                }
                is AiResult.Failure -> {
                    messages.add(UiMessage("", false, result.kind, result.detail))
                    hub.setThinking(false)
                }
            }
            chatStore.save(
                messages.filter { it.error == null }.map { AiMessage(it.fromUser, it.text) },
            )
            loading = false
        }
    }

    fun startVoice() {
        hub.startHandsFree { heard -> send(heard, true) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoice() else hub.reportError(VoiceError.NoPermission)
    }

    fun onMicClick() {
        when (hub.state) {
            OrbState.Listening -> hub.stopListening()
            OrbState.Speaking -> hub.stopSpeaking()
            else -> {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    startVoice()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    val micLabel = when (hub.state) {
        OrbState.Listening -> R.string.chat_mic_done
        OrbState.Speaking -> R.string.chat_stop_speaking
        else -> R.string.chat_mic
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (messages.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    messages.clear()
                    chatStore.clear()
                }) {
                    Text(stringResource(R.string.chat_clear))
                }
            }
        }
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.chat_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { MessageBubble(it) }
            }
        }
        if (hub.state == OrbState.Listening) {
            Text(
                text = if (hub.partialText.isBlank()) {
                    stringResource(R.string.chat_listening_wait)
                } else {
                    hub.partialText
                },
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (loading) {
            Text(
                text = stringResource(R.string.chat_thinking),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        hub.lastError?.let {
            Text(
                text = stringResource(voiceErrorRes(it)),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_hint)) },
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val typed = input
                    input = ""
                    send(typed, false)
                },
                enabled = !loading,
            ) {
                Text(stringResource(R.string.chat_send))
            }
        }
        Button(
            onClick = { onMicClick() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(stringResource(micLabel))
        }
    }
}

@Composable
private fun MessageBubble(message: UiMessage) {
    val isError = message.error != null
    val text = if (message.error != null) {
        val base = stringResource(errorRes(message.error))
        if (message.detail.isBlank()) base else base + "\n(" + message.detail + ")"
    } else {
        message.text
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                message.fromUser -> MaterialTheme.colorScheme.primary
                isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = when {
                    message.fromUser -> MaterialTheme.colorScheme.onPrimary
                    isError -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
