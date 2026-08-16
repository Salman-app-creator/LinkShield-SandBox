package com.linkshield.sandbox.ui.browser

import android.os.Bundle
import android.webkit.WebBackForwardList
import android.webkit.WebView

class SandboxWebViewStateStore {

    fun save(
        webView: WebView
    ): Bundle {
        return Bundle().apply {
            putString(
                KEY_URL,
                webView.url.orEmpty()
            )

            putString(
                KEY_TITLE,
                webView.title.orEmpty()
            )

            putInt(
                KEY_HISTORY_SIZE,
                webView.copyBackForwardList()
                    .size
            )

            putInt(
                KEY_HISTORY_INDEX,
                webView.copyBackForwardList()
                    .currentIndex
            )
        }
    }

    fun restore(
        webView: WebView,
        state: Bundle?
    ) {
        if (state == null) {
            return
        }

        val url =
            state.getString(
                KEY_URL
            ).orEmpty()

        if (
            url.isNotBlank() &&
            webView.url.isNullOrBlank()
        ) {
            webView.loadUrl(url)
        }
    }

    fun hasHistory(
        webView: WebView
    ): Boolean {
        val history:
            WebBackForwardList =
            webView.copyBackForwardList()

        return history.size > 0
    }

    companion object {
        private const val KEY_URL =
            "webview_state_url"

        private const val KEY_TITLE =
            "webview_state_title"

        private const val KEY_HISTORY_SIZE =
            "webview_history_size"

        private const val KEY_HISTORY_INDEX =
            "webview_history_index"
    }
}
