package com.linkshield.sandbox.ui.unblock

import android.app.Activity
import android.net.VpnService
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.browser.SandboxBrowserScreen
import com.linkshield.sandbox.ui.grabber.LinkShieldGrabberScreen
import com.linkshield.sandbox.ui.upgrade.UpgradeScreen
import com.linkshield.sandbox.vpn.WireGuardVpnManager
import kotlinx.coroutines.launch
import java.net.URLEncoder

private enum class MainTab(val label: String) {
    BROWSE("Shield"),
    GRAB("Grabber"),
    UPGRADE("Upgrade")
}

@Composable
fun UnblockShieldScreen(
    initialUrl: String = "",
    isDarkTheme: Boolean = true,
    onThemeToggle: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // LicenseManager instance for key validation
    val licenseManager = remember {
        LicenseManager(context.applicationContext)
    }

    // Sync existing pro status to DnsManager on first composition
    LaunchedEffect(Unit) {
        if (licenseManager.isProUser()) {
            com.linkshield.sandbox.dns.DnsManager(context).setProUser(true)
        }
    }

    val isProUser by remember { mutableStateOf(licenseManager.isProUser()) }
    val trialDays by remember { mutableIntStateOf(licenseManager.getTrialDaysRemaining()) }

    // WireGuard Engine Manager
    val wireGuardManager = remember {
        WireGuardVpnManager(context.applicationContext)
    }

    var isWireGuardConnected by remember {
        mutableStateOf(wireGuardManager.isConnected())
    }

    // Android System VPN Permission Launcher
    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                scope.launch {
                    val connectResult = wireGuardManager.connect()
                    isWireGuardConnected = connectResult.isSuccess
                    if (connectResult.isFailure) {
                        Toast.makeText(
                            context,
                            connectResult.exceptionOrNull()?.message ?: "Connection failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                isWireGuardConnected = false
            }
        }

    var selectedTab by rememberSaveable {
        mutableStateOf(MainTab.BROWSE.name)
    }

    var url by rememberSaveable {
        mutableStateOf(
            normalizeUrl(initialUrl)
                .ifBlank { "https://www.google.com" }
        )
    }

    var browserUrl by rememberSaveable {
        mutableStateOf(url)
    }

    val isAdGuardActive = true

    var canBack by remember {
        mutableStateOf(false)
    }

    var canForward by remember {
        mutableStateOf(false)
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var webView by remember {
        mutableStateOf<WebView?>(null)
    }

    var webViewGeneration by remember {
        mutableIntStateOf(0)
    }

    val tab = try {
        MainTab.valueOf(selectedTab)
    } catch (e: Exception) {
        MainTab.BROWSE
    }

    BackHandler {
        when {
            tab == MainTab.BROWSE && canBack -> {
                webView?.goBack()
            }

            tab != MainTab.BROWSE -> {
                selectedTab = MainTab.BROWSE.name
            }

            else -> {
                (context as? Activity)?.finish()
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.values().forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            selectedTab = item.name
                        },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    MainTab.BROWSE -> Icons.Default.Public
                                    MainTab.GRAB -> Icons.Default.Download
                                    MainTab.UPGRADE -> Icons.Default.Star
                                },
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(item.label)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                MainTab.BROWSE -> {
                    SandboxBrowserScreen(
                        generation = webViewGeneration,
                        startUrl = browserUrl,
                        currentUrl = url,
                        onUrlChange = {
                            url = it
                        },
                        isShieldProtectionEnabled = isAdGuardActive,
                        onShieldProtectionToggle = {},
                        isWireGuardEnabled = isWireGuardConnected,
                        onWireGuardToggle = {
                            scope.launch {
                                if (wireGuardManager.isConnected()) {
                                    val disconnectResult = wireGuardManager.disconnect()
                                    if (disconnectResult.isSuccess) {
                                        isWireGuardConnected = false
                                    }
                                } else {
                                    if (!wireGuardManager.hasConfiguration()) {
                                        Toast.makeText(
                                            context,
                                            "WireGuard configuration missing!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }

                                    val prepareIntent = VpnService.prepare(context)
                                    if (prepareIntent != null) {
                                        vpnPermissionLauncher.launch(prepareIntent)
                                    } else {
                                        val connectResult = wireGuardManager.connect()
                                        isWireGuardConnected = connectResult.isSuccess
                                        if (connectResult.isFailure) {
                                            Toast.makeText(
                                                context,
                                                connectResult.exceptionOrNull()?.message ?: "Connection failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        },
                        trialDaysLeft = trialDays,
                        isProUser = isProUser,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = onThemeToggle,
                        canGoBack = canBack,
                        canGoForward = canForward,
                        onBack = {
                            webView?.goBack()
                        },
                        onForward = {
                            webView?.goForward()
                        },
                        onReload = {
                            webView?.reload()
                        },
                        onNavigate = {
                            val target = normalizeUrl(url)
                            if (target.isNotBlank()) {
                                url = target
                                browserUrl = target
                                webView?.loadUrl(target)
                            }
                        },
                        isLoading = isLoading,
                        onReady = {
                            webView = it
                        },
                        onUrlChanged = {
                            browserUrl = it
                            url = it
                        },
                        onLoading = {
                            isLoading = it
                        },
                        onNavigation = { back, forward ->
                            canBack = back
                            canForward = forward
                        },
                        onRendererGone = {
                            webView = null
                            webViewGeneration++
                        }
                    )
                }

                MainTab.GRAB -> {
                    runCatching {
                        LinkShieldGrabberScreen(
                            onBackToBrowser = {
                                selectedTab = MainTab.BROWSE.name
                            },
                            onUpgradeClick = {
                                selectedTab = MainTab.UPGRADE.name
                            }
                        )
                    }.getOrElse {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Grabber feature opening failed.")
                        }
                    }
                }

                MainTab.UPGRADE -> {
                    runCatching {
                        UpgradeScreen(
                            licenseManager = licenseManager,
                            modifier = Modifier.fillMaxSize()
                        )
                    }.getOrElse {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Upgrade screen loading failed.")
                        }
                    }
                }
            }
        }
    }
}

// Improved URL Normalizer: Fixes white screen on search queries
private fun normalizeUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""

    val hasProtocol = trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)
    val hasDomainDot = trimmed.contains(".") && !trimmed.contains(" ")

    return when {
        hasProtocol -> trimmed
        hasDomainDot -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
    }
}
