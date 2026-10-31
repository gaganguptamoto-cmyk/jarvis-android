package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.R
import com.jarvis.assistant.core.SettingsStore
import com.jarvis.assistant.ui.components.JarvisOrb
import com.jarvis.assistant.ui.components.OrbState

@Composable
fun DashboardScreen(store: SettingsStore) {
    var state by remember { mutableStateOf(OrbState.Idle) }

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
        JarvisOrb(state = state, animated = store.animations)
        Text(
            text = stringResource(state.labelRes),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.orb_preview_note),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OrbState.entries.forEach { s ->
                FilterChip(
                    selected = s == state,
                    onClick = { state = s },
                    label = { Text(stringResource(s.labelRes)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        StatusCard(R.string.status_ai_title, R.string.status_ai_value)
        StatusCard(R.string.status_voice_title, R.string.status_voice_value)
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
