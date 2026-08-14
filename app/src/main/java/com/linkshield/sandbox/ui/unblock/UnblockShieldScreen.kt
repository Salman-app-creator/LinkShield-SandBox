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
import androidx.compose.material.icons.filled.Shield
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
import com.linkshield.sandbox.dns.DnsManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.ByteArrayInputStream
import java.net.InetAddress

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

data class DohServer(val name: String, val url: String, val ip1: String, val ip2: String)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnblockShieldScreen(
    dnsManager: DnsManager,
    onMediaFound: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("https://google.com") }
    var currentWebUrl by remember { mutableStateOf("https://google.com") }
    var isShieldEnabled by remember { mutableStateOf(true) }
    var isDarkMode by remember { mutableStateOf(false) }

    // --- Server Selection State ---
    val dohServers = remember {
        listOf(
            DohServer("Cloudflare", "https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1"),
            DohServer("Google DNS", "https://dns.google/dns-query", "8.8.8.8", "8.8.4.4"),
            DohServer("AdGuard (No Ads)", "https://dns.adguard.com/dns-query", "94.140.14.14", "94.140.15.15"),
            DohServer("Quad9 Security", "https://dns.quad9.net/dns-query", "9.9.9.9", "149.112.112.112")
        )
    }
    var selectedServer by remember { mutableStateOf(dohServers[0]) }
    var isServerMenuExpanded by remember { mutableStateOf(false) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Dynamic DoH Client building based on selected server
    val dohClient = remember(selectedServer) {
        val bootstrapClient = OkHttpClient.Builder().build()
        val doh = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(selectedServer.url.toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName(selectedServer.ip1),
                InetAddress.getByName(selectedServer.ip2)
            )
            .build()

        OkHttpClient.Builder()
            .dns(doh)
            .build()
    }

    val standardClient = remember { OkHttpClient.Builder().build() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // --- Top Header ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                // ROW 1: [ Logo ] | [ Shield Switch ] | [ Server Dropdown ] | [ Theme ] | [ Trial ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Logo
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Logo",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LinkShield",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 2. Shield Switch
                    Switch(
                        checked = isShieldEnabled,
                        onCheckedChange = { isShieldEnabled = it },
                        modifier = Modifier.height(30.dp)
                    )

                    // 3. SERVER SELECTION DROPDOWN BUTTON
                    Box {
                        OutlinedButton(
                            onClick = { isServerMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Server",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedServer.name.take(8) + "..",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Expand",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isServerMenuExpanded,
                            onDismissRequest = { isServerMenuExpanded = false }
                        ) {
                            dohServers.forEach { server ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = server.name,
                                            fontWeight = if (server == selectedServer) FontWeight.Bold else FontWeight.Normal,
                                            color = if (server == selectedServer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedServer = server
                                        isServerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 4. Mode Toggle Switch
                    IconButton(
                        onClick = { isDarkMode = !isDarkMode },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme Toggle",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 5. Trial Display Tag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Trial 30d",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ROW 2: Navigation Controls & Address Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { webViewInstance?.goForward() },
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

                    // Address Bar
                    BasicTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Go", fontSize = 12.sp)
                    }
                }
            }
        }

        // --- WebView Section ---
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

                            // Disable proxy intercept for Google Domain to avoid CAPTCHA triggers
                            if (!isShieldEnabled || url.contains("google.com") || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                                return super.shouldInterceptRequest(view, request)
                            }

                            return try {
                                val activeClient = if (isShieldEnabled) dohClient else standardClient
                                
                                // Pass complete Browser Headers to prevent bot detection
                                val builder = Request.Builder().url(url)
                                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                    .header("Accept-Language", "en-US,en;q=0.9")

                                request.requestHeaders.forEach { (k, v) ->
                                    if (!k.equals("User-Agent", ignoreCase = true)) {
                                        builder.addHeader(k, v)
                                    }
                                }

                                val response = activeClient.newCall(builder.build()).execute()
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
