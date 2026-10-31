package com.jarvis.assistant

import android.os.Bundle
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
