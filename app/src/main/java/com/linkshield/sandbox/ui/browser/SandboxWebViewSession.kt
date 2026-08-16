package com.linkshield.sandbox.ui.browser

import android.webkit.WebView

object SamdboxWebViewSession {

    private var webView: WebView? = null

    fun attach(view: WebView) {
        webView = view
    }

    fun get(): WebView? {
        return webView
    }

    fun currentUrl(): String {
        return webView?.url.orEmpty()
    }

    fun canGoBack(): Boolean {
        return webView?.canGoBack() == true
    }

    fun canGoForward(): Boolean {
        return webView?.canGoForward() == true
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

    fun clearReference() {
        webView = null
    }
        fun destroy() {
        val view = webView ?: return

        webView = null

        try {
            view.stopLoading()
            view.webChromeClient = null
            view.webViewClient = null
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        } catch (_: Exception) {
        }
    }
}
