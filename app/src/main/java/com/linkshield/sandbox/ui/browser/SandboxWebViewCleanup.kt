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

        if (SandboxWebViewSession.get() === webView) {
            SandboxWebViewSession.destroy()
            return
        }

        try {
            webView.stopLoading()

            webView.webChromeClient = null

            // webViewClient cannot be null – assign a no-op client first,
            // then navigate to blank to stop any in-flight requests.
            // FIX #13: Original code had "webView.loadUrl(...)" grotesquely
            // over-indented directly after "WebViewClient()" on the next line,
            // making it look visually fused to the assignment. Kotlin's
            // newline-as-semicolon rule technically compiled it as two separate
            // statements, but the ambiguity was a latent maintenance defect.
            // Separated into two clear, properly indented statements.
            webView.webViewClient = WebViewClient()
            webView.loadUrl("about:blank")

            webView.clearHistory()

            webView.removeAllViews()

            webView.destroy()
        } catch (_: Exception) {
        }
    }
}
