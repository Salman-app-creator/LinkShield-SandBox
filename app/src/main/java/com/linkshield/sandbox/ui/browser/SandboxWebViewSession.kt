package com.linkshield.sandbox.ui.browser

import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Owns the single WebView used by the sandbox browser session.
 * The stored instance is reused across Compose/navigation changes, but a
 * destroyed or unusable instance is never returned to the UI.
 */
object SandboxWebViewSession {

    private var webView: WebView? = null

    @Synchronized
    fun attach(view: WebView) {
        if (webView === view) return

        webView?.let { old ->
            runCatching { old.stopLoading() }
        }
        webView = view
    }

    @Synchronized
    fun get(): WebView? {
        val view = webView ?: return null
        if (isUsable(view)) return view

        webView = null
        runCatching { view.destroy() }
        return null
    }

    @Synchronized
    fun hasSession(): Boolean = get() != null

    @Synchronized
    fun currentUrl(): String = get()?.url.orEmpty()

    @Synchronized
    fun goBack(): Boolean {
        val view = get() ?: return false
        if (!view.canGoBack()) return false
        view.goBack()
        return true
    }

    @Synchronized
    fun goForward(): Boolean {
        val view = get() ?: return false
        if (!view.canGoForward()) return false
        view.goForward()
        return true
    }

    @Synchronized
    fun reload() {
        get()?.reload()
    }

    @Synchronized
    fun destroy() {
        val view = webView ?: return
        webView = null

        runCatching {
            view.stopLoading()
            view.webChromeClient = null
            view.webViewClient = WebViewClient()
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        }
    }

    private fun isUsable(view: WebView): Boolean {
        return runCatching {
            // Accessing settings is a useful destroyed-WebView guard.
            view.settings
            true
        }.getOrDefault(false)
    }
}
