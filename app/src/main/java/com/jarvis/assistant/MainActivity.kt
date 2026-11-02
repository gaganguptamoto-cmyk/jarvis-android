package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.jarvis.assistant.overlay.OverlayService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jarvis.assistant.core.SettingsStore
import com.jarvis.assistant.ui.navigation.JarvisApp
import com.jarvis.assistant.ui.theme.JarvisTheme
import com.jarvis.assistant.voice.VoiceHub

class MainActivity : ComponentActivity() {
    private var hub: VoiceHub? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore.get(applicationContext)
        val voiceHub = VoiceHub(applicationContext, store)
        hub = voiceHub
        // AUTO_OVERLAY
        if (Settings.canDrawOverlays(this) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            !OverlayService.running
        ) {
            try {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            } catch (e: Exception) {
            }
        }
        setContent {
            JarvisTheme(darkTheme = store.darkTheme) {
                JarvisApp(store, voiceHub)
            }
        }
    }

    override fun onStop() {
        hub?.stopAll()
        super.onStop()
    }

    override fun onDestroy() {
        hub?.shutdown()
        super.onDestroy()
    }
}
