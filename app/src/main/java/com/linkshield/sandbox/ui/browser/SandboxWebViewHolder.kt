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
fun SandboxWebViewHolder(
    modifier: Modifier = Modifier,
    initialUrl: String,
    onUrlChanged: (String) -> Unit = {}
) {
    val webView =
        remember {
            WebView(
                androidx.compose.ui.platform
                    .LocalContext.current
            ).apply {
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
        update = {
            if (
                initialUrl.isNotBlank() &&
                it.url.isNullOrBlank()
            ) {
                it.loadUrl(initialUrl)
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            // Keep the WebView instance alive while
            // the navigation/session owner is active.
            // Final destruction is handled by the
            // owning browser container.
        }
    }
}
