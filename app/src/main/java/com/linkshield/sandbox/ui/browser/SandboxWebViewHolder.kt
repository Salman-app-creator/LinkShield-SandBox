package com.linkshield.sandbox.ui.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun SandboxWebViewHolder(
    initialUrl: String,
    modifier: Modifier = Modifier,
    onUrlChanged: (String) -> Unit
) {
    val context =
        LocalContext.current

    val webView =
        remember(context) {

            WebView(context).apply {

                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                settings.javaScriptEnabled =
                    true

                settings.domStorageEnabled =
                    true

                settings.mediaPlaybackRequiresUserGesture =
                    false

                webViewClient =
                    createSandboxWebViewClient(
                        onPageChanged = { url ->
                            onUrlChanged(url)
                        }
                    )

                if (
                    initialUrl.isNotBlank()
                ) {
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
                view.loadUrl(
                    initialUrl
                )
            }
        }
    )
    DisposableEffect(Unit) {

        onDispose {

            // Browser session owns
            // the WebView lifetime.
            //
            // Do not destroy the WebView
            // when Compose recomposes.
        }
    }
}
