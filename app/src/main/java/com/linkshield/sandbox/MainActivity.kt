package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.ui.components.TopHeader
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.GrabberScreen
import com.linkshield.sandbox.ui.screens.UpgradeScreen
import com.linkshield.sandbox.ui.screens.isDefaultBrowser
import com.linkshield.sandbox.ui.screens.openDefaultBrowserSettings
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager

private enum class AppStep {
    DISCLAIMER,      
    ENABLE_SHIELD,   
    MAIN             
}

class MainActivity : ComponentActivity() {

    private lateinit var disclaimerManager: DisclaimerManager
    private lateinit var themeManager: ThemeManager

    private val defaultBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        disclaimerManager = DisclaimerManager(this)
        themeManager      = ThemeManager(this)

        setContent {
            var isDarkTheme by remember { mutableStateOf(themeManager.isDarkTheme()) }

            LinkShieldTheme(darkTheme = isDarkTheme) {
                LinkShieldRoot(
                    disclaimerManager    = disclaimerManager,
                    isDarkTheme          = isDarkTheme,
                    onThemeToggle        = { newDark ->
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
        openDefaultBrowserSettings(this)
    }
}

@Composable
private fun LinkShieldRoot(
    disclaimerManager:    DisclaimerManager,
    isDarkTheme:          Boolean,
    onThemeToggle:        (Boolean) -> Unit,
    onRequestBrowserRole: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var step by remember {
        val initial = when {
            !disclaimerManager.hasAccepted() -> AppStep.DISCLAIMER
            !context.isDefaultBrowser()      -> AppStep.ENABLE_SHIELD
            else                             -> AppStep.MAIN
        }
        mutableStateOf(initial)
    }

    LaunchedEffect(step) {
        if (step == AppStep.ENABLE_SHIELD) {
            while (step == AppStep.ENABLE_SHIELD) {
                kotlinx.coroutines.delay(1000)
                if (context.isDefaultBrowser()) {
                    disclaimerManager.markBrowserSet()
                    step = AppStep.MAIN
                }
            }
        }
    }

    when (step) {
        AppStep.DISCLAIMER -> {
            DisclaimerScreen(
                onAccept = {
                    disclaimerManager.accept()
                    step = if (context.isDefaultBrowser()) AppStep.MAIN
                           else AppStep.ENABLE_SHIELD
                }
            )
        }

        AppStep.ENABLE_SHIELD -> {
            EnableShieldScreen(
                onRequestDefaultBrowser = onRequestBrowserRole, // FIX: Tied directly to Activity Launcher
                onBrowserSet = {
                    disclaimerManager.markBrowserSet()
                    step = AppStep.MAIN
                }
            )
        }

        AppStep.MAIN -> {
            MainScreen(
                isDarkTheme   = isDarkTheme,
                onThemeToggle = onThemeToggle
            )
        }
    }
}

@Composable
fun MainScreen(
    isDarkTheme:   Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    var selectedTab    by remember { mutableIntStateOf(0) }
    var currentUrl     by remember { mutableStateOf("https://www.google.com") }
    var isShieldActive by remember { mutableStateOf(true) }
    var trialDaysLeft  by remember { mutableIntStateOf(30) }

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
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopHeader(
                            currentUrl     = currentUrl,
                            onUrlChange    = { currentUrl = it },
                            isShieldActive = isShieldActive,
                            onShieldToggle = { isShieldActive = !isShieldActive },
                            trialDaysLeft  = trialDaysLeft,
                            isDarkTheme    = isDarkTheme,
                            onThemeToggle  = onThemeToggle,
                            onMenuClick    = {}
                        )
                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Web Content / WebView Area")
                        }
                    }
                }
                1 -> GrabberScreen()
                2 -> UpgradeScreen(
                    trialDaysLeft = trialDaysLeft,
                    isTrialActive = trialDaysLeft > 0
                )
            }
        }
    }
}

private val MainActivity.Context: android.content.Context
    get() = this
