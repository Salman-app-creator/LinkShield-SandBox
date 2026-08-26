package com.linkshield.sandbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.linkshield.sandbox.ui.browser.BrowserViewModel
import com.linkshield.sandbox.ui.grabber.LinkShieldGrabberScreen

@Composable
fun GrabberScreen(
    browserViewModel: BrowserViewModel,
    onBackToBrowser: () -> Unit = {},
    onUpgradeClick: () -> Unit = {}
) {
    val activeUrl by browserViewModel.currentUrl.collectAsState()

    LinkShieldGrabberScreen(
        initialUrl = activeUrl,
        onBackToBrowser = onBackToBrowser,
        onUpgradeClick = onUpgradeClick
    )
}
