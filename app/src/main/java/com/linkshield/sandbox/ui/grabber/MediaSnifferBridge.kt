package com.linkshield.sandbox.ui.grabber

import android.webkit.WebView

object MediaSnifferBridge {

    fun attach(
        webView: WebView
    ) {
        WebSnifferManager.attach(
            webView = webView
        )
    }

    fun detach(
        webView: WebView
    ) {
        WebSnifferManager.detach(
            webView
        )
    }
}
