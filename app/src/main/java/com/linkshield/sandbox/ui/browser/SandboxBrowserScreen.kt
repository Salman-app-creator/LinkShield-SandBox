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
import kotlinx.coroutines.Job
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
    isShieldProtectionEnabled: Boolean = true,
    onShieldProtectionToggle: () -> Unit = {},
    isWireGuardEnabled: Boolean = false,
    onWireGuardToggle: () -> Unit = {}
) {
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val securityService = remember { SecurityApiService() }
    val scope = rememberCoroutineScope()
    var navigationSecurityJob by remember { mutableStateOf<Job?>(null) }

    var shieldState by remember {
        mutableStateOf(ShieldState.CHECKING)
    }

    /*
     * Status-only verification for URLs that arrive from WebView history or
     * other navigation paths. Actual new loads are gated by safeLoadUrl().
     */
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

    /*
     * Every app-requested navigation passes through the security service
     * before WebView.loadUrl(). A malicious, suspicious, or unverifiable URL
     * is never loaded.
     */
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
                }

                result.isSuspicious -> {
                    shieldState = ShieldState.SUSPICIOUS
                    onLoading(false)
                }

                result.isError -> {
                    // Offline/API failure is NEVER represented as Safe.
                    shieldState = ShieldState.ERROR
                    onLoading(false)
                }

                else -> {
                    shieldState = ShieldState.SAFE
                    val verifiedUrl = result.checkedUrl
                    onUrlChanged(verifiedUrl)
                    onUrlChange(verifiedUrl)
                    webView.loadUrl(verifiedUrl)
                }
            }
        }
    }

    LaunchedEffect(startUrl) {
        val webView = webViewState.value ?: return@LaunchedEffect

        if (
            startUrl.isNotBlank() &&
            startUrl != "about:blank" &&
            webView.url != startUrl
        ) {
            safeLoadUrl(webView, startUrl)
        }
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
                isLoading = isLoading
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
                            safeLoadUrl(existing, startUrl)
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

                        /*
                         * Do not allow file/content URLs to participate in the
                         * sandbox. WebView remains HTTP/HTTPS only.
                         */
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false

                        webViewClient = object : WebViewClient() {

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                shieldState = ShieldState.CHECKING
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
                                val target = request?.url?.toString().orEmpty()
                                val scheme = request?.url?.scheme?.lowercase()

                                if (scheme != "http" && scheme != "https") {
                                    return true
                                }

                                if (view == null || target.isBlank()) {
                                    return true
                                }

                                /*
                                 * Returning true prevents WebView from loading
                                 * first. safeLoadUrl performs the threat check
                                 * and only then calls loadUrl().
                                 */
                                safeLoadUrl(view, target)
                                return true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                url: String?
                            ): Boolean {
                                if (url.isNullOrBlank()) return true

                                val scheme =
                                    runCatching {
                                        java.net.URI(url).scheme?.lowercase()
                                    }.getOrNull()

                                if (scheme != "http" && scheme != "https") {
                                    return true
                                }

                                if (view == null) return true

                                safeLoadUrl(view, url)
                                return true
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: android.webkit.RenderProcessGoneDetail?
                            ): Boolean {
                                onLoading(false)

                                runCatching {
                                    view?.destroy()
                                }

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
                            safeLoadUrl(this, startUrl)
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
