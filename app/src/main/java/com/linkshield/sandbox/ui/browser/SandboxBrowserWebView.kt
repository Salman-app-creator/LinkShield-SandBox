package com.linkshield.sandbox.ui.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxBrowserWebView(
    modifier: Modifier = Modifier,
    initialUrl: String = "",
    onPageChanged: (String) -> Unit = {}
) {
    val context =
        androidx.compose.ui.platform.LocalContext.current

    val webView =
        remember {
            SandboxBrowserSessionManager(
                context
            ).getOrCreate(
                onUrlChanged = onPageChanged
            )
        }

    AndroidView(
        modifier = modifier,
        factory = {
            webView.apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                if (
                    initialUrl.isNotBlank() &&
                    url.isNullOrBlank()
                ) {
                    loadUrl(initialUrl)
                }
            }
        }
    )

    DisposableEffect(webView) {
        onDispose {
            // Keep session alive while navigating
            // to Grabber.
        }
    }
}
