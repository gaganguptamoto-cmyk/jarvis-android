package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jarvis.assistant.core.SettingsStore
import com.jarvis.assistant.ui.navigation.JarvisApp
import com.jarvis.assistant.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore(applicationContext)
        setContent {
            JarvisTheme(darkTheme = store.darkTheme) {
                JarvisApp(store)
            }
        }
    }
}
