package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.UpgradeScreen
import com.linkshield.sandbox.ui.screens.checkIsDefaultBrowser
import com.linkshield.sandbox.ui.screens.openDefaultBrowserSettings
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel
import kotlinx.coroutines.delay

private enum class AppStep {
    DISCLAIMER,
    ENABLE_SHIELD,
    MAIN
}

class MainActivity : ComponentActivity() {

    private lateinit var disclaimerManager:
        DisclaimerManager

    private lateinit var themeManager:
        ThemeManager

    /**
     * Activity-scoped ViewModel.
     *
     * This survives Compose recomposition and tab changes,
     * so the WebView instance remains owned by the same
     * ViewModel instead of being recreated every time the
     * Grabber tab is opened.
     */
    private val unblockShieldViewModel:
        UnblockShieldViewModel by viewModels()

    private val defaultBrowserLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

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

                    onThemeToggle = {
                        newDark ->

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
                    },

                    unblockShieldViewModel =
                        unblockShieldViewModel
                )
            }
        }
    }

    override fun onDestroy() {
        /**
         * Do not manually destroy the ViewModel/WebView here.
         *
         * The Activity ViewModelStore owns the ViewModel and
         * will call onCleared() at the correct lifecycle point.
         */
        super.onDestroy()
    }

    private fun requestDefaultBrowserRole() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            val rm =
                getSystemService(
                    Context.ROLE_SERVICE
                ) as RoleManager

            if (
                rm.isRoleAvailable(
                    RoleManager.ROLE_BROWSER
                ) &&
                !rm.isRoleHeld(
                    RoleManager.ROLE_BROWSER
                )
            ) {

                defaultBrowserLauncher.launch(
                    rm.createRequestRoleIntent(
                        RoleManager.ROLE_BROWSER
                    )
                )

                return
            }
        }

        openDefaultBrowserSettings(
            this
        )
    }
}

@Composable
private fun LinkShieldRoot(
    disclaimerManager:
        DisclaimerManager,

    isDarkTheme: Boolean,

    onThemeToggle:
        (Boolean) -> Unit,

    onRequestBrowserRole:
        () -> Unit,

    unblockShieldViewModel:
        UnblockShieldViewModel
) {

    val context =
        LocalContext.current

    var step by remember {

        val initial =
            when {

                !disclaimerManager.hasAccepted() ->
                    AppStep.DISCLAIMER

                !checkIsDefaultBrowser(
                    context
                ) ->
                    AppStep.ENABLE_SHIELD

                else ->
                    AppStep.MAIN
            }

        mutableStateOf(
            initial
        )
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

            DisclaimerScreen(
                onAccept = {

                    disclaimerManager
                        .accept()

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
            )
        }

        AppStep.ENABLE_SHIELD -> {

            EnableShieldScreen(

                onBrowserSet = {

                    disclaimerManager
                        .markBrowserSet()

                    step =
                        AppStep.MAIN
                },

                onRequestBrowserRole =
                    onRequestBrowserRole
            )
        }

        AppStep.MAIN -> {

            MainScreen(
                isDarkTheme =
                    isDarkTheme,

                onThemeToggle =
                    onThemeToggle,

                unblockShieldViewModel =
                    unblockShieldViewModel
            )
        }
    }
}
@Composable
fun MainScreen(
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    unblockShieldViewModel:
        UnblockShieldViewModel
) {

    /**
     * This state only controls which existing screen is
     * visible. It does NOT create/destroy the WebView.
     */
    var selectedTab by
        remember {
            mutableIntStateOf(0)
        }

    val context =
        LocalContext.current

    val dnsManager =
        remember {
            DnsManager(context)
        }

    val licenseManager =
        remember {
            LicenseManager(context)
        }

    LaunchedEffect(Unit) {

        if (
            !dnsManager.isDohEnabled()
        ) {
            dnsManager.enableDoh()
        }
    }

    Scaffold(

        bottomBar = {

            NavigationBar {

                NavigationBarItem(

                    selected =
                        selectedTab == 0,

                    onClick = {
                        selectedTab = 0
                    },

                    icon = {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription =
                                "Shield"
                        )
                    },

                    label = {
                        Text("Shield")
                    }
                )

                NavigationBarItem(

                    selected =
                        selectedTab == 1,

                    onClick = {
                        selectedTab = 1
                    },

                    icon = {
                        Icon(
                            Icons.Default.Download,
                            contentDescription =
                                "Grabber"
                        )
                    },

                    label = {
                        Text("Grabber")
                    }
                )

                NavigationBarItem(

                    selected =
                        selectedTab == 2,

                    onClick = {
                        selectedTab = 2
                    },

                    icon = {
                        Icon(
                            Icons.Default.Star,
                            contentDescription =
                                "Upgrade"
                        )
                    },

                    label = {
                        Text("Upgrade")
                    }
                )
            }
        }

    ) { innerPadding ->

        Box(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        innerPadding
                    )
        ) {

            when (selectedTab) {

                0 -> {

                    /**
                     * IMPORTANT:
                     * The same Activity-scoped ViewModel
                     * is passed every time.
                     *
                     * Therefore switching to Grabber and
                     * back does not create a new browser
                     * session.
                     */
                    UnblockShieldScreen(
                        dnsManager =
                            dnsManager,

                        viewModel =
                            unblockShieldViewModel,

                        onMediaFound = {
                            mediaItem ->
                            unblockShieldViewModel
                                .onMediaFound(
                                    url =
                                        mediaItem,
                                    title =
                                        unblockShieldViewModel.pageTitle,
                                    pageUrl =
                                        unblockShieldViewModel.currentUrl
                                )
                        },

                        isDarkMode =
                            isDarkTheme,

                        onToggleTheme = {
                            onThemeToggle(
                                !isDarkTheme
                            )
                        }
                    )
                }

                1 -> {

                    /**
                     * Existing Grabber UI remains unchanged.
                     *
                     * Back switches to the existing Shield
                     * screen while the same ViewModel/WebView
                     * instance remains alive.
                     */
                    MediaGrabberScreen(
                        licenseManager =
                            licenseManager,

                        dnsManager =
                            dnsManager,

                        onBack = {
                            selectedTab = 0
                        }
                    )
                }

                2 -> {

                    UpgradeScreen(

                        trialDaysLeft =
                            licenseManager
                                .getTrialDaysRemaining(),

                        isTrialActive =
                            licenseManager
                                .isTrialActive()
                    )
                }
            }
        }
    }
}
