package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.GrabberScreen
import com.linkshield.sandbox.ui.screens.UpgradeScreen
import com.linkshield.sandbox.ui.screens.isDefaultBrowser
import com.linkshield.sandbox.ui.screens.openDefaultBrowserSettings
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
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
    private lateinit var dnsManager: DnsManager

    private val defaultBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        disclaimerManager = DisclaimerManager(this)
        themeManager = ThemeManager(this)
        dnsManager = DnsManager(this)

        setContent {
            var isDarkTheme by remember { mutableStateOf(themeManager.isDarkTheme()) }

            LinkShieldTheme(darkTheme = isDarkTheme) {
                LinkShieldRoot(
                    disclaimerManager = disclaimerManager,
                    dnsManager = dnsManager,
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { newDark ->
                        isDarkTheme = newDark
                        themeManager.setTheme(
                            if (newDark) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LinkShieldRoot(
    disclaimerManager: DisclaimerManager,
    dnsManager: DnsManager,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var step by remember {
        val initial = when {
            !disclaimerManager.hasAccepted() -> AppStep.DISCLAIMER
            !context.isDefaultBrowser() -> AppStep.ENABLE_SHIELD
            else -> AppStep.MAIN
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
                onBrowserSet = {
                    disclaimerManager.markBrowserSet()
                    step = AppStep.MAIN
                }
            )
        }

        AppStep.MAIN -> {
            MainScreen(
                dnsManager = dnsManager,
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle
            )
        }
    }
}

@Composable
fun MainScreen(
    dnsManager: DnsManager,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Shield") },
                    label = { Text("Shield") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Grabber") },
                    label = { Text("Grabber") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Upgrade") },
                    label = { Text("Upgrade") }
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
                    // Yahan real WebView aur Header screen load ho rahi hai
                    UnblockShieldScreen(
                        dnsManager = dnsManager,
                        onMediaFound = { mediaUrl -> 
                            // Media grabber handle logic
                        },
                        isDarkMode = isDarkTheme,
                        onToggleTheme = { onThemeToggle(!isDarkTheme) }
                    )
                }
                1 -> GrabberScreen()
                2 -> UpgradeScreen()
            }
        }
    }
}
