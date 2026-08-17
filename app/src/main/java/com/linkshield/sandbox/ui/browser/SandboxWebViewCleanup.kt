package com.linkshield.sandbox.ui.browser

import android.webkit.WebView
import android.webkit.WebViewClient

object SandboxWebViewCleanup {

    fun destroy(
        webView: WebView?
    ) {
        if (webView == null) {
            return
        }

        if (
            SandboxWebViewSession
                .get() === webView
        ) {
            SandboxWebViewSession
                .destroy()
            return
        }

        try {
            webView.stopLoading()

            webView.webChromeClient = null

            // webViewClient cannot be null.
            webView.webViewClient =
                WebViewClient()
                            webView.loadUrl(
                "about:blank"
            )

            webView.clearHistory()

            webView.removeAllViews()

            webView.destroy()
        } catch (_: Exception) {
        }
    }
}
