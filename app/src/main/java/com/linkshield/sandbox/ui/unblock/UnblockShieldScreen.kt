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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.dns.DnsManager
import okhttp3.Request

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnblockShieldScreen(
    initialUrl: String? = null,
    dnsManager: DnsManager,
    onUrlChanged: (String) -> Unit = {},
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
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

    // Shield toggle — restored from persisted state
    var isDohEnabled by remember { mutableStateOf(dnsManager.isDohEnabled()) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Animated shield icon tint
    val shieldColor by animateColorAsState(
        targetValue = if (isDohEnabled) Color(0xFF00F0FF) else Color(0xFF90A4AE),
        animationSpec = tween(durationMillis = 300),
        label = "shieldColor"
    )

    // Restore DoH on first compose if it was persisted ON
    LaunchedEffect(Unit) {
        if (dnsManager.isShieldPersistedOn() && !dnsManager.isDohEnabled()) {
            try {
                dnsManager.enableDoh(DnsManager.DnsProvider.CLOUDFLARE)
                isDohEnabled = true
            } catch (_: Exception) {}
        }
    }

    // Notify parent of URL changes for Grabber auto-fill
    LaunchedEffect(currentUrl) {
        onUrlChanged(currentUrl)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── TOP TOOLBAR ────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp
        ) {
            Column {
                // Shield status indicator strip
                if (isDohEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF00F0FF).copy(alpha = 0.10f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SHIELD ACTIVE — DoH: ${dnsManager.getCurrentProvider().displayName}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color(0xFF00F0FF),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF90A4AE).copy(alpha = 0.07f)
                    ) {
                        Text(
                            text = "SHIELD INACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color(0xFF90A4AE),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Main toolbar row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Back
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier
                            .size(40.dp)
                            .alpha(if (canGoBack) 1f else 0.35f)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Forward
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier
                            .size(40.dp)
                            .alpha(if (canGoForward) 1f else 0.35f)
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Refresh / Stop
                    IconButton(
                        onClick = { webView?.reload() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // URL Omnibox
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        placeholder = {
                            Text(
                                "Search or type URL",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (urlText.isNotEmpty()) {
                                IconButton(
                                    onClick = { urlText = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
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
                                    else -> "https://www.google.com/search?q=${
                                        java.net.URLEncoder.encode(urlText, "UTF-8")
                                    }"
                                }
                                webView?.loadUrl(url)
                            }
                        ),
                        shape = RoundedCornerShape(22.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    )

                    // ── Shield Toggle Button ─────────────────────────────────
                    Box {
                        IconButton(
                            onClick = { showDnsMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = if (isDohEnabled) "Shield ON" else "Shield OFF",
                                tint = shieldColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showDnsMenu,
                            onDismissRequest = { showDnsMenu = false }
                        ) {
                            Text(
                                "DNS Shield Protection",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            HorizontalDivider()

                            // Disable option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Disabled",
                                        color = if (!isDohEnabled)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                },
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
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )

                            HorizontalDivider()

                            // Provider options
                            DnsManager.DnsProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        try {
                                            dnsManager.enableDoh(provider)
                                            isDohEnabled = true
                                            showDnsMenu = false
                                            webView?.reload()
                                        } catch (_: Exception) {}
                                    },
                                    leadingIcon = {
                                        if (isDohEnabled && dnsManager.getCurrentProvider() == provider) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF00F0FF)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Light/Dark theme toggle
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkTheme) "Switch to Light" else "Switch to Dark",
                            tint = if (isDarkTheme) Color(0xFFFFB300) else Color(0xFF5F6B7A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Loading progress bar — compact 2dp strip
                AnimatedVisibility(visible = isLoading) {
                    LinearProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        // ── WEBVIEW — fills ALL remaining space, zero extra padding ──────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
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
                            loadWithOverviewMode = true
                            useWideViewPort = true
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

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
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

                            /**
                             * DoH-based ISP unblock:
                             * When Shield is ON, all WebView network requests are routed
                             * through the OkHttp DoH client, bypassing ISP DNS blocks
                             * and opening restricted sites natively — no VPN required.
                             */
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebR
