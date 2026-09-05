package com.linkshield.sandbox.ui.browser

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/browser/SandboxBrowserScreen.kt

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
import com.linkshield.sandbox.ui.components.ShieldState
import com.linkshield.sandbox.ui.components.TopHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    // VPN params — kept for backward compat, ignored
    isShieldProtectionEnabled: Boolean = true,
    onShieldProtectionToggle: () -> Unit = {},
    isWireGuardEnabled: Boolean = false,
    onWireGuardToggle: () -> Unit = {}
) {
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()
    val securityService = remember { SecurityApiService() }

    // Shield state — updates when URL changes
    var shieldState by remember { mutableStateOf(ShieldState.SAFE) }

    // Check URL with Safe Browsing API whenever URL changes
    LaunchedEffect(currentUrl) {
        if (currentUrl.isBlank() || currentUrl == "about:blank") {
            shieldState = ShieldState.SAFE
            return@LaunchedEffect
        }
        shieldState = ShieldState.CHECKING
        scope.launch(Dispatchers.IO) {
            try {
                val result = securityService.checkUrl(currentUrl)
                shieldState = when {
                    result.isMalicious  -> ShieldState.DANGEROUS
                    result.isSuspicious -> ShieldState.SUSPICIOUS
                    else                -> ShieldState.SAFE
                }
            } catch (_: Exception) {
                shieldState = ShieldState.SAFE
            }
        }
    }

    LaunchedEffect(startUrl) {
        val currentWebView = webViewState.value
        if (currentWebView != null && startUrl.isNotBlank() && currentWebView.url != startUrl) {
            currentWebView.loadUrl(startUrl)
        }
    }

    key(generation) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopHeader(
                currentUrl    = currentUrl,
                onUrlChange   = onUrlChange,
                shieldState   = shieldState,
                trialDaysLeft = trialDaysLeft,
                isProUser     = isProUser,
                isDarkTheme   = isDarkTheme,
                onThemeToggle = onThemeToggle,
                canGoBack     = canGoBack,
                canGoForward  = canGoForward,
                onBack        = onBack,
                onForward     = onForward,
                onReload      = onReload,
                onNavigate    = onNavigate,
                isLoading     = isLoading
            )

            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    SandboxWebViewSession.get()?.also { existing ->
                        webViewState.value = existing
                        onReady(existing)
                        if (startUrl.isNotBlank() && existing.url != startUrl) {
                            existing.loadUrl(startUrl)
                        }
                    } ?: WebView(ctx).apply {
                        setBackgroundColor(
                            if (isDarkTheme) Color.parseColor("#FF0A0F14")
                            else Color.parseColor("#FFF0F2F5")
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
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                onLoading(true)
                                url?.let(onUrlChanged)
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                onLoading(false)
                                onNavigation(view?.canGoBack() == true, view?.canGoForward() == true)
                                url?.let { if (it != "about:blank") onUrlChanged(it) }
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
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val scheme = request?.url?.scheme?.lowercase()
                                return scheme != null && scheme !in setOf("http", "https")
                            }
                        }
                        webChromeClient = WebChromeClient()
                        webViewState.value = this
                        SandboxWebViewSession.attach(this)
                        onReady(this)
                        if (startUrl.isNotBlank()) loadUrl(startUrl)
                    }
                },
                update = { webViewState.value = it; onReady(it) }
            )
        }
    }
}
