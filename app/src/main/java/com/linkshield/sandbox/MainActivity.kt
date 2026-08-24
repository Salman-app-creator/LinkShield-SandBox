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

        /*
         * Edge-to-edge is intentional.
         *
         * Individual onboarding screens handle:
         * - status bar inset
         * - navigation bar inset
         */
        enableEdgeToEdge()

        interceptedUrlFlow.value = intent?.getStringExtra("url")

        val disclaimerManager = DisclaimerManager(this)
        val themeManager = ThemeManager(this)

        setContent {

            val interceptedUrl by interceptedUrlFlow.collectAsState()
            val context = LocalContext.current

            var isDarkTheme by remember {
                mutableStateOf(themeManager.isDarkTheme())
            }

            /*
             * Mandatory step 1:
             * User must accept the disclaimer.
             */
            var hasAccepted by remember {
                mutableStateOf(disclaimerManager.hasAccepted())
            }

            /*
             * Mandatory step 2:
             * User must make LinkShield the Android default browser.
             */
            var hasBrowserSet by remember {
                mutableStateOf(disclaimerManager.hasBrowserSet())
            }

            /*
             * Never trust only the stored preference.
             *
             * The actual Android role is checked as well.
             */
            var isDefaultBrowser by remember {
                mutableStateOf(checkIsDefaultBrowser(context))
            }

            /*
             * Update checker runs independently.
             * It must never block onboarding.
             */
            var showUpdateDialog by remember {
                mutableStateOf(false)
            }

            var updateInfo by remember {
                mutableStateOf<com.linkshield.sandbox.update.UpdateInfo?>(null)
            }

            LaunchedEffect(Unit) {
                runCatching {
                    UpdateChecker(context)
                        .checkForUpdate()
                        .getOrNull()
                        ?.let {
                            if (it.updateAvailable) {
                                updateInfo = it
                                showUpdateDialog = true
                            }
                        }
                }
            }

            /*
             * Mandatory Default Browser verification.
             *
             * Once the user returns from Android's browser-role screen,
             * this detects the real Android role and only then unlocks
             * the main application.
             *
             * If the user presses Back/cancels:
             * hasBrowserSet remains false.
             */
            LaunchedEffect(hasAccepted, hasBrowserSet) {

                if (hasAccepted && !hasBrowserSet) {

                    while (true) {

                        isDefaultBrowser =
                            checkIsDefaultBrowser(context)

                        if (isDefaultBrowser) {

                            /*
                             * Only now is the mandatory second
                             * onboarding step considered complete.
                             */
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
                    UpdateDialog(
                        updateInfo = updateInfo!!,
                        onDismiss = {
                            showUpdateDialog = false
                        }
                    )
                }

                when {

                    /*
                     * ======================================================
                     * STEP 1 — DISCLAIMER
                     * ======================================================
                     *
                     * No skip button.
                     * No back button.
                     * Main screen cannot be reached until accepted.
                     */
                    !hasAccepted -> {

                        DisclaimerScreen(
                            onAccept = {

                                disclaimerManager.accept()

                                hasAccepted = true

                                /*
                                 * If the app is already the default browser,
                                 * the second step can be considered complete.
                                 * Otherwise EnableShieldScreen is shown.
                                 */
                                isDefaultBrowser =
                                    checkIsDefaultBrowser(context)

                                if (isDefaultBrowser) {
                                    disclaimerManager.markBrowserSet()
                                    hasBrowserSet = true
                                }
                            }
                        )
                    }

                    /*
                     * ======================================================
                     * STEP 2 — DEFAULT BROWSER
                     * ======================================================
                     *
                     * This screen remains mandatory.
                     */
                    !hasBrowserSet -> {

                        EnableShieldScreen(
                            onRequestBrowserRole = {
                                openDefaultBrowserSettings(context)
                            }
                        )
                    }

                    /*
                     * ======================================================
                     * STEP 3 — MAIN APPLICATION
                     * ======================================================
                     *
                     * This branch is unreachable until:
                     *
                     * 1. Disclaimer accepted
                     * 2. LinkShield confirmed as default browser
                     */
                    else -> {

                        UnblockShieldScreen(
                            initialUrl = interceptedUrl ?: "",
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = { newDark ->

                                themeManager.setTheme(
                                    if (newDark) {
                                        ThemeManager.THEME_DARK
                                    } else {
                                        ThemeManager.THEME_LIGHT
                                    }
                                )

                                isDarkTheme = newDark
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)

        intent?.getStringExtra("url")?.let {
            interceptedUrlFlow.value = it
        }
    }
}
