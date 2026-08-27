package com.linkshield.sandbox.ui.screens

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/screens/GrabberScreen.kt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.linkshield.sandbox.ui.browser.SandboxBrowserViewModel
import com.linkshield.sandbox.ui.grabber.LinkShieldGrabberScreen

@Composable
fun GrabberScreen(
    browserViewModel: SandboxBrowserViewModel,
    onBackToBrowser: () -> Unit = {},
    onUpgradeClick: () -> Unit = {}
) {
    val uiState by browserViewModel.uiState.collectAsState()

    LinkShieldGrabberScreen(
        initialUrl = uiState.url,
        onBackToBrowser = onBackToBrowser,
        onUpgradeClick = onUpgradeClick
    )
}
