package com.linkshield.sandbox.ui.browser

import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.api.SecurityApiService
import com.linkshield.sandbox.ui.ShieldState
import com.linkshield.sandbox.ui.TopHeader
import com.linkshield.sandbox.ui.qr.QrScannerScreen

@Composable
fun SandboxBrowserScreen(
    generation: Int,
    startUrl: String,
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    trialDaysLeft: Int,
    isProUser: Boolean = false,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onNavigate: () -> Unit,
    isLoading: Boolean,
    onReady: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    onNavigation: (Boolean, Boolean) -> Unit,
    onRendererGone: () -> Unit,
    isShieldProtectionEnabled: Boolean = true,
    onShieldProtectionToggle: () -> Unit = {},
    isWireGuardEnabled: Boolean = false,
    onWireGuardToggle: () -> Unit = {}
) {
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val securityService = remember { SecurityApiService() }

    var shieldState by remember {
        mutableStateOf(ShieldState.CHECKING)
    }

    // ── READER MODE STATE ──
    var isReaderModeEnabled by remember { mutableStateOf(false) }

    // ── QR SCANNER STATE ──
    var showQrScanner by remember { mutableStateOf(false) }

    // ── QR SE SCAN KIYA GAYA URL (pending load ke liye) ──
    var pendingQrUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUrl) {
        val url = currentUrl.trim()
        if (url.isBlank() || url == "about:blank") {
            shieldState = ShieldState.CHECKING
            return@LaunchedEffect
        }
        shieldState = ShieldState.CHECKING
        val result = securityService.checkUrl(url)
        shieldState = when {
            result.isError -> ShieldState.ERROR
            result.isMalicious -> ShieldState.DANGEROUS
            result.isSuspicious -> ShieldState.SUSPICIOUS
            else -> ShieldState.SAFE
        }
    }

    LaunchedEffect(startUrl) {
        val webView = webViewState.value ?: return@LaunchedEffect
        if (
            startUrl.isNotBlank() &&
            startUrl != "about:blank" &&
            webView.url != startUrl
        ) {
            webView.loadUrl(startUrl)
            isReaderModeEnabled = false
        }
    }

    // ── QR SE SCAN KIYA GAYA URL LOAD KAREIN ──
    // Jab scanner band ho jaye aur webView available ho, tab URL load karein
    LaunchedEffect(showQrScanner, webViewState.value) {
        if (!showQrScanner) {
            val pendingUrl = pendingQrUrl
            val webView = webViewState.value
            if (pendingUrl != null && webView != null) {
                webView.loadUrl(pendingUrl)
                onUrlChanged(pendingUrl)
                onUrlChange(pendingUrl)
                pendingQrUrl = null
            }
        }
    }

    // ── QR SCANNER OVERLAY ──
    if (showQrScanner) {
        QrScannerScreen(
            onQrScanned = { scannedValue ->
                // URL normalize karein
                val url = when {
                    scannedValue.startsWith("http://") ||
                    scannedValue.startsWith("https://") -> scannedValue
                    scannedValue.contains(".") &&
                    !scannedValue.contains(" ") -> "https://$scannedValue"
                    else -> "https://www.google.com/search?q=${scannedValue}"
                }
                // Pending URL save karein — scanner band hone ke baad load hoga
                pendingQrUrl = url
                showQrScanner = false
            },
            onBackPressed = { showQrScanner = false }
        )
        return
    }

    key(generation) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TopHeader(
                currentUrl = currentUrl,
                onUrlChange = onUrlChange,
                shieldState = shieldState,
                trialDaysLeft = trialDaysLeft,
                isProUser = isProUser,
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBack = onBack,
                onForward = onForward,
                onReload = onReload,
                onNavigate = onNavigate,
                isLoading = isLoading,
                isReaderModeEnabled = isReaderModeEnabled,
                onReaderModeToggle = {
                    val webView = webViewState.value ?: return@TopHeader
                    isReaderModeEnabled = !isReaderModeEnabled
                    if (isReaderModeEnabled) {
                        ReaderModeManager.enable(webView)
                    } else {
                        ReaderModeManager.disable(webView)
                    }
                },
                onQrScanClick = { showQrScanner = true }
            )

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),

                factory = { ctx ->
                    SandboxWebViewSession.get()?.also { existing ->
                        webViewState.value = existing
                        onReady(existing)
                        if (
                            startUrl.isNotBlank() &&
                            startUrl != "about:blank" &&
                            existing.url != startUrl
                        ) {
                            existing.loadUrl(startUrl)
                        }
                    } ?: WebView(ctx).apply {
                        setBackgroundColor(
                            if (isDarkTheme) {
                                Color.parseColor("#FF0A0F14")
                            } else {
                                Color.parseColor("#FFF0F2F5")
                            }
                        )

                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportMultipleWindows(false)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                onLoading(true)
                                url?.let {
                                    if (it != "about:blank") {
                                        onUrlChanged(it)
                                    }
                                }
                            }

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?
                            ) {
                                onLoading(false)
                                onNavigation(
                                    view?.canGoBack() == true,
                                    view?.canGoForward() == true
                                )
                                url?.let {
                                    if (it != "about:blank") {
                                        onUrlChanged(it)
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val scheme = request?.url?.scheme?.lowercase()
                                return scheme != "http" && scheme != "https"
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: android.webkit.RenderProcessGoneDetail?
                            ): Boolean {
                                onLoading(false)
                                runCatching { view?.destroy() }
                                webViewState.value = null
                                SandboxWebViewSession.destroy()
                                onRendererGone()
                                return true
                            }
                        }

                        webChromeClient = WebChromeClient()

                        webViewState.value = this
                        SandboxWebViewSession.attach(this)
                        onReady(this)

                        if (
                            startUrl.isNotBlank() &&
                            startUrl != "about:blank"
                        ) {
                            loadUrl(startUrl)
                        }
                    }
                },

                update = {
                    webViewState.value = it
                    onReady(it)
                }
            )
        }
    }
}
