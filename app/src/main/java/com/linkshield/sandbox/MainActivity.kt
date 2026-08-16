package com.linkshield.sandbox

// ─────────────────────────────────────────────────────────────────────────────
// MainActivity.kt — Build-fixed + UI Integrated
//
// FIXES APPLIED:
//  1. Added missing "import android.content.Context" (was causing unresolved
//     reference on Context.ROLE_SERVICE).
//  2. Removed the broken extension property "private val MainActivity.Context"
//     at the bottom of the file.
//  3. MainScreen now uses UnblockShieldScreen (full browser with URL bar,
//     navigation, WebView, Shield badge, server dropdown, theme toggle)
//     instead of the placeholder TopHeader + "Web Content Area" text.
//  4. Grabber tab now uses MediaGrabberScreen (real download UI) instead
//     of the placeholder GrabberScreen.
//  5. Upgrade tab now receives live trial data from LicenseManager.
//  6. DnsManager is initialized once and passed to both Shield & Grabber.
// ─────────────────────────────────────────────────────────────────────────────

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.UpgradeScreen
import com.linkshield.sandbox.ui.screens.isDefaultBrowser
import com.linkshield.sandbox.ui.screens.openDefaultBrowserSettings
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import kotlinx.coroutines.delay

// ── App navigation state ──────────────────────────────────────────────────────
private enum class AppStep {
    DISCLAIMER,      // Screen 1 — must accept T&C
    ENABLE_SHIELD,   // Screen 2 — must set as default browser
    MAIN             // Full app — 3-tab main screen
}

class MainActivity : ComponentActivity() {

    private lateinit var disclaimerManager: DisclaimerManager
    private lateinit var themeManager: ThemeManager

    // Launcher for RoleManager's default browser intent (Android 10+)
    private val defaultBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Result arrives here after user interacts with the system picker.
        // We don't need to handle the result code — isDefaultBrowser() will
        // be polled in the composable LaunchedEffect.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        disclaimerManager = DisclaimerManager(this)
        themeManager      = ThemeManager(this)

        setContent {
            var isDarkTheme by remember { mutableStateOf(themeManager.isDarkTheme()) }

            LinkShieldTheme(darkTheme = isDarkTheme) {
                LinkShieldRoot(
                    disclaimerManager  = disclaimerManager,
                    isDarkTheme        = isDarkTheme,
                    onThemeToggle      = { newDark ->
                        isDarkTheme = newDark
                        themeManager.setTheme(
                            if (newDark) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                        )
                    },
                    onRequestBrowserRole = { requestDefaultBrowserRole() }
                )
            }
        }
    }

    // ── Request default browser role (Android 10+ direct picker) ─────────────
    private fun requestDefaultBrowserRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                !rm.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                defaultBrowserLauncher.launch(
                    rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                )
                return
            }
        }
        // Fallback for older Android versions
        openDefaultBrowserSettings(this)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LinkShieldRoot — decides which screen to show based on AppStep state
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LinkShieldRoot(
    disclaimerManager:    DisclaimerManager,
    isDarkTheme:          Boolean,
    onThemeToggle:        (Boolean) -> Unit,
    onRequestBrowserRole: () -> Unit
) {
    val context = LocalContext.current

    // Compute initial step once — then update reactively
    var step by remember {
        val initial = when {
            !disclaimerManager.hasAccepted()           -> AppStep.DISCLAIMER
            !context.isDefaultBrowser()                -> AppStep.ENABLE_SHIELD
            else                                       -> AppStep.MAIN
        }
        mutableStateOf(initial)
    }

    // When app resumes from background, re-check if we're still default browser.
    // This handles the case where user sets default browser then comes back.
    LaunchedEffect(step) {
        if (step == AppStep.ENABLE_SHIELD) {
            // Keep checking every second while on this screen
            while (step == AppStep.ENABLE_SHIELD) {
                delay(1000)
                if (context.isDefaultBrowser()) {
                    disclaimerManager.markBrowserSet()
                    step = AppStep.MAIN
                }
            }
        }
    }

    when (step) {
        // ── SCREEN 1: Disclaimer ──────────────────────────────────────────────
        AppStep.DISCLAIMER -> {
            DisclaimerScreen(
                onAccept = {
                    disclaimerManager.accept()
                    // Move to step 2 — check browser status fresh
                    step = if (context.isDefaultBrowser()) AppStep.MAIN
                           else AppStep.ENABLE_SHIELD
                }
            )
        }

        // ── SCREEN 2: Enable Shield (mandatory) ───────────────────────────────
        AppStep.ENABLE_SHIELD -> {
            EnableShieldScreen(
                onBrowserSet = {
                    disclaimerManager.markBrowserSet()
                    step = AppStep.MAIN
                }
            )
        }

        // ── SCREEN 3: Main App ────────────────────────────────────────────────
        AppStep.MAIN -> {
            MainScreen(
                isDarkTheme    = isDarkTheme,
                onThemeToggle  = onThemeToggle
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MainScreen — 3-tab scaffold with real browser, grabber & upgrade
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MainScreen(
    isDarkTheme:   Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context     = LocalContext.current
    val dnsManager  = remember { DnsManager(context) }
    val licenseManager = remember { LicenseManager(context) }

    // Ensure DoH is enabled by default on first launch
    LaunchedEffect(Unit) {
        if (!dnsManager.isDohEnabled()) {
            dnsManager.enableDoh()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    icon     = { Icon(Icons.Default.Shield, contentDescription = "Shield") },
                    label    = { Text("Shield") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    icon     = { Icon(Icons.Default.Download, contentDescription = "Grabber") },
                    label    = { Text("Grabber") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick  = { selectedTab = 2 },
                    icon     = { Icon(Icons.Default.Star, contentDescription = "Upgrade") },
                    label    = { Text("Upgrade") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                // ── TAB 0: Shield / Browser ─────────────────────────────────────
                0 -> UnblockShieldScreen(
                    dnsManager    = dnsManager,
                    onMediaFound  = { /* Media URL detected — can be passed to Grabber later */ },
                    isDarkMode    = isDarkTheme,
                    onToggleTheme = { onThemeToggle(!isDarkTheme) }
                )

                // ── TAB 1: Media Grabber ────────────────────────────────────────
                1 -> MediaGrabberScreen(
                    licenseManager = licenseManager,
                    dnsManager     = dnsManager
                )

                // ── TAB 2: Upgrade ──────────────────────────────────────────────
                2 -> UpgradeScreen(
                    trialDaysLeft = licenseManager.getTrialDaysRemaining(),
                    isTrialActive = licenseManager.isTrialActive()
                )
            }
        }
    }
}
