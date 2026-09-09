package com.linkshield.sandbox.ui.browser

import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URI

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
    val scope = rememberCoroutineScope()
    var navigationSecurityJob by remember { mutableStateOf<Job?>(null) }
    var shieldState by remember { mutableStateOf(ShieldState.CHECKING) }

    fun showBrowserError(webView: WebView, title: String, message: String) {
        val safeTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val safeMessage = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val html = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              body{margin:0;background:#0f0f1a;color:#fff;font-family:sans-serif;
              min-height:100vh;display:flex;align-items:center;justify-content:center;text-align:center}
              main{max-width:520px;padding:32px}h1{font-size:22px;margin:0 0 12px}
              p{color:#aaa;line-height:1.6;font-size:14px}.shield{font-size:56px;margin-bottom:18px}
            </style></head><body><main>
            <div class="shield">🛡️</div><h1>$safeTitle</h1><p>$safeMessage</p>
            </main></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun safeLoadUrl(webView: WebView, rawUrl: String) {
        val requested = rawUrl.trim()
        if (requested.isBlank() || requested == "about:blank") return

        navigationSecurityJob?.cancel()
        navigationSecurityJob = scope.launch {
            shieldState = ShieldState.CHECKING
            onLoading(true)
            val result = securityService.checkUrl(requested)

            when {
                result.isMalicious -> {
                    shieldState = ShieldState.DANGEROUS
                    onLoading(false)
                    showBrowserError(webView, "Unsafe website blocked", "LinkShield blocked this URL because a known security threat was detected.")
                }
                result.isSuspicious -> {
                    shieldState = ShieldState.SUSPICIOUS
                    onLoading(false)
                    showBrowserError(webView, "Suspicious website blocked", "LinkShield could not safely approve this URL.")
                }
                result.isError -> {
                    // Security failures must fail closed; never load an unverified URL.
                    shieldState = ShieldState.ERROR
                    onLoading(false)
                    showBrowserError(webView, "Security check unavailable", "This website was not loaded because LinkShield could not verify its safety. Check your connection or security configuration and try again.")
                }
                else -> {
                    shieldState = ShieldState.SAFE
                    val verifiedUrl = result.checkedUrl
                    onUrlChanged(verifiedUrl)
                    onUrlChange(verifiedUrl)
                    onLoading(true)
                    webView.loadUrl(verifiedUrl)
                }
            }
        }
    }

    // Wait for the actual WebView. This removes the old startUrl/WebView race.
    // There is intentionally no direct loadUrl() in the AndroidView factory.
    LaunchedEffect(startUrl, webViewState.value) {
        val webView = webViewState.value ?: return@LaunchedEffect
        val requested = startUrl.trim()
        if (requested.isBlank() || requested == "about:blank") return@LaunchedEffect

        val loaded = webView.url
        if (loaded == null || loaded == "about:blank" || loaded != requested) {
            safeLoadUrl(webView, requested)
        }
    }

    // Keep the header reputation indicator synchronized with the URL field.
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
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                shieldState = ShieldState.CHECKING
                                onLoading(true)
                                url?.takeIf { it != "about:blank" }?.let(onUrlChanged)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                onLoading(false)
                                onNavigation(view?.canGoBack() == true, view?.canGoForward() == true)
                                url?.takeIf { it != "about:blank" }?.let(onUrlChanged)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true && view != null) {
                                    onLoading(false)
                                    shieldState = ShieldState.ERROR
                                    showBrowserError(
                                        view,
                                        "Page not available",
                                        error?.description?.toString()?.ifBlank { "The website could not be loaded." }
                                            ?: "The website could not be loaded."
                                    )
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url?.toString().orEmpty()
                                val scheme = request?.url?.scheme?.lowercase()
                                if (view == null || target.isBlank() || (scheme != "http" && scheme != "https")) return true
                                safeLoadUrl(view, target)
                                return true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                if (view == null || url.isNullOrBlank()) return true
                                val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
                                if (scheme != "http" && scheme != "https") return true
                                safeLoadUrl(view, url)
                                return true
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: android.webkit.RenderProcessGoneDetail?
                            ): Boolean {
                                onLoading(false)
                                SandboxWebViewSession.destroy()
                                webViewState.value = null
                                onRendererGone()
                                return true
                            }
                        }

                        webChromeClient = WebChromeClient()
                        webViewState.value = this
                        SandboxWebViewSession.attach(this)
                        onReady(this)
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
