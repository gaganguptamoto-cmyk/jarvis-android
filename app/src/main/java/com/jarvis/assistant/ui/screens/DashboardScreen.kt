package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.R
import com.jarvis.assistant.core.SettingsStore
import com.jarvis.assistant.ui.components.JarvisOrb
import com.jarvis.assistant.voice.VoiceHub

@Composable
fun DashboardScreen(store: SettingsStore, hub: VoiceHub) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        JarvisOrb(state = hub.state, animated = store.animations)
        Text(
            text = stringResource(hub.state.labelRes),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (hub.partialText.isNotBlank()) {
            Text(
                text = hub.partialText,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        StatusCard(R.string.status_ai_title, R.string.status_ai_value_ready)
        StatusCard(R.string.status_voice_title, R.string.status_voice_value_ready)
        StatusCard(R.string.status_overlay_title, R.string.status_overlay_value)
        StatusCard(R.string.status_automation_title, R.string.status_automation_value)
    }
}

@Composable
private fun StatusCard(titleRes: Int, valueRes: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(valueRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
