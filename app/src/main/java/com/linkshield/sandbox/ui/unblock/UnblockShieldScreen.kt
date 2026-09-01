package com.linkshield.sandbox.ui.unblock

import android.app.Activity
import android.net.VpnService
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.browser.SandboxBrowserScreen
import com.linkshield.sandbox.ui.grabber.LinkShieldGrabberScreen
import com.linkshield.sandbox.ui.upgrade.UpgradeScreen
import com.linkshield.sandbox.vpn.TorVpnManager
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
    sharedGrabUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    isDarkTheme: Boolean = true,
    onThemeToggle: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val licenseManager = remember { LicenseManager(context.applicationContext) }

    LaunchedEffect(Unit) {
        if (licenseManager.isProUser()) {
            com.linkshield.sandbox.dns.DnsManager(context).setProUser(true)
        }
    }

    var isProUser by remember { mutableStateOf(licenseManager.isProUser()) }
    var trialDays by remember { mutableIntStateOf(licenseManager.getTrialDaysRemaining()) }

    val psiphonManager = remember { TorVpnManager(context.applicationContext) }
    var isPsiphonConnected by remember { mutableStateOf(psiphonManager.isConnected()) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                val connectResult = psiphonManager.connect()
                isPsiphonConnected = connectResult.isSuccess
                if (connectResult.isFailure) {
                    Toast.makeText(context, "VPN connection failed", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            isPsiphonConnected = false
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.BROWSE.name) }

    var browserUrl by rememberSaveable {
        mutableStateOf(normalizeUrl(initialUrl).ifBlank { "https://www.google.com" })
    }
    var urlBarText by rememberSaveable { mutableStateOf(browserUrl) }

    // Share Intent se URL aaye to Grabber tab pe switch karo
    LaunchedEffect(sharedGrabUrl) {
        if (!sharedGrabUrl.isNullOrBlank()) {
            browserUrl = sharedGrabUrl
            selectedTab = MainTab.GRAB.name
            onSharedUrlConsumed()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            val newPro = licenseManager.isProUser()
            if (newPro != isProUser) {
                isProUser = newPro
                trialDays = licenseManager.getTrialDaysRemaining()
            }
        }
    }

    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }

    val tab = try { MainTab.valueOf(selectedTab) } catch (e: Exception) { MainTab.BROWSE }

    BackHandler {
        when {
            tab == MainTab.BROWSE && canBack -> webView?.goBack()
            tab != MainTab.BROWSE -> selectedTab = MainTab.BROWSE.name
            else -> (context as? Activity)?.finish()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                MainTab.values().forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            if (item == MainTab.GRAB) {
                                webView?.url?.takeIf { it.isNotBlank() }?.let { current ->
                                    browserUrl = current
                                    urlBarText = current
                                }
                            }
                            selectedTab = item.name
                        },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    MainTab.BROWSE  -> Icons.Default.Public
                                    MainTab.GRAB    -> Icons.Default.Download
                                    MainTab.UPGRADE -> Icons.Default.Star
                                },
                                contentDescription = item.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = { Text(item.label, fontSize = 10.sp, maxLines = 1) },
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
                        currentUrl = urlBarText,
                        onUrlChange = { urlBarText = it },
                        isShieldProtectionEnabled = true,
                        onShieldProtectionToggle = {},
                        isWireGuardEnabled = isPsiphonConnected,
                        onWireGuardToggle = {
                            scope.launch {
                                if (psiphonManager.isConnected()) {
                                    val result = psiphonManager.disconnect()
                                    if (result.isSuccess) isPsiphonConnected = false
                                } else {
                                    val prepareIntent = VpnService.prepare(context)
                                    if (prepareIntent != null) {
                                        vpnPermissionLauncher.launch(prepareIntent)
                                    } else {
                                        val result = psiphonManager.connect()
                                        isPsiphonConnected = result.isSuccess
                                        if (result.isFailure) {
                                            Toast.makeText(
    context,
    "VPN connection failed",
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
                        onBack = { webView?.goBack() },
                        onForward = { webView?.goForward() },
                        onReload = { webView?.reload() },
                        onNavigate = {
                            val target = normalizeUrl(urlBarText)
                            if (target.isNotBlank()) {
                                browserUrl = target
                                urlBarText = target
                                webView?.loadUrl(target)
                            }
                        },
                        isLoading = isLoading,
                        onReady = { webView = it },
                        onUrlChanged = { newUrl ->
                            browserUrl = newUrl
                            urlBarText = newUrl
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

                MainTab.GRAB -> {
                    LinkShieldGrabberScreen(
                        onBackToBrowser = { selectedTab = MainTab.BROWSE.name },
                        onUpgradeClick  = { selectedTab = MainTab.UPGRADE.name },
                        isProUser       = isProUser,
                        trialDaysLeft   = trialDays,
                        initialUrl      = browserUrl
                    )
                }

                MainTab.UPGRADE -> {
                    UpgradeScreen(
                        licenseManager = licenseManager,
                        modifier       = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun normalizeUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    val hasProtocol = trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)
    val hasDomainDot = trimmed.contains(".") && !trimmed.contains(" ")
    return when {
        hasProtocol  -> trimmed
        hasDomainDot -> "https://$trimmed"
        else         -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
    }
}
