package com.linkshield.sandbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.CapturedMediaItem
import com.linkshield.sandbox.ui.MediaGrabberScreen
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel
import com.linkshield.sandbox.ui.disclaimer.FirstLaunchDisclaimerDialog
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.upgrade.UpgradeScreen

class MainActivity : ComponentActivity() {

    private lateinit var dnsManager:        DnsManager
    private lateinit var licenseManager:    LicenseManager
    private lateinit var disclaimerManager: DisclaimerManager
    private lateinit var themeManager:      ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dnsManager        = DnsManager(this)
        licenseManager    = LicenseManager(this)
        disclaimerManager = DisclaimerManager(this)
        themeManager      = ThemeManager(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        val interceptedUrl: String? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent?.data?.toString()
            else               -> intent?.getStringExtra("url")
        }

        setContent {
            var isDark by rememberSaveable { mutableStateOf(themeManager.isDarkTheme()) }

            LinkShieldTheme(darkTheme = isDark) {
                LinkShieldApp(
                    dnsManager        = dnsManager,
                    licenseManager    = licenseManager,
                    disclaimerManager = disclaimerManager,
                    interceptedUrl    = interceptedUrl,
                    isDark            = isDark,
                    onToggleTheme     = {
                        isDark = !isDark
                        themeManager.setTheme(
                            if (isDark) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LinkShieldApp(
    dnsManager:        DnsManager,
    licenseManager:    LicenseManager,
    disclaimerManager: DisclaimerManager,
    interceptedUrl:    String?,
    isDark:            Boolean,
    onToggleTheme:     () -> Unit
) {
    var disclaimerAccepted by rememberSaveable { mutableStateOf(disclaimerManager.hasAccepted()) }
    if (!disclaimerAccepted) {
        FirstLaunchDisclaimerDialog(onAccept = {
            disclaimerManager.accept()
            disclaimerAccepted = true
        })
        return
    }

    val unblockViewModel: UnblockShieldViewModel = viewModel()

    LaunchedEffect(interceptedUrl) {
        if (!interceptedUrl.isNullOrBlank()) unblockViewModel.loadUrl(interceptedUrl)
    }

    val mediaItems  by unblockViewModel.mediaUrls.collectAsState(initial = emptyList())
    val capturedMedia = androidx.compose.runtime.remember(mediaItems) {
        mediaItems.map {
            CapturedMediaItem(url = it.url, title = it.title, pageUrl = it.pageUrl, timestamp = it.timestamp)
        }
    }

    var selectedTab    by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = selectedTab != 0 || unblockViewModel.canGoBack) {
        when {
            selectedTab == 0 && unblockViewModel.canGoBack -> unblockViewModel.goBack()
            selectedTab != 0                               -> selectedTab = 0
        }
    }

    Scaffold(
        modifier  = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon     = { Icon(Icons.Default.Shield, contentDescription = "Shield") },
                    label    = { Text("Shield") },
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon     = { Icon(Icons.Default.Download, contentDescription = "Grabber") },
                    label    = { Text("Grabber") },
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon     = { Icon(Icons.Default.FlashOn, contentDescription = "Upgrade") },
                    label    = { Text("Upgrade") },
                    selected = selectedTab == 2,
                    onClick  = { selectedTab = 2 }
                )
            }
        }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            UnblockShieldScreen(
                dnsManager    = dnsManager,
                viewModel     = unblockViewModel,
                isVisible     = selectedTab == 0,
                isDarkTheme   = isDark,
                onToggleTheme = onToggleTheme
            )

            if (selectedTab == 1) {
                MediaGrabberScreen(
                    dnsManager      = dnsManager,
                    licenseManager  = licenseManager,
                    capturedMedia   = capturedMedia,
                    onClearCaptured = { unblockViewModel.clearMedia() },
                    onProRequired   = { selectedTab = 2 }
                )
            }

            if (selectedTab == 2) {
                UpgradeScreen(
                    licenseManager = licenseManager,
                    dnsManager     = dnsManager,
                    isDark         = isDark,
                    onToggleTheme  = onToggleTheme,
                    onUnlocked     = { /* refresh UI state */ }
                )
            }
        }
    }
}
