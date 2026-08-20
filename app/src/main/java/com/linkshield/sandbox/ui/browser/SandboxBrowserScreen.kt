package com.linkshield.sandbox.ui.browser

import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.ui.components.TopHeader

/**
 * Canonical browser tab. TopHeader is intentionally owned by this screen only.
 */
@Composable
fun SandboxBrowserScreen(
    generation: Int,
    startUrl: String,
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    isShieldProtectionEnabled: Boolean,
    onShieldProtectionToggle: () -> Unit,
    isWireGuardEnabled: Boolean,
    onWireGuardToggle: () -> Unit,
    trialDaysLeft: Int,
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
    onRendererGone: () -> Unit
) {
    val webViewState = remember { mutableStateOf<WebView?>(null) }

    key(generation) {
        Column {
            TopHeader(
                currentUrl = currentUrl,
                onUrlChange = onUrlChange,
                isShieldProtectionEnabled = isShieldProtectionEnabled,
                onShieldProtectionToggle = onShieldProtectionToggle,
                isWireGuardEnabled = isWireGuardEnabled,
                onWireGuardToggle = onWireGuardToggle,
                trialDaysLeft = trialDaysLeft,
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
                    WebView(ctx).apply {
                        // White screen flash ko rokne ke liye transparent background
                        setBackgroundColor(Color.TRANSPARENT)

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
                                url?.let(onUrlChanged)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                onLoading(false)
                                onNavigation(
                                    view?.canGoBack() == true,
                                    view?.canGoForward() == true
                                )
                                // Back/Forward ke baad address bar ko final URL se sync rakhne k liye
                                url?.let {
                                    if (it != "about:blank") {
                                        onUrlChanged(it)
                                    }
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                onLoading(false)
                                runCatching { view?.destroy() }
                                webViewState.value = null
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
                        onReady(this)

                        if (startUrl.isNotBlank()) {
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
