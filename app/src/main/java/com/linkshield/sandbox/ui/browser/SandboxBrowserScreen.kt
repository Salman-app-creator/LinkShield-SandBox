package com.linkshield.sandbox.ui.browser

import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
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

    var shieldState by remember { mutableStateOf(ShieldState.CHECKING) }

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
        if (startUrl.isNotBlank() && startUrl != "about:blank" && webView.url != startUrl) {
            webView.loadUrl(startUrl)
        }
    }

    key(generation) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                isLoading = isLoading
            )

            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    SandboxWebViewSession.get()?.also { existing ->
                        webViewState.value = existing
                        onReady(existing)
                        if (startUrl.isNotBlank() && startUrl != "about:blank" && existing.url != startUrl) {
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
                        settings.domStorageEnabled = false
                        settings.databaseEnabled = false
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportMultipleWindows(false)

                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false

                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                        CookieManager.getInstance().setAcceptCookie(false)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                onLoading(true)
                                url?.let { if (it != "about:blank") onUrlChanged(it) }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                onLoading(false)
                                onNavigation(view?.canGoBack() == true, view?.canGoForward() == true)
                                url?.let { if (it != "about:blank") onUrlChanged(it) }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val scheme = request?.url?.scheme?.lowercase()
                                return scheme != "http" && scheme != "https"
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
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

                        if (startUrl.isNotBlank() && startUrl != "about:blank") {
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
