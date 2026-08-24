package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.checkIsDefaultBrowser
import com.linkshield.sandbox.ui.screens.openDefaultBrowserSettings
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import com.linkshield.sandbox.update.UpdateChecker
import com.linkshield.sandbox.update.UpdateDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val interceptedUrlFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        interceptedUrlFlow.value = intent?.getStringExtra("url")

        val disclaimerManager = DisclaimerManager(this)
        val themeManager      = ThemeManager(this)

        setContent {
            val interceptedUrl   by interceptedUrlFlow.collectAsState()
            val context          = LocalContext.current
            var isDarkTheme      by remember { mutableStateOf(themeManager.isDarkTheme()) }
            var hasAccepted      by remember { mutableStateOf(disclaimerManager.hasAccepted()) }
            var hasBrowserSet    by remember { mutableStateOf(disclaimerManager.hasBrowserSet()) }
            var isDefaultBrowser by remember { mutableStateOf(checkIsDefaultBrowser(context)) }

            // Update check
            var showUpdateDialog by remember { mutableStateOf(false) }
            var updateInfo by remember {
                mutableStateOf<com.linkshield.sandbox.update.UpdateInfo?>(null)
            }
            LaunchedEffect(Unit) {
                runCatching {
                    UpdateChecker(context).checkForUpdate().getOrNull()?.let {
                        if (it.updateAvailable) { updateInfo = it; showUpdateDialog = true }
                    }
                }
            }

            // Poll browser default status every 500ms while on EnableShield screen
            LaunchedEffect(hasAccepted, hasBrowserSet) {
                if (hasAccepted && !hasBrowserSet) {
                    while (true) {
                        isDefaultBrowser = checkIsDefaultBrowser(context)
                        if (isDefaultBrowser) {
                            disclaimerManager.markBrowserSet()
                            hasBrowserSet = true
                            break
                        }
                        delay(500)
                    }
                }
            }

            LinkShieldTheme(darkTheme = isDarkTheme) {

                if (showUpdateDialog && updateInfo != null) {
                    UpdateDialog(updateInfo = updateInfo!!, onDismiss = { showUpdateDialog = false })
                }

                when {
                    // Step 1: Original DisclaimerScreen — logo + original content
                    !hasAccepted -> DisclaimerScreen(
                        onAccept = {
                            disclaimerManager.accept()
                            hasAccepted = true
                            isDefaultBrowser = checkIsDefaultBrowser(context)
                        }
                    )

                    // Step 2: Original EnableShieldScreen — logo + original content
                    !hasBrowserSet -> EnableShieldScreen(
                        onBrowserSet = {
                            disclaimerManager.markBrowserSet()
                            hasBrowserSet = true
                        },
                        onRequestBrowserRole = { openDefaultBrowserSettings(context) }
                    )

                    // Step 3: Main app
                    else -> UnblockShieldScreen(
                        initialUrl    = interceptedUrl ?: "",
                        isDarkTheme   = isDarkTheme,
                        onThemeToggle = { newDark ->
                            themeManager.setTheme(
                                if (newDark) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                            )
                            isDarkTheme = newDark
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra("url")?.let { interceptedUrlFlow.value = it }
    }
}
