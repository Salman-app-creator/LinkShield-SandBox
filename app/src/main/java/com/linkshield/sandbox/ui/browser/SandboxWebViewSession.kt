package com.linkshield.sandbox.ui.browser

import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Owns the single WebView used by the sandbox browser session.
 *
 * The session survives Compose/navigation changes and is destroyed
 * only when explicitly requested.
 */
object SandboxWebViewSession {

    private var webView: WebView? = null

    @Synchronized
    fun attach(view: WebView) {
        if (webView === view) {
            return
        }

        webView?.let { old ->
            try {
                old.stopLoading()
            } catch (_: Exception) {
            }
        }

        webView = view
    }

    @Synchronized
    fun get(): WebView? {
        return webView
    }

    @Synchronized
    fun hasSession(): Boolean {
        return webView != null
    }

    fun currentUrl(): String {
        return webView?.url.orEmpty()
    }

    fun goBack(): Boolean {
        val view = webView ?: return false

        if (!view.canGoBack()) {
            return false
        }

        view.goBack()
        return true
    }

    fun goForward(): Boolean {
        val view = webView ?: return false

        if (!view.canGoForward()) {
            return false
        }

        view.goForward()
        return true
    }
        fun reload() {
        webView?.reload()
    }

    @Synchronized
    fun destroy() {
        val view = webView ?: return

        webView = null

        try {
            view.stopLoading()

            view.webChromeClient = null

            // WebViewClient is non-null in the Kotlin API.
            // Never assign null here.
            view.webViewClient = WebViewClient()

            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        } catch (_: Exception) {
        }
    }
}
