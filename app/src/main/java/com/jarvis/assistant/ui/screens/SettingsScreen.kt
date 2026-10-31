package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.R
import com.jarvis.assistant.core.SecureKeyStore
import com.jarvis.assistant.core.SettingsStore
import com.jarvis.assistant.voice.VoiceHub

@Composable
fun SettingsScreen(store: SettingsStore, hub: VoiceHub) {
    val context = LocalContext.current
    val keyStore = remember { SecureKeyStore(context.applicationContext) }
    var hasKey by remember { mutableStateOf(keyStore.hasApiKey()) }
    var keyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(store.geminiModel) }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        SwitchRow(R.string.settings_dark_theme, store.darkTheme) { store.updateDarkTheme(it) }
        SwitchRow(R.string.settings_animations, store.animations) { store.updateAnimations(it) }
        VoiceSettingsSection(store, hub)

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_ai_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(
                if (hasKey) R.string.settings_key_saved else R.string.settings_key_missing,
            ),
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_api_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (keyInput.isNotBlank()) {
                        keyStore.saveApiKey(keyInput)
                        keyInput = ""
                        hasKey = keyStore.hasApiKey()
                    }
                },
                enabled = keyInput.isNotBlank(),
            ) {
                Text(stringResource(R.string.settings_save))
            }
            TextButton(
                onClick = {
                    keyStore.clearApiKey()
                    hasKey = false
                },
                enabled = hasKey,
            ) {
                Text(stringResource(R.string.settings_remove))
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = modelInput,
            onValueChange = { modelInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_model)) },
            singleLine = true,
        )
        Text(
            text = stringResource(R.string.settings_model_hint),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                store.updateGeminiModel(modelInput)
                modelInput = store.geminiModel
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.settings_save))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_language),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_version, version),
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
