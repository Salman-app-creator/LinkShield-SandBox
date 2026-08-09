package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.ui.components.TopHeaderBar
import com.linkshield.sandbox.ui.disclaimer.FirstLaunchDisclaimerDialog
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val disclaimerManager = DisclaimerManager(this)
        val themeManager = ThemeManager(this)

        setContent {
            val darkTheme = remember { mutableStateOf(themeManager.isDarkTheme()) }

            LinkShieldTheme(darkTheme = darkTheme.value) {
                var accepted by remember { mutableStateOf(disclaimerManager.hasAccepted()) }

                if (!accepted) {
                    FirstLaunchDisclaimerDialog(
                        onAccept = {
                            disclaimerManager.accept()
                            accepted = true
                        }
                    )
                }

                LinkShieldApp(
                    isDarkTheme = darkTheme.value,
                    onThemeToggle = {
                        val newIsDark = !darkTheme.value
                        darkTheme.value = newIsDark
                        themeManager.setTheme(
                            if (newIsDark) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                        )
                    }
                )
            }
        }
    }
}

sealed class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Shield : Tab("shield", "Unblock Shield", Icons.Default.Bolt)
    object Grabber : Tab("grabber", "Media Grabber", Icons.Default.Download)
}

@Composable
fun LinkShieldApp(
    isDarkTheme: Boolean = true,
    onThemeToggle: () -> Unit = {}
) {
    val navController = rememberNavController()
    val tabs = listOf(Tab.Shield, Tab.Grabber)

    Scaffold(
        topBar = {
            TopHeaderBar(
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle
            )
        },
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val current = navBackStackEntry?.destination

                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = current?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Shield.route,
            Modifier.padding(innerPadding)
        ) {
            composable(Tab.Shield.route) { UnblockShieldScreen() }
            composable(Tab.Grabber.route) { MediaGrabberScreen() }
        }
    }
}
