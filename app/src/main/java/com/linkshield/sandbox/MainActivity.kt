package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val interceptedUrlFlow = MutableStateFlow<String?>(null)
    // Fires true every time onResume runs — triggers recheck in Compose
    private val resumeTickFlow = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        interceptedUrlFlow.value = intent?.getStringExtra("url")

        val disclaimerManager = DisclaimerManager(this)
        val themeManager      = ThemeManager(this)

        setContent {
            val interceptedUrl by interceptedUrlFlow.collectAsState()
            val resumeTick     by resumeTickFlow.collectAsState()
            val context        = LocalContext.current

            var isDarkTheme   by remember { mutableStateOf(themeManager.isDarkTheme()) }
            var hasAccepted   by remember { mutableStateOf(disclaimerManager.hasAccepted()) }
            var hasBrowserSet by remember { mutableStateOf(disclaimerManager.hasBrowserSet()) }

            // Re-check every time activity resumes (e.g. returning from RoleManager dialog)
            LaunchedEffect(resumeTick) {
                if (hasAccepted && !hasBrowserSet) {
                    if (checkIsDefaultBrowser(context)) {
                        disclaimerManager.markBrowserSet()
                        hasBrowserSet = true
                    }
                }
            }

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

            LinkShieldTheme(darkTheme = isDarkTheme) {

                if (showUpdateDialog && updateInfo != null) {
                    UpdateDialog(updateInfo = updateInfo!!, onDismiss = { showUpdateDialog = false })
                }

                when {
                    !hasAccepted -> DisclaimerScreen(
                        onAccept = {
                            disclaimerManager.accept()
                            hasAccepted = true
                        }
                    )

                    !hasBrowserSet -> EnableShieldScreen(
                        onBrowserSet = {
                            disclaimerManager.markBrowserSet()
                            hasBrowserSet = true
                        },
                        onRequestBrowserRole = { openDefaultBrowserSettings(context) }
                    )

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

        // Increment tick every time activity comes to foreground
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                resumeTickFlow.value++
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra("url")?.let { interceptedUrlFlow.value = it }
    }
}
