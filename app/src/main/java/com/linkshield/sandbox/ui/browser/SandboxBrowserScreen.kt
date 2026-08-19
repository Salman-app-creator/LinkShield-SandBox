package com.linkshield.sandbox.ui.browser

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxBrowserScreen(
    onOpenGrabber: () -> Unit,
    onExit: () -> Unit,
    initialUrl: String = ""
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by rememberSaveable { mutableStateOf("LinkShield Sandbox") }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }

    BackHandler {
        if (canBack) webView?.goBack() else onExit()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = { if (canBack) webView?.goBack() else onExit() }) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { webView?.goBack() }, enabled = canBack) { Icon(Icons.Default.ArrowBack, "Previous") }
                IconButton(onClick = { webView?.goForward() }, enabled = canForward) { Icon(Icons.Default.ArrowForward, "Next") }
                IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
                IconButton(onClick = onOpenGrabber) { Icon(Icons.Default.Download, "Grabber") }
            }
        )
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            title = view?.title?.takeIf { it.isNotBlank() } ?: "LinkShield Sandbox"
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            canBack = view?.canGoBack() == true
                            canForward = view?.canGoForward() == true
                            title = view?.title?.takeIf { it.isNotBlank() } ?: "LinkShield Sandbox"
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val scheme = request?.url?.scheme?.lowercase()
                            return scheme != null && scheme !in setOf("http", "https")
                        }
                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            runCatching { view?.destroy() }
                            webView = null
                            return true
                        }
                    }
                    webChromeClient = WebChromeClient()
                    webView = this
                    if (initialUrl.isNotBlank()) loadUrl(initialUrl)
                }
            },
            update = { webView = it }
        )
    }
}
