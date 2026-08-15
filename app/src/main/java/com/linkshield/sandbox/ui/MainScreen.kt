package com.linkshield.sandbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.ui.components.TopHeader
import com.linkshield.sandbox.ui.screens.GrabberScreen
import com.linkshield.sandbox.ui.screens.UpgradeScreen

@Composable
fun MainScreen(
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf("https://example.com/sandbox") }
    var isShieldActive by remember { mutableStateOf(true) }
    var trialDaysLeft by remember { mutableIntStateOf(30) }

    Scaffold(
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopHeader(
                            currentUrl = currentUrl,
                            onUrlChange = { currentUrl = it },
                            isShieldActive = isShieldActive,
                            onShieldToggle = { isShieldActive = !isShieldActive },
                            trialDaysLeft = trialDaysLeft,
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = { newThemeState -> onThemeToggle(newThemeState) }, // Fix: Type Mismatch Resolved
                            onMenuClick = { }
                        )

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Web Content Area")
                        }
                    }
                }
                1 -> GrabberScreen()
                2 -> UpgradeScreen(
                    trialDaysLeft = trialDaysLeft,
                    isTrialActive = trialDaysLeft > 0
                )
            }
        }
    }
}
