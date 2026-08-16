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
fun SandboxWebViewContainer(
    modifier: Modifier = Modifier,
    initialUrl: String,
    onUrlChanged: (String) -> Unit = {}
) {
    val context =
        androidx.compose.ui.platform
            .LocalContext.current

    val webView =
        remember {
            SandboxWebViewSession
                .get()
                ?: WebView(context).apply {
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

                    SandboxWebViewSession
                        .attach(this)
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
            // It must survive navigation to Grabber.
        }
    }
}
