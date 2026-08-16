package com.linkshield.sandbox.ui.browser

import android.webkit.WebView

object SandboxWebViewSession {

    private var webView: WebView? = null

    fun attach(
        instance: WebView
    ) {
        webView = instance
    }

    fun get():
        WebView? {
        return webView
    }

    fun hasSession():
        Boolean {
        return webView != null
    }

    fun currentUrl():
        String {
        return webView?.url.orEmpty()
    }

    fun goBack():
        Boolean {
        val instance =
            webView ?: return false

        if (instance.canGoBack()) {
            instance.goBack()
            return true
        }

        return false
    }

    fun goForward():
        Boolean {
        val instance =
            webView ?: return false

        if (instance.canGoForward()) {
            instance.goForward()
            return true
        }

        return false
    }

    fun reload() {
        webView?.reload()
    }

    fun clearReference() {
        webView = null
    }

    fun destroy() {
        val instance =
            webView ?: return

        webView = null

        try {
            instance.stopLoading()
            instance.webChromeClient = null
            instance.webViewClient = null
            instance.loadUrl("about:blank")
            instance.clearHistory()
            instance.removeAllViews()
            instance.destroy()
        } catch (_: Exception) {
        }
    }
}
