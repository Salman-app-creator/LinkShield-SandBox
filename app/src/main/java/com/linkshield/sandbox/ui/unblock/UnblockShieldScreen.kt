package com.linkshield.sandbox.ui.unblock

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.adblock.AdBlockEngine
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.dns.DohProvider
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
    onMediaFound: (String) -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    var urlText by remember { mutableStateOf("https://google.com") }
    var currentWebUrl by remember { mutableStateOf("https://google.com") }
    
    val dohProviders = remember { DohProvider.values().toList() }
    var selectedProvider by remember { mutableStateOf(dnsManager.getCurrentProvider()) }
    var isServerMenuExpanded by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val adBlockEngine = remember { AdBlockEngine.getInstance() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                
                // ROW 1: Logo & Right Controls (Wireframe Exact Match)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LOGO Section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "App Logo",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Controls Section
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
                    ) {
                        // [🛡️ Shield ON]
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Shield ON",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // [🛡️ Server Dropdown]
                        Box {
                            OutlinedButton(
                                onClick = { isServerMenuExpanded = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Server",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = selectedProvider.displayName.split(" ").first(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand",
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = isServerMenuExpanded,
                                onDismissRequest = { isServerMenuExpanded = false }
                            ) {
                                dohProviders.forEach { provider ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = provider.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = if (provider == selectedProvider) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedProvider = provider
                                            dnsManager.enableDoh(provider)
                                            isServerMenuExpanded = false
                                            webViewInstance?.reload()
                                        }
                                    )
                                }
                            }
                        }

                        // [Trial: 30d Left]
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Trial: 30d Left",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // [Theme Switch Button]
                        IconButton(
                            onClick = { onToggleTheme() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Theme Toggle",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ROW 2: Navigation Controls & URL Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { if (webViewInstance?.canGoForward() == true) webViewInstance?.goForward() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    BasicTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = {
                            var formatted = urlText.trim()
                            if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                                formatted = "https://$formatted"
                            }
                            currentWebUrl = formatted
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Go", fontSize = 12.sp)
                    }
                }
            }
        }

        // DYNAMIC TAB CONTENT AREA (WebView)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewInstance = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    }

                    addJavascriptInterface(LinkShieldBridge { url ->
                        onMediaFound(url)
                    }, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url ?: return false
                            val scheme = url.scheme?.lowercase() ?: return false
                            
                            if (scheme == "http" || scheme == "https") return false
                            
                            if (scheme.startsWith("snssdk") || scheme == "intent" || scheme == "market" || 
                                scheme.startsWith("instagram") || scheme.startsWith("fb") ||
                                scheme.startsWith("tiktok")) {
                                return true
                            }
                            
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null

                            // AdBlock Filter Engine Check
                            if (adBlockEngine.shouldBlock(url)) {
                                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                            }

                            if (!dnsManager.isDohEnabled()) return null
                            if (!url.startsWith("http://") && !url.startsWith("https://")) return null
                            if (url.contains("google.com") || url.contains("googleapis.com") || url.contains("gstatic.com")) {
                                return super.shouldInterceptRequest(view, request)
                            }

                            return try {
                                val client = dnsManager.getClient()
                                val builder = Request.Builder().url(url)
                                    .header("User-Agent", settings.userAgentString)
                                    .header("Accept-Language", "en-US,en;q=0.9")

                                request.requestHeaders.forEach { (k, v) ->
                                    if (!k.equals("User-Agent", ignoreCase = true)) {
                                        builder.addHeader(k, v)
                                    }
                                }

                                val response = client.newCall(builder.build()).execute()
                                val contentType = response.header("content-type", "text/html") ?: "text/html"
                                val mimeType = contentType.split(";")[0].trim()
                                val encoding = if (contentType.contains("charset=")) {
                                    contentType.substringAfter("charset=").substringBefore(";").trim()
                                } else "utf-8"

                                val stream = response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))
                                WebResourceResponse(mimeType, encoding, stream)
                            } catch (_: Exception) {
                                super.shouldInterceptRequest(view, request)
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null) urlText = url
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
                if (webView.url != currentWebUrl && currentWebUrl.isNotEmpty()) {
                    webView.loadUrl(currentWebUrl)
                }
            }
        )
    }
}
