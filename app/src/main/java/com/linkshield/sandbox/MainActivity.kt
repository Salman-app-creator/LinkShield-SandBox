package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel
import com.linkshield.sandbox.ui.screens.checkIsDefaultBrowser
import com.linkshield.sandbox.ui.screens.openDefaultBrowserSettings
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import kotlinx.coroutines.delay
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

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

    override fun onCreate(savedInstanceState: Bundle?) {
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
                        isDarkTheme = newDark

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

@Composable
private fun LinkShieldRoot(
    disclaimerManager: DisclaimerManager,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onRequestBrowserRole: () -> Unit
) {

    val context =
        LocalContext.current

    /*
     * IMPORTANT:
     *
     * The ViewModel is created at Activity/Composition scope.
     *
     * It is NOT recreated when the user switches between
     * Shield and Grabber inside UnblockShieldScreen.
     *
     * Therefore the WebView stored inside the ViewModel
     * remains alive.
     */
    val unblockShieldViewModel:
        UnblockShieldViewModel =
        viewModel()

    var step by remember {

        val initial =
            when {

                !disclaimerManager.hasAccepted() ->
                    AppStep.DISCLAIMER

                !checkIsDefaultBrowser(context) ->
                    AppStep.ENABLE_SHIELD

                else ->
                    AppStep.MAIN
            }

        mutableStateOf(initial)
    }

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
                }
            }
        }
    }

    when (step) {

        AppStep.DISCLAIMER -> {

            com.linkshield.sandbox.ui.disclaimer
                .FirstLaunchDisclaimerDialog {

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

        AppStep.ENABLE_SHIELD -> {

            EnableShieldRootScreen(
                onRequestBrowserRole =
                    onRequestBrowserRole,

                onBrowserSet = {

                    disclaimerManager
                        .markBrowserSet()

                    step =
                        AppStep.MAIN
                }
            )
        }

        AppStep.MAIN -> {

            /*
             * DO NOT create another Scaffold here.
             *
             * UnblockShieldScreen already owns:
             *
             * - Top bar
             * - WebView
             * - Grabber tab
             * - Upgrade tab
             * - Bottom Navigation
             *
             * Keeping it as the single main screen prevents
             * duplicate navigation UI.
             */
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

    /*
     * These managers are remembered for the lifetime
     * of the Main composition.
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

    LaunchedEffect(Unit) {

        if (
            !dnsManager.isDohEnabled()
        ) {
            dnsManager.enableDoh()
        }
    }

    /*
     * IMPORTANT:
     *
     * UnblockShieldScreen already contains the complete
     * existing UI and its own Shield / Grabber / Upgrade
     * navigation.
     *
     * We therefore DO NOT wrap it in another Scaffold
     * or NavigationBar.
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

@Composable
private fun EnableShieldRootScreen(
    onRequestBrowserRole: () -> Unit,
    onBrowserSet: () -> Unit
) {

    com.linkshield.sandbox.ui.EnableProtectionScreenWrapper(
        onRequestBrowserRole =
            onRequestBrowserRole,

        onBrowserSet =
            onBrowserSet
    )
}
