package com.linkshield.sandbox.ui.unblock

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.dns.DnsManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream

const val MEDIA_SNIFFER_JS = """
    (function() {
        function sendToAndroid(url) {
            if (url && (url.includes('.mp4') || url.includes('.m3u8') || url.includes('googlevideo') || url.includes('.webm'))) {
                window.AndroidBridge.processMedia(url);
            }
        }
        
        var origFetch = window.fetch;
        window.fetch = function() {
            var url = arguments[0];
            if (typeof url === 'string') sendToAndroid(url);
            return origFetch.apply(this, arguments);
        };

        var origOpen = window.XMLHttpRequest.prototype.open;
        window.XMLHttpRequest.prototype.open = function(method, url) {
            if (typeof url === 'string') sendToAndroid(url);
            return origOpen.apply(this, arguments);
        };

        setInterval(function() {
            var elements = document.querySelectorAll('video, audio, source');
            elements.forEach(function(el) {
                if (el.src && el.src.startsWith('http')) {
                    sendToAndroid(el.src);
                }
            });
        }, 2000);
    })();
"""

class LinkShieldBridge(private val onMediaDetected: (String) -> Unit) {
    @JavascriptInterface
    fun processMedia(url: String) {
        if (url.isNotEmpty()) {
            onMediaDetected(url)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnblockShieldScreen(
    dnsManager: DnsManager,
    onMediaFound: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("https://google.com") }
    var currentWebUrl by remember { mutableStateOf("https://google.com") }
    
    val fallbackClient = remember { OkHttpClient.Builder().build() }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Top Bar Section ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "App Logo",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
                value = urlText,
                onValueChange = { urlText = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    var formatted = urlText.trim()
                    if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                        formatted = "https://$formatted"
                    }
                    currentWebUrl = formatted
                },
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Go")
            }
        }

        // --- WebView Section ---
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    }

                    addJavascriptInterface(LinkShieldBridge { url ->
                        onMediaFound(url)
                    }, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null

                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                return super.shouldInterceptRequest(view, request)
                            }

                            return try {
                                val builder = Request.Builder().url(url)
                                request.requestHeaders.forEach { (k, v) ->
                                    builder.addHeader(k, v)
                                }

                                val response = fallbackClient.newCall(builder.build()).execute()
                                val contentType = response.header("content-type", "text/html") ?: "text/html"
                                val mimeType = contentType.split(";")[0].trim()
                                val encoding = if (contentType.contains("charset=")) {
                                    contentType.substringAfter("charset=").substringBefore(";").trim()
                                } else "utf-8"

                                val stream = response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))

                                WebResourceResponse(
                                    mimeType,
                                    encoding,
                                    stream
                                )
                            } catch (e: Exception) {
                                super.shouldInterceptRequest(view, request)
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(MEDIA_SNIFFER_JS, null)
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            handler?.proceed()
                        }
                    }

                    loadUrl(currentWebUrl)
                }
            },
            update = { webView ->
                if (webView.url != currentWebUrl) {
                    webView.loadUrl(currentWebUrl)
                }
            }
        )
    }
}
