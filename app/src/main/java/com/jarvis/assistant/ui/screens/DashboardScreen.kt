package com.jarvis.assistant.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jarvis.assistant.R

@Composable
fun DashboardScreen() {
    CenteredMessage(
        title = stringResource(R.string.app_name),
        message = stringResource(R.string.dashboard_status),
    )
}
