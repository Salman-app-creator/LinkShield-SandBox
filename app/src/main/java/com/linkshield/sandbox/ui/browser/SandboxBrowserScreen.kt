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

    // ── QR SCANNER STATE (NEW) ──
    var showQrScanner by remember { mutableStateOf(false) }

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

    // ── QR SCANNER OVERLAY (NEW) ──
    if (showQrScanner) {
        QrScannerScreen(
            onQrScanned = { scannedValue ->
                showQrScanner = false
                val url = if (scannedValue.startsWith("http://") ||
                    scannedValue.startsWith("https://")) {
                    scannedValue
                } else if (scannedValue.contains(".") &&
                    !scannedValue.contains(" ")) {
                    "https://$scannedValue"
                } else {
                    "https://www.google.com/search?q=${scannedValue}"
                }
                webViewState.value?.loadUrl(url)
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
                // ── READER MODE ──
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
                // ── QR SCANNER (NEW) ──
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
                               
