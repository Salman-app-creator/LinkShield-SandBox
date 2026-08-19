package com.linkshield.sandbox.ui.unblock

import android.app.Activity
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.linkshield.sandbox.ui.components.TopHeader
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
    val keyboard = LocalSoftwareKeyboardController.current
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.BROWSE.name) }
    var url by rememberSaveable { mutableStateOf(normalizeUrl(initialUrl).ifBlank { "https://www.google.com" }) }
    var browserUrl by rememberSaveable { mutableStateOf(url) }
    var isShieldActive by rememberSaveable { mutableStateOf(true) }
    var trialDays by rememberSaveable { mutableIntStateOf(30) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }

    val tab = MainTab.valueOf(selectedTab)

    BackHandler {
        when {
            tab == MainTab.BROWSE && canBack -> webView?.goBack()
            tab != MainTab.BROWSE -> selectedTab = MainTab.BROWSE.name
            else -> (context as? Activity)?.finish()
        }
    }

    Scaffold(
        topBar = {
            TopHeader(
                currentUrl = url,
                onUrlChange = { url = it },
                isShieldActive = isShieldActive,
                onShieldToggle = { isShieldActive = !isShieldActive },
                trialDaysLeft = trialDays,
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                canGoBack = tab == MainTab.BROWSE && canBack,
                canGoForward = tab == MainTab.BROWSE && canForward,
                onBack = { if (tab == MainTab.BROWSE) webView?.goBack() else selectedTab = MainTab.BROWSE.name },
                onForward = { webView?.goForward() },
                onReload = { webView?.reload() },
                onNavigate = {
                    val target = normalizeUrl(url)
                    if (target.isNotBlank()) {
                        keyboard?.hide()
                        url = target
                        browserUrl = target
                        selectedTab = MainTab.BROWSE.name
                        webView?.loadUrl(target)
                    }
                },
                isLoading = isLoading,
                onDnsProviderChange = { isShieldActive = true },
                onDnsDisable = { isShieldActive = false },
                onOpenSecure = { Toast.makeText(context, "Secure Network is reserved for the next engine phase.", Toast.LENGTH_SHORT).show() }
            )
        },
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
                                item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                MainTab.BROWSE -> BasicSandboxBrowser(
                    generation = webViewGeneration,
                    startUrl = browserUrl,
                    onReady = { webView = it },
                    onUrlChanged = { browserUrl = it; url = it },
                    onLoading = { isLoading = it },
                    onNavigation = { back, forward -> canBack = back; canForward = forward },
                    onRendererGone = { webView = null; webViewGeneration++ }
                )
                MainTab.CHECK -> CheckTab()
                MainTab.GRAB -> LinkShieldGrabberScreen(
                    onBackToBrowser = { selectedTab = MainTab.BROWSE.name },
                    onUpgradeClick = { selectedTab = MainTab.UPGRADE.name }
                )
                MainTab.UPGRADE -> UpgradeScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }

}

@Composable
private fun BasicSandboxBrowser(
    generation: Int,
    startUrl: String,
    onReady: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    onNavigation: (Boolean, Boolean) -> Unit,
    onRendererGone: () -> Unit
) {
    androidx.compose.runtime.key(generation) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportMultipleWindows(false)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            onLoading(true)
                            url?.let(onUrlChanged)
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            onLoading(false)
                            onNavigation(view?.canGoBack() == true, view?.canGoForward() == true)
                        }
                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            onLoading(false)
                            runCatching { view?.destroy() }
                            onRendererGone()
                            return true
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val scheme = request?.url?.scheme?.lowercase()
                            return scheme != null && scheme !in setOf("http", "https")
                        }
                    }
                    webChromeClient = WebChromeClient()
                    onReady(this)
                    if (startUrl.isNotBlank()) loadUrl(startUrl)
                }
            },
            update = { onReady(it) }
        )
    }
}

@Composable
private fun CheckTab() {
    var input by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Link Security Check", style = MaterialTheme.typography.headlineSmall)
        Text("UI-only preview. Security engines will be connected in the backend phase.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        ) { Text("Check Link") }
        if (checked) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Demo result", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("No engine executed. Backend security analysis will be plugged in later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun normalizeUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
}
