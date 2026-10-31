package com.jarvis.assistant.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jarvis.assistant.R

@Composable
fun ChatScreen() {
    CenteredMessage(
        title = stringResource(R.string.nav_chat),
        message = stringResource(R.string.chat_coming),
    )
}
