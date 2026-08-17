package com.linkshield.sandbox.ui.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxWebViewHolder(
    modifier: Modifier = Modifier,
    initialUrl: String,
    onUrlChanged: (String) -> Unit = {}
) {
    // IMPORTANT:
    // LocalContext.current is a composable value, so it must be
    // obtained outside remember{}.
    val context = LocalContext.current

    val webView = remember(context) {
        WebView(context).apply {
            layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture =
                false

            webViewClient =
                createSandboxWebViewClient(
                    onPageChanged =
                        onUrlChanged
                )
                            if (initialUrl.isNotBlank()) {
                loadUrl(initialUrl)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            webView
        },
        update = { view ->
            if (
                initialUrl.isNotBlank() &&
                view.url.isNullOrBlank()
            ) {
                view.loadUrl(initialUrl)
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            // Do not destroy the WebView here.
            // Browser session owns its lifetime.
        }
    }
}
