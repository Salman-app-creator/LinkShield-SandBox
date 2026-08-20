package com.linkshield.sandbox.ui.unblock

import android.app.Activity
import android.content.Intent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.linkshield.sandbox.ui.browser.SandboxBrowserScreen
import com.linkshield.sandbox.ui.grabber.LinkShieldGrabberScreen
import com.linkshield.sandbox.ui.upgrade.UpgradeScreen
import com.linkshield.sandbox.vpn.VpnShieldController
import com.linkshield.sandbox.vpn.WireGuardVpnStatus
import kotlinx.coroutines.launch

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

    val vpnController = remember {
        VpnShieldController(context.applicationContext)
    }

    val vpnStatus by vpnController.status.collectAsState()

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                scope.launch {
                    vpnController.connect()
                }
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

    // AdGuard / Content Blocker is permanently active by default in the background
    val isAdGuardActive = true

    var isDnsShieldEnabled by rememberSaveable {
        mutableStateOf(true)
    }

    var trialDays by rememberSaveable {
        mutableIntStateOf(7)
    }

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
                        // AdGuard hamesha active rahega background mein
                        isShieldProtectionEnabled = isAdGuardActive,
                        onShieldProtectionToggle = {
                            // AdGuard is auto-enabled, no manual toggle needed here
                        },
                        isWireGuardEnabled =
                            vpnStatus == WireGuardVpnStatus.CONNECTED,
                        onWireGuardToggle = {
                            val activity = context as? Activity ?: return@SandboxBrowserScreen

                            scope.launch {
                                vpnController.toggle(
                                    activity = activity,
                                    launcher = vpnPermissionLauncher
                                )
                            }
                        },
                        trialDaysLeft = trialDays,
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
                            licenseManager = null,
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

private fun normalizeUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        return ""
    }
    return if (
        trimmed.startsWith("http://", true) ||
        trimmed.startsWith("https://", true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
}
