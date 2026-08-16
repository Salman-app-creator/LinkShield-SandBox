package com.linkshield.sandbox.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object SandboxWebViewFactory {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        onUrlChanged: (String) -> Unit = {}
    ): WebView {

        return WebView(
            context.applicationContext
        ).apply {

            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true

                mediaPlaybackRequiresUserGesture =
                    false

                cacheMode =
                    WebSettings.LOAD_DEFAULT

                allowFileAccess = false
                allowContentAccess = true

                builtInZoomControls = false
                displayZoomControls = false

                javaScriptCanOpenWindowsAutomatically =
                    true
            }

            webViewClient =
                createSandboxBrowserClient(
                    onStarted = {
                        onUrlChanged(it)
                    },
                    onFinished = {
                            url,
                            _,
                            _,
                            _ ->
                        onUrlChanged(url)
                    }
                )

            webChromeClient =
                android.webkit.WebChromeClient()

            setBackgroundColor(
                android.graphics.Color.TRANSPARENT
            )

            SandboxWebViewSession.attach(
                this
            )
        }
    }
}
