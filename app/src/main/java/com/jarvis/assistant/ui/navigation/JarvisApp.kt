package com.jarvis.assistant.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.assistant.R
import com.jarvis.assistant.ui.screens.ChatScreen
import com.jarvis.assistant.ui.screens.DashboardScreen
import com.jarvis.assistant.ui.screens.SettingsScreen

enum class Route(val path: String, val labelRes: Int) {
    Dashboard("dashboard", R.string.nav_dashboard),
    Chat("chat", R.string.nav_chat),
    Settings("settings", R.string.nav_settings),
}

@Composable
fun JarvisApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Route.entries.forEach { route ->
                    NavigationBarItem(
                        selected = current == route.path,
                        onClick = {
                            nav.navigate(route.path) {
                                popUpTo(Route.Dashboard.path)
                                launchSingleTop = true
                            }
                        },
                        icon = {},
                        label = { Text(stringResource(route.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Route.Dashboard.path,
            modifier = Modifier.padding(padding),
        ) {
            composable(Route.Dashboard.path) { DashboardScreen() }
            composable(Route.Chat.path) { ChatScreen() }
            composable(Route.Settings.path) { SettingsScreen() }
        }
    }
}
