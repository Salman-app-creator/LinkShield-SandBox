package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel
import com.linkshield.sandbox.ui.disclaimer.FirstLaunchDisclaimerDialog
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.checkIsDefaultBrowser
import com.linkshield.sandbox.ui.screens.openDefaultBrowserSettings
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import kotlinx.coroutines.delay


private enum class AppStep {
    DISCLAIMER,
    ENABLE_SHIELD,
    MAIN
}


class MainActivity : ComponentActivity() {

    private lateinit var disclaimerManager: DisclaimerManager
    private lateinit var themeManager: ThemeManager

    private val defaultBrowserLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        disclaimerManager =
            DisclaimerManager(this)

        themeManager =
            ThemeManager(this)

        setContent {

            var isDarkTheme by remember {
                mutableStateOf(
                    themeManager.isDarkTheme()
                )
            }

            LinkShieldTheme(
                darkTheme = isDarkTheme
            ) {

                LinkShieldRoot(
                    disclaimerManager =
                        disclaimerManager,

                    isDarkTheme =
                        isDarkTheme,

                    onThemeToggle = { newDark ->

                        isDarkTheme =
                            newDark

                        themeManager.setTheme(
                            if (newDark) {
                                ThemeManager.THEME_DARK
                            } else {
                                ThemeManager.THEME_LIGHT
                            }
                        )
                    },

                    onRequestBrowserRole = {
                        requestDefaultBrowserRole()
                    }
                )
            }
        }
    }


    private fun requestDefaultBrowserRole() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            val roleManager =
                getSystemService(
                    Context.ROLE_SERVICE
                ) as RoleManager

            if (
                roleManager.isRoleAvailable(
                    RoleManager.ROLE_BROWSER
                ) &&
                !roleManager.isRoleHeld(
                    RoleManager.ROLE_BROWSER
                )
            ) {

                defaultBrowserLauncher.launch(
                    roleManager.createRequestRoleIntent(
                        RoleManager.ROLE_BROWSER
                    )
                )

                return
            }
        }

        openDefaultBrowserSettings(this)
    }
}


/**
 * Root application flow.
 *
 * ViewModel is created at Activity/Composition scope.
 *
 * The same UnblockShieldViewModel instance is passed
 * into UnblockShieldScreen for the complete lifetime
 * of this Activity.
 *
 * Therefore the WebView stored inside the ViewModel
 * is NOT recreated merely because the user changes
 * between Shield / Grabber / Upgrade.
 */
@Composable
private fun LinkShieldRoot(
    disclaimerManager: DisclaimerManager,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onRequestBrowserRole: () -> Unit
) {

    val context =
        LocalContext.current


    /**
     * IMPORTANT:
     *
     * Activity-scoped ViewModel.
     *
     * Do NOT create this ViewModel inside
     * UnblockShieldScreen.
     */
    val unblockShieldViewModel:
        UnblockShieldViewModel =
        viewModel()


    var step by remember {

        val initialStep =
            when {

                !disclaimerManager.hasAccepted() ->
                    AppStep.DISCLAIMER

                !checkIsDefaultBrowser(context) ->
                    AppStep.ENABLE_SHIELD

                else ->
                    AppStep.MAIN
            }

        mutableStateOf(
            initialStep
        )
    }


    /**
     * Keep checking whether the user has selected
     * LinkShield as the default browser.
     *
     * This is only the application flow state.
     *
     * The actual Enable Shield UI itself is provided
     * by EnableShieldScreen().
     */
    LaunchedEffect(step) {

        if (
            step ==
            AppStep.ENABLE_SHIELD
        ) {

            while (
                step ==
                AppStep.ENABLE_SHIELD
            ) {

                delay(1000)

                if (
                    checkIsDefaultBrowser(
                        context
                    )
                ) {

                    disclaimerManager
                        .markBrowserSet()

                    step =
                        AppStep.MAIN

                    break
                }
            }
        }
    }


    when (step) {

        /**
         * ------------------------------------------------
         * STEP 1 - DISCLAIMER
         * ------------------------------------------------
         */
        AppStep.DISCLAIMER -> {

            FirstLaunchDisclaimerDialog {

                disclaimerManager.accept()

                step =
                    if (
                        checkIsDefaultBrowser(
                            context
                        )
                    ) {
                        AppStep.MAIN
                    } else {
                        AppStep.ENABLE_SHIELD
                    }
            }
        }


        /**
         * ------------------------------------------------
         * STEP 2 - ENABLE SHIELD
         * ------------------------------------------------
         *
         * IMPORTANT FIX:
         *
         * Previous code used:
         *
         * EnableProtectionScreenWrapper(...)
         *
         * That composable does NOT exist in the repo.
         *
         * The existing correct screen is:
         *
         * EnableShieldScreen(...)
         *
         * from:
         *
         * ui/screens/OnboardingScreens.kt
         */
        AppStep.ENABLE_SHIELD -> {

            EnableShieldScreen(

                onRequestBrowserRole = {
                    onRequestBrowserRole()
                },

                onBrowserSet = {

                    disclaimerManager
                        .markBrowserSet()

                    step =
                        AppStep.MAIN
                }
            )
        }


        /**
         * ------------------------------------------------
         * STEP 3 - MAIN APP
         * ------------------------------------------------
         */
        AppStep.MAIN -> {

            MainScreen(
                unblockShieldViewModel =
                    unblockShieldViewModel,

                isDarkTheme =
                    isDarkTheme,

                onThemeToggle =
                    onThemeToggle
            )
        }
    }
}


/**
 * Main application screen.
 *
 * IMPORTANT:
 *
 * We do NOT create another Scaffold here.
 *
 * UnblockShieldScreen already owns:
 *
 * - Top bar
 * - Address bar
 * - Back / Forward
 * - Refresh
 * - DNS controls
 * - Theme button
 * - Shield tab
 * - Grabber tab
 * - Upgrade tab
 * - WebView
 *
 * This keeps the existing UI completely unchanged.
 */
@Composable
private fun MainScreen(
    unblockShieldViewModel:
        UnblockShieldViewModel,

    isDarkTheme: Boolean,

    onThemeToggle:
        (Boolean) -> Unit
) {

    val context =
        LocalContext.current


    /**
     * Managers remain remembered for this
     * Main composition.
     */
    val dnsManager =
        remember {
            DnsManager(context)
        }

    val licenseManager =
        remember {
            LicenseManager(context)
        }

    val disclaimerManager =
        remember {
            DisclaimerManager(context)
        }


    /**
     * Enable existing DNS-over-HTTPS protection
     * if it is currently disabled.
     */
    LaunchedEffect(Unit) {

        if (
            !dnsManager.isDohEnabled()
        ) {

            dnsManager.enableDoh()
        }
    }


    /**
     * SINGLE existing application UI.
     *
     * The same ViewModel is passed here.
     *
     * This is important for WebView session retention.
     */
    UnblockShieldScreen(

        dnsManager =
            dnsManager,

        viewModel =
            unblockShieldViewModel,

        licenseManager =
            licenseManager,

        disclaimerManager =
            disclaimerManager,

        isDarkTheme =
            isDarkTheme,

        onThemeToggle =
            onThemeToggle,

        isVisible =
            true
    )
}
