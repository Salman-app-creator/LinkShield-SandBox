package com.linkshield.sandbox.ui.unblock

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.dns.DnsManager
import okhttp3.Request

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnblockShieldScreen(
    initialUrl: String? = null,
    dnsManager: DnsManager,
    onUrlChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val startUrl = initialUrl ?: "https://www.google.com"
    var urlText by remember { mutableStateOf(startUrl) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showDnsMenu by remember { mutableStateOf(false) }

    var isDohEnabled by remember { mutableStateOf(dnsManager.isDohEnabled()) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Auto-enable Cloudflare DoH on first launch
    LaunchedEffect(Unit) {
        if (!dnsManager.isDohEnabled()) {
            try {
                dnsManager.enableDoh(DnsManager.DnsProvider.CLOUDFLARE)
                isDohEnabled = true
                webView?.reload()
            } catch (_: Exception) {
            }
        }
    }

    // Notify parent of URL changes for Grabber auto-paste
    LaunchedEffect(currentUrl) {
        onUrlChanged(currentUrl)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TOP TOOLBAR — Chrome style, fixed at top
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Back
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.alpha(if (canGoBack) 1f else 0.4f)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Forward
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.alpha(if (canGoForward) 1f else 0.4f)
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Refresh
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // URL Bar (Omnibox style)
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        placeholder = {
                            Text(
                                "Search or type URL",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (urlText.isNotEmpty()) {
                                IconButton(
                                    onClick = { urlText = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                val url = when {
                                    urlText.startsWith("http") -> urlText
                                    urlText.contains(".") -> "https://$urlText"
                                    else -> "https://www.google.com/search?q=$urlText"
                                }
                                webView?.loadUrl(url)
                            }
                        ),
                        shape = RoundedCornerShape(24.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    )

                    // DNS Shield Toggle
                    Box {
                        IconButton(
                            onClick = { showDnsMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "DNS",
                                tint = if (isDohEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showDnsMenu,
                            onDismissRequest = { showDnsMenu = false }
                        ) {
                            Text(
                                "DNS Protection",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            Divider()

                            DropdownMenuItem(
                                text = { Text("Disabled") },
                                onClick = {
                                    dnsManager.disableDoh()
                                    isDohEnabled = false
                                    showDnsMenu = false
                                    webView?.reload()
                                },
                                leadingIcon = {
                                    if (!isDohEnabled) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )

                            DnsManager.DnsProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        try {
                                            dnsManager.enableDoh(provider)
                                            isDohEnabled = true
                                            showDnsMenu = false
                                            webView?.reload()
                                        } catch (_: Exception) {
                                        }
                                    },
                                    leadingIcon = {
                                        if (isDohEnabled && dnsManager.getCurrentProvider() == provider) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Progress bar below toolbar
                AnimatedVisibility(visible = isLoading) {
                    LinearProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        // WebView fills remaining space
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = WebSettings.getDefaultUserAgent(ctx)
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress / 100f
                                isLoading = newProgress < 100
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                request?.url?.toString()?.let {
                                    currentUrl = it
                                    urlText = it
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                url?.let {
                                    currentUrl = it
                                    urlText = it
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }

                            // Proxy requests through DoH-enabled OkHttp to unblock restricted sites
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                if (!dnsManager.isDohEnabled()) return null

                                val url = request?.url?.toString() ?: return null
                                if (!url.startsWith("http")) return null

                                return try {
                                    val okBuilder = Request.Builder().url(url)
                                    request.requestHeaders.forEach { (key, value) ->
                                        if (key.equals("host", ignoreCase = true)) return@forEach
                                        okBuilder.addHeader(key, value)
                                    }

                                    val response = dnsManager.getClient()
                                        .newCall(okBuilder.build())
                                        .execute()

                                    if (!response.isSuccessful) return null

                                    val contentType = response.body?.contentType()
                                    val mimeType = if (contentType != null) {
                                        "${contentType.type}/${contentType.subtype}"
                                    } else "text/html"
                                    val charset = contentType?.charset()?.name()

                                    val responseHeaders = mutableMapOf<String, String>()
                                    response.headers.forEach { header ->
                                        responseHeaders[header.first] = header.second
                                    }

                                    WebResourceResponse(
                                        mimeType,
                                        charset,
                                        response.code,
                                        response.message,
                                        responseHeaders,
                                        response.body?.byteStream()
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }

                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
