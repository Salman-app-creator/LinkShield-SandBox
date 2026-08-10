package com.linkshield.sandbox.ui.unblock

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.JavascriptInterface
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

// ─────────────────────────────────────────────────────────────────────────────
// Hosts / path fragments that must NEVER be intercepted — streaming media.
// Intercepting these causes "Playback ID" / bot-detection errors on YouTube etc.
// ─────────────────────────────────────────────────────────────────────────────
private val STREAM_HOST_FRAGMENTS = listOf(
    "googlevideo.com",
    "videoplayback",
    "googlevideo",
    "youtubei.googleapis.com",
    "manifest.googlevideo.com"
)
private val STREAM_PATH_EXTENSIONS = listOf(
    ".m3u8", ".ts", ".mp4", ".mp3", ".webm", ".m4s",
    ".aac", ".ogg", ".flac", ".opus"
)
private val STATIC_SKIP_EXTENSIONS = listOf(
    ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
    ".ico", ".woff", ".woff2", ".ttf", ".eot"
)

// ─────────────────────────────────────────────────────────────────────────────
// JS bridge: injected after page load to extract the first <video> src.
// The result is posted back via the `VideoExtractor` JS interface.
// ─────────────────────────────────────────────────────────────────────────────
private const val JS_VIDEO_EXTRACTOR = """
(function() {
  var v = document.querySelector('video');
  var src = '';
  if (v) {
    src = v.src || '';
    if (!src) {
      var s = v.querySelector('source');
      if (s) src = s.src || '';
    }
  }
  if (src && src.startsWith('http')) {
    VideoExtractor.onVideoFound(src);
  }
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnblockShieldScreen(
    initialUrl:           String?  = null,
    dnsManager:           DnsManager,
    onUrlChanged:         (String) -> Unit = {},
    isDarkTheme:          Boolean  = true,
    onToggleTheme:        () -> Unit = {},
    onShieldStateChanged: (Boolean) -> Unit = {}
) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    val startUrl        = initialUrl ?: "https://www.google.com"
    var urlText         by remember { mutableStateOf(startUrl) }
    var currentUrl      by remember { mutableStateOf(startUrl) }
    var isLoading       by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0f) }
    var canGoBack       by remember { mutableStateOf(false) }
    var canGoForward    by remember { mutableStateOf(false) }
    var showDnsMenu     by remember { mutableStateOf(false) }
    var isDohEnabled    by remember { mutableStateOf(dnsManager.isDohEnabled()) }

    // Extracted HTML5 video URL (from JS bridge) — surfaced to Grabber via onUrlChanged
    var extractedVideoUrl by remember { mutableStateOf<String?>(null) }

    // WebView instance kept in memory for backstack across tab switches
    var webView by remember { mutableStateOf<WebView?>(null) }

    val shieldColor by animateColorAsState(
        targetValue   = if (isDohEnabled) Color(0xFF00F0FF) else Color(0xFF90A4AE),
        animationSpec = tween(durationMillis = 300),
        label         = "shieldColor"
    )

    // Restore shield state from persistence on first composition
    LaunchedEffect(Unit) {
        if (dnsManager.isShieldPersistedOn() && !dnsManager.isDohEnabled()) {
            try {
                dnsManager.enableDoh(DnsManager.DnsProvider.CLOUDFLARE)
                isDohEnabled = true
                onShieldStateChanged(true)
            } catch (_: Exception) {}
        } else if (dnsManager.isDohEnabled()) {
            onShieldStateChanged(true)
        }
    }

    // Propagate URL changes to parent (Grabber auto-fill)
    LaunchedEffect(currentUrl) {
        onUrlChanged(currentUrl)
    }

    // ── ROOT LAYOUT — zero extra padding, WebView gets everything ────────────
    Column(modifier = Modifier.fillMaxSize()) {

        // ── SINGLE-ROW HEADER ─────────────────────────────────────────────────
        //  LEFT:   Shield icon + "LinkShield" label
        //  CENTER: URL omnibox (weight 1f)
        //  RIGHT:  ● Sandbox Active pill + theme toggle
        Surface(
            modifier       = Modifier.fillMaxWidth(),
            color          = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ── LEFT: Logo + Name ──────────────────────────────────
                    Icon(
                        imageVector        = Icons.Default.Shield,
                        contentDescription = "LinkShield",
                        tint               = shieldColor,
                        modifier           = Modifier.size(20.dp)
                    )
                    Text(
                        text       = "LinkShield",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        fontSize   = 12.sp
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    // ── Nav: Back / Forward / Refresh ──────────────────────
                    IconButton(
                        onClick  = { webView?.goBack() },
                        enabled  = canGoBack,
                        modifier = Modifier
                            .size(34.dp)
                            .alpha(if (canGoBack) 1f else 0.3f)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick  = { webView?.goForward() },
                        enabled  = canGoForward,
                        modifier = Modifier
                            .size(34.dp)
                            .alpha(if (canGoForward) 1f else 0.3f)
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick  = { webView?.reload() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier           = Modifier.size(18.dp)
                        )
                    }

                    // ── CENTER: URL Omnibox (pill, weight 1f) ─────────────
                    OutlinedTextField(
                        value         = urlText,
                        onValueChange = { urlText = it },
                        modifier      = Modifier
                            .weight(1f)
                            .height(40.dp),
                        placeholder   = {
                            Text(
                                "Search or enter URL",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        },
                        trailingIcon  = {
                            if (urlText.isNotEmpty()) {
                                IconButton(
                                    onClick  = { urlText = "" },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier           = Modifier.size(14.dp)
                                    )
                                }
                            }
                        },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction    = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                val nav = when {
                                    urlText.startsWith("http") -> urlText
                                    urlText.contains(".")     -> "https://$urlText"
                                    else -> "https://www.google.com/search?q=${
                                        java.net.URLEncoder.encode(urlText, "UTF-8")
                                    }"
                                }
                                webView?.loadUrl(nav)
                            }
                        ),
                        shape    = RoundedCornerShape(20.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        colors   = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            unfocusedBorderColor    = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    )

                    // ── RIGHT: Sandbox Active pill ─────────────────────────
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDohEnabled)
                            Color(0xFF00F0FF).copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Dot indicator
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (isDohEnabled) Color(0xFF00FF94) else Color(0xFF90A4AE),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                            Text(
                                text      = if (isDohEnabled) "Active" else "Off",
                                fontSize  = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color     = if (isDohEnabled) Color(0xFF00F0FF) else Color(0xFF90A4AE)
                            )
                        }
                    }

                    // ── Shield DNS toggle ──────────────────────────────────
                    Box {
                        IconButton(
                            onClick  = { showDnsMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Shield,
                                contentDescription = if (isDohEnabled) "Shield ON" else "Shield OFF",
                                tint               = shieldColor,
                                modifier           = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded          = showDnsMenu,
                            onDismissRequest  = { showDnsMenu = false }
                        ) {
                            Text(
                                "DNS Shield",
                                style      = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                                    onShieldStateChanged(false)
                                    showDnsMenu  = false
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

                            DnsManager.DnsProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        try {
                                            dnsManager.enableDoh(provider)
                                            isDohEnabled = true
                                            onShieldStateChanged(true)
                                            showDnsMenu  = false
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

                    // ── Theme toggle ───────────────────────────────────────
                    IconButton(
                        onClick  = onToggleTheme,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector        = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint               = if (isDarkTheme) Color(0xFFFFB300) else Color(0xFF5F6B7A),
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }

                // Loading progress bar — 2dp strip, no extra vertical space
                AnimatedVisibility(visible = isLoading) {
                    LinearProgressIndicator(
                        progress   = { loadingProgress },
                        modifier   = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color      = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        // ── WEBVIEW — fills ALL remaining vertical space ───────────────────────
        // The WebView instance is preserved in `webView` state so switching tabs
        // and returning does NOT reload or reset the page.
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
                            javaScriptEnabled   = true
                            domStorageEnabled   = true
                            cacheMode           = WebSettings.LOAD_DEFAULT
                            userAgentString     = WebSettings.getDefaultUserAgent(ctx)
                            setSupportZoom(true)
                            builtInZoomControls  = true
                            displayZoomControls  = false
                            mixedContentMode     = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            loadWithOverviewMode = true
                            useWideViewPort      = true
                            mediaPlaybackRequiresUserGesture = false
                        }

                        // JS bridge for HTML5 video extraction fallback
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onVideoFound(src: String) {
                                    if (src.isNotBlank() && src.startsWith("http")) {
                                        extractedVideoUrl = src
                                        // Bubble up so Grabber can use it as sharedUrl
                                        onUrlChanged(src)
                                    }
                                }
                            },
                            "VideoExtractor"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadingProgress = newProgress / 100f
                                isLoading       = newProgress < 100
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view:    WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                request?.url?.toString()?.let {
                                    currentUrl = it
                                    urlText    = it
                                }
                                return false
                            }

                            override fun onPageStarted(
                                view:    WebView?,
                                url:     String?,
                                favicon: Bitmap?
                            ) {
                                isLoading = true
                                url?.let {
                                    currentUrl = it
                                    urlText    = it
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading    = false
                                canGoBack    = view?.canGoBack()    ?: false
                                canGoForward = view?.canGoForward() ?: false

                                // Inject JS video extractor on every page load
                                view?.evaluateJavascript(JS_VIDEO_EXTRACTOR, null)
                            }

                            /**
                             * DoH-based ISP unblock via OkHttp.
                             *
                             * CRITICAL STREAMING FIX:
                             * Requests to streaming CDNs (googlevideo.com, .m3u8, .ts, .mp4, etc.)
                             * are passed through to WebView's native socket handler unchanged.
                             * Intercepting these causes "Playback ID" errors and bot detection.
                             * Static assets (images, fonts) are also skipped for performance.
                             */
                            override fun shouldInterceptRequest(
                                view:    WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                if (!dnsManager.isDohEnabled()) return null

                                val url = request?.url?.toString() ?: return null
                                if (!url.startsWith("http")) return null

                                val urlLower = url.lowercase()

                                // Never intercept streaming media or bot-detected CDN paths
                                if (STREAM_HOST_FRAGMENTS.any { urlLower.contains(it) }) return null
                                if (STREAM_PATH_EXTENSIONS.any { urlLower.contains(it) }) return null
                                if (STATIC_SKIP_EXTENSIONS.any  { urlLower.contains(it) }) return null

                                return try {
                                    val reqBuilder = Request.Builder().url(url)
                                    request.requestHeaders.forEach { (key, value) ->
                                        if (key.equals("host", ignoreCase = true)) return@forEach
                                        try { reqBuilder.addHeader(key, value) } catch (_: Exception) {}
                                    }

                                    val response = dnsManager.getClient()
                                        .newCall(reqBuilder.build())
                                        .execute()

                                    if (!response.isSuccessful) return null

                                    val contentType = response.body?.contentType()
                                    val mimeType    = if (contentType != null)
                                        "${contentType.type}/${contentType.subtype}"
                                    else "text/html"
                                    val charset     = contentType?.charset()?.name()

                                    val responseHeaders = mutableMapOf<String, String>()
                                    response.headers.forEach { (k, v) ->
                                        responseHeaders[k] = v
                                    }

                                    WebResourceResponse(
                                        mimeType,
                                        charset,
                                        response.code,
                                        response.message.ifEmpty { "OK" },
                                        responseHeaders,
                                        response.body?.byteStream()
                                    )
                                } catch (_: Exception) {
                                    null    // fall back to WebView native handling
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

    // Hardware back key → WebView history (not app exit)
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
