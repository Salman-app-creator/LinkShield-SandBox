package com.linkshield.sandbox.ui.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView

class SandboxBrowserSessionManager(
    private val context: Context
) {

    fun getOrCreate(
        onUrlChanged: (String) -> Unit = {}
    ): WebView {

        val existing =
            SandboxWebViewSession.get()

        if (existing != null) {
            return existing
        }

        CookieManager
            .getInstance()
            .setAcceptCookie(true)

        return SandboxWebViewFactory
            .create(
                context = context,
                onUrlChanged = onUrlChanged
            )
    }

    fun load(
        url: String
    ) {
        val webView =
            SandboxWebViewSession.get()
                ?: return

        if (url.isBlank()) {
            return
        }

        if (
            webView.url != url
        ) {
            webView.loadUrl(url)
        }
    }

    fun currentUrl(): String {
        return SandboxWebViewSession
            .currentUrl()
    }

    fun goBack(): Boolean {
        return SandboxWebViewSession
            .goBack()
    }

    fun goForward(): Boolean {
        return SandboxWebViewSession
            .goForward()
    }

    fun reload() {
        SandboxWebViewSession.reload()
    }

    fun destroy() {
        SandboxWebViewSession.destroy()
    }
}
