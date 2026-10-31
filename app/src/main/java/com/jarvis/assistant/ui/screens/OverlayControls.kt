package com.jarvis.assistant.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.assistant.R
import com.jarvis.assistant.overlay.OverlayService

@Composable
fun OverlayControls() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var running by remember { mutableStateOf(OverlayService.running) }
    var micDenied by remember { mutableStateOf(false) }

    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = Settings.canDrawOverlays(context)
                running = OverlayService.running
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    fun startNow() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, OverlayService::class.java),
        )
        running = true
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { startNow() }

    fun askNotificationThenStart() {
        val needed = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needed) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startNow()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micDenied = !granted
        if (granted) askNotificationThenStart()
    }

    fun onStartClick() {
        micDenied = false
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName),
                ),
            )
            return
        }
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) askNotificationThenStart() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.overlay_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(if (allowed) R.string.overlay_perm_ok else R.string.overlay_perm_no),
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (running) {
            Text(
                text = stringResource(R.string.overlay_running),
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = {
                    context.stopService(Intent(context, OverlayService::class.java))
                    running = false
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.overlay_stop))
            }
        } else {
            Button(
                onClick = { onStartClick() },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    stringResource(if (allowed) R.string.overlay_start else R.string.overlay_allow),
                )
            }
        }
        if (micDenied) {
            Text(
                text = stringResource(R.string.overlay_mic_needed),
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.overlay_info),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
