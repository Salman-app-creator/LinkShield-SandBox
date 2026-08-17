package com.linkshield.sandbox.ui.grabber

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class MediaSnifferLifecycle(
    private val webViewProvider:
        () -> WebView?
) : DefaultLifecycleObserver {

    override fun onStart(
        owner: LifecycleOwner
    ) {
        super.onStart(owner)

        webViewProvider()?.let { webView ->
            MediaSnifferBridge.attach(
                webView = webView
            )
        }
    }

    override fun onStop(
        owner: LifecycleOwner
    ) {
        super.onStop(owner)

        webViewProvider()?.let { webView ->
            webView.stopLoading()
        }
    }
        override fun onDestroy(
        owner: LifecycleOwner
    ) {
        super.onDestroy(owner)

        webViewProvider()?.let { webView ->
            webView.stopLoading()

            // WebViewClient is non-null.
            webView.webViewClient =
                WebViewClient()
        }

        MediaSnifferBridge.clear()
    }
}
