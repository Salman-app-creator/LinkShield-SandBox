package com.linkshield.sandbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.ui.components.TopHeader
import com.linkshield.sandbox.ui.screens.GrabberScreen
import com.linkshield.sandbox.ui.screens.OnboardingScreen
import com.linkshield.sandbox.ui.screens.UpgradeScreen

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(1) } // Default: Grabber Tab
    var isDarkTheme by remember { mutableStateOf(true) }
    var showOnboarding by remember { mutableStateOf(false) }

    if (showOnboarding) {
        OnboardingScreen(onGetStarted = { showOnboarding = false })
    } else {
        Scaffold(
            topBar = {
                TopHeader(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = !isDarkTheme }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> { /* Sandbox Browser Engine yahan aayega */ }
                    1 -> GrabberScreen()
                    2 -> UpgradeScreen()
                }
            }
        }
    }
}
