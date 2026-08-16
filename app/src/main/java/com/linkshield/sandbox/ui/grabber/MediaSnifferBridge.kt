package com.linkshield.sandbox.ui.grabber

import android.webkit.WebView

object MediaSnifferBridge {

    fun attach(
        webView: WebView,
        titleProvider: () -> String = {
            webView.title.orEmpty()
        },
        urlProvider: () -> String = {
            webView.url.orEmpty()
        }
    ) {
        val sniffer =
            WebSnifferManager { item ->
                MediaSnifferState.publish(
                    item
                )
            }

        webView.webViewClient =
            sniffer.createClient(
                pageTitle = titleProvider,
                pageUrl = urlProvider
            )
    }

    fun clear() {
        MediaSnifferState.clear()
    }
}
