package com.linkshield.sandbox.ui.grabber

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.webkit.WebView

class MediaSnifferLifecycle(
    private val webViewProvider: () -> WebView?
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
            webView.webViewClient = null
        }

        MediaSnifferBridge.clear()
    }
}
