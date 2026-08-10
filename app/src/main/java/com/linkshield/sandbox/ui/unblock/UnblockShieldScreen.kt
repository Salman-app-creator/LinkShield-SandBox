package com.linkshield.sandbox.ui.unblock

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.dns.DnsManager

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnblockShieldScreen(initialUrl: String? = null) {
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

    val dnsManager = remember { DnsManager(context) }
    var isDohEnabled by remember { mutableStateOf(dnsManager.isDohEnabled()) }

    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // WebView fills the entire content area edge-to-edge
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
                    }

                    loadUrl(currentUrl)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Chrome-style thin progress bar at very top
        AnimatedVisibility(
            visible = isLoading,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { loadingProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }

        // Floating Address Bar (pill-shaped, translucent)
        // statusBarsPadding ensures it sits below the system status bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                placeholder = {
                    Text(
                        "Search or enter URL",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (urlText.isNotEmpty()) {
                        IconButton(onClick = { urlText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
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
                        val url = if (urlText.startsWith("http")) urlText else "https://$urlText"
                        webView?.loadUrl(url)
                    }
                ),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        }

        // Floating Navigation Dock (Back, Forward, Refresh, DoH toggle)
        // Positioned above the app's bottom navigation bar with safe insets
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
                .wrapContentWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.alpha(if (canGoBack) 1f else 0.4f)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }

                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.alpha(if (canGoForward) 1f else 0.4f)
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                }

                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }

                // DoH Toggle with dropdown
                Box {
                    IconButton(onClick = { showDnsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "DNS",
                            tint = if (isDohEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
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
                                    dnsManager.enableDoh(provider)
                                    isDohEnabled = true
                                    showDnsMenu = false
                                    webView?.reload()
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
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
