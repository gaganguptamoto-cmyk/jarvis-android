package com.jarvis.assistant.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jarvis.assistant.R

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }
    CenteredMessage(
        title = stringResource(R.string.nav_settings),
        message = stringResource(R.string.settings_version, version) + "\n" +
            stringResource(R.string.settings_language),
    )
}
