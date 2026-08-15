package com.linkshield.sandbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linkshield.sandbox.ui.components.TopHeader
import com.linkshield.sandbox.ui.screens.GrabberScreen
import com.linkshield.sandbox.ui.screens.UpgradeScreen

@Composable
fun MainScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf("https://www.google.com/") }
    var isShieldActive by remember { mutableStateOf(true) }
    var trialDaysLeft by remember { mutableIntStateOf(30) }

    Scaffold(
        topBar = {
            TopHeader(
                currentUrl = currentUrl,
                onUrlChange = { currentUrl = it },
                isShieldActive = isShieldActive,
                onShieldToggle = { isShieldActive = !isShieldActive },
                trialDaysLeft = trialDaysLeft,
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Shield") },
                    label = { Text("Shield") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Grabber") },
                    label = { Text("Grabber") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Upgrade") },
                    label = { Text("Upgrade") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> {
                    // Browser View / WebView Container
                    Text(
                        text = "Browser View: $currentUrl",
                        modifier = Modifier.padding(16.dp)
                    )
                }
                1 -> GrabberScreen()
                2 -> UpgradeScreen()
            }
        }
    }
}
