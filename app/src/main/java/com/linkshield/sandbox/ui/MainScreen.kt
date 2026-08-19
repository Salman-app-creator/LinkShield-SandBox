package com.linkshield.sandbox.ui

import androidx.compose.runtime.Composable
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen

/** Canonical frozen main UI entry point. Backend engines are intentionally not bound here. */
@Composable
fun MainScreen(
    initialUrl: String = "",
    isDarkTheme: Boolean = true,
    onThemeToggle: (Boolean) -> Unit = {}
) {
    UnblockShieldScreen(
        initialUrl = initialUrl,
        isDarkTheme = isDarkTheme,
        onThemeToggle = onThemeToggle
    )
}
