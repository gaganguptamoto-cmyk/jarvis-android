package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.R
import com.jarvis.assistant.core.SettingsStore
import com.jarvis.assistant.voice.VoiceHub
import com.jarvis.assistant.voice.voiceErrorRes
import kotlinx.coroutines.delay

@Composable
fun VoiceSettingsSection(store: SettingsStore, hub: VoiceHub) {
    val isHindi = store.voiceLanguage.startsWith("hi")
    val testHi = stringResource(R.string.voice_test_hi)
    val testEn = stringResource(R.string.voice_test_en)
    val testText = if (isHindi) testHi else testEn
    var voices by remember { mutableStateOf(hub.availableVoices()) }

    LaunchedEffect(store.voiceLanguage) {
        delay(600)
        voices = hub.availableVoices()
    }

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.voice_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.voice_language),
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = isHindi,
                onClick = { store.updateVoiceLanguage("hi-IN") },
                label = { Text(stringResource(R.string.voice_lang_hi)) },
            )
            FilterChip(
                selected = !isHindi,
                onClick = { store.updateVoiceLanguage("en-IN") },
                label = { Text(stringResource(R.string.voice_lang_en)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.voice_speak_replies),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(
                checked = store.speakReplies,
                onCheckedChange = { store.updateSpeakReplies(it) },
            )
        }
        Text(
            text = stringResource(R.string.voice_speed, "%.1f".format(store.speechRate)),
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Slider(
            value = store.speechRate,
            onValueChange = { store.updateSpeechRate(it) },
            valueRange = 0.5f..1.5f,
        )
        Text(
            text = stringResource(R.string.voice_pitch, "%.2f".format(store.speechPitch)),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Slider(
            value = store.speechPitch,
            onValueChange = { store.updateSpeechPitch(it) },
            valueRange = 0.6f..1.2f,
        )
        Text(
            text = stringResource(R.string.voice_volume, "%.1f".format(store.speechVolume)),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Slider(
            value = store.speechVolume,
            onValueChange = { store.updateSpeechVolume(it) },
            valueRange = 0.2f..1.0f,
        )

        Text(
            text = stringResource(R.string.voice_pick),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        VoiceRow(
            label = stringResource(R.string.voice_auto),
            selected = store.voiceName.isBlank(),
            onClick = { store.updateVoiceName("") },
        )
        voices.forEachIndexed { index, name ->
            VoiceRow(
                label = "Voice " + (index + 1) + "  (" + name + ")",
                selected = store.voiceName == name,
                onClick = {
                    store.updateVoiceName(name)
                    hub.speak(testText)
                },
            )
        }
        TextButton(onClick = { voices = hub.availableVoices() }) {
            Text(stringResource(R.string.voice_refresh))
        }
        Text(
            text = stringResource(R.string.voice_pick_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = { hub.speak(testText) },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.voice_test))
        }
        hub.lastError?.let {
            Text(
                text = stringResource(voiceErrorRes(it)),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, color = MaterialTheme.colorScheme.onBackground)
    }
}
