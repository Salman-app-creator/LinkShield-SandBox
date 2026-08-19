package com.linkshield.sandbox.ui.unblock

import android.app.Activity
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R
import com.linkshield.sandbox.ui.browser.SandboxBrowserScreen
import com.linkshield.sandbox.ui.grabber.LinkShieldGrabberScreen
import com.linkshield.sandbox.ui.upgrade.UpgradeScreen

private enum class MainTab(val label: String) {
    CHECK("Check"), BROWSE("Browse"), GRAB("Grabber"), UPGRADE("Upgrade")
}

@Composable
fun UnblockShieldScreen(
    initialUrl: String = "",
    isDarkTheme: Boolean = true,
    onThemeToggle: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.BROWSE.name) }
    var url by rememberSaveable {
        mutableStateOf(normalizeUrl(initialUrl).ifBlank { "https://www.google.com" })
    }
    var browserUrl by rememberSaveable { mutableStateOf(url) }
    var isShieldActive by rememberSaveable { mutableStateOf(true) }
    var isWireGuardEnabled by rememberSaveable { mutableStateOf(false) }
    var trialDays by rememberSaveable { mutableIntStateOf(30) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }

    val tab = try {
        MainTab.valueOf(selectedTab)
    } catch (e: Exception) {
        MainTab.BROWSE
    }

    BackHandler {
        when {
            tab == MainTab.BROWSE && canBack -> webView?.goBack()
            tab != MainTab.BROWSE -> selectedTab = MainTab.BROWSE.name
            else -> (context as? Activity)?.finish()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.values().forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { selectedTab = item.name },
                        icon = {
                            Icon(
                                when (item) {
                                    MainTab.CHECK -> Icons.Default.Security
                                    MainTab.BROWSE -> Icons.Default.Public
                                    MainTab.GRAB -> Icons.Default.Download
                                    MainTab.UPGRADE -> Icons.Default.Star
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
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
                        onUrlChange = { url = it },
                        isShieldProtectionEnabled = isShieldActive,
                        onShieldProtectionToggle = { isShieldActive = !isShieldActive },
                        isWireGuardEnabled = isWireGuardEnabled,
                        onWireGuardToggle = { isWireGuardEnabled = !isWireGuardEnabled },
                        trialDaysLeft = trialDays,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = onThemeToggle,
                        canGoBack = canBack,
                        canGoForward = canForward,
                        onBack = { webView?.goBack() },
                        onForward = { webView?.goForward() },
                        onReload = { webView?.reload() },
                        onNavigate = {
                            val target = normalizeUrl(url)
                            if (target.isNotBlank()) {
                                url = target
                                browserUrl = target
                                webView?.loadUrl(target)
                            }
                        },
                        isLoading = isLoading,
                        onReady = { webView = it },
                        onUrlChanged = {
                            browserUrl = it
                            url = it
                        },
                        onLoading = { isLoading = it },
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

                MainTab.CHECK -> {
                    CheckTab()
                }

                MainTab.GRAB -> {
                    runCatching {
                        LinkShieldGrabberScreen(
                            onBackToBrowser = { selectedTab = MainTab.BROWSE.name },
                            onUpgradeClick = { selectedTab = MainTab.UPGRADE.name }
                        )
                    }.getOrElse {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Upgrade screen loading failed.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckTab() {
    var input by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Link Security Check",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 0.3.sp
            )
        }

        Text(
            "UI-only preview. Security engines will be connected in the backend phase.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it; checked = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://example.com") }
        )

        Button(
            onClick = { checked = input.isNotBlank() },
            enabled = input.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check Link")
        }

        if (checked) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Demo result", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No engine executed. Backend security analysis will be plugged in later.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun normalizeUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}
