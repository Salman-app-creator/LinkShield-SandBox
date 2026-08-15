package com.linkshield.sandbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.ui.components.BottomNavigationBar
import com.linkshield.sandbox.ui.components.TopHeader
import com.linkshield.sandbox.ui.screens.GrabberScreen
import com.linkshield.sandbox.ui.screens.ShieldScreen
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
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> ShieldScreen(currentUrl = currentUrl)
                1 -> GrabberScreen()
                2 -> UpgradeScreen()
            }
        }
    }
}
