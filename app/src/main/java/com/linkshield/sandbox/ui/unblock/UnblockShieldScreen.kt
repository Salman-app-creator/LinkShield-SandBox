package com.linkshield.sandbox.ui.unblock

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
// Chrome Mobile User-Agent — avoids reCAPTCHA and Error 400 responses.
// Matches a real mid-2024 Chrome on Android device fingerprint.
// ─────────────────────────────────────────────────────────────────────────────
private const val CHROME_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/124.0.0.0 Mobile Safari/537.36"

private const val TAG = "UnblockShieldScreen"

// ─────────────────────────────────────────────────────────────────────────────
// Streaming — hosts and path fragments that MUST bypass DoH interception.
// Intercepting these causes "Playback ID" / bot-detection errors on YouTube.
// ─────────────────────────────────────────────────────────────────────────────
private val STREAM_HOST_BYPASS = listOf(
    "googlevideo.com",
    "manifest.googlevideo.com",
    "youtubei.googleapis.com",
    "videoplayback",
    "googlevideo",
    "c.youtube.com",
    "i.ytimg.com"
)

private val STREAM_EXTENSION_BYPASS = listOf(
    ".m3u8", ".ts", ".mp4", ".mp3", ".webm",
    ".m4s", ".aac", ".ogg", ".flac", ".opus", ".m4v"
)

private val STATIC_ASSET_BYPASS = listOf(
    ".jpg", ".jpeg", ".png", ".gif", ".webp",
    ".svg", ".ico", ".woff", ".woff2", ".ttf", ".eot"
)

// ─────────────────────────────────────────────────────────────────────────────
// JavaScript injected after every page load.
// Extracts the first <video> src and posts it back via the VideoExtractor
// JavascriptInterface so MediaGrabberScreen can use it as a fallback URL.
// ─────────────────────────────────────────────────────────────────────────────
private const val JS_HTML5_EXTRACTOR = """
(function() {
    try {
        var video = document.querySelector('video');
        if (!video) return;
        var src = video.src || '';
        if (!src || src === '') {
            var source = video.querySelector('source');
            if (source) src = source.src || '';
        }
        if (!src || src === '') {
            var sources = document.querySelectorAll('source');
            for (var i = 0; i < sources.length; i++) {
                if (sources[i].src && sources[i].src.startsWith('http')) {
                    src = sources[i].src;
                    break;
                }
            }
        }
        if (src && src.startsWith('http')) {
            VideoExtractor.onVideoFound(src);
        }
    } catch(e) {}
})();
"""

// ─────────────────────────────────────────────────────────────────────────────
// WebViewState — holds the WebView instance across recompositions/tab switches.
// Keeping the instance in a remember{} block outside AndroidView prevents
// the blank-screen problem caused by AndroidView recreating the view on
// every recomposition or navigation graph back-stack change.
// ─────────────────────────────────────────────────────────────────────────────
class WebViewState(val initialUrl: String) {
    var webView: WebView?           = null
    var savedBundle: Bundle?        = null
    var currentUrl:  String         = initialUrl
    var canGoBack:   Boolean        = false
    var canGoForward: Boolean       = false
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnblockShieldScreen(
    initialUrl:           String?   = null,
    dnsManager:           DnsManager,
    onUrlChanged:         (String) -> Unit  = {},
    onVideoExtracted:     (String) -> Unit  = {},
    isDarkTheme:          Boolean   = true,
    onToggleTheme:        () -> Unit        = {},
    onShieldStateChanged: (Boolean) -> Unit = {}
) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current
    val startUrl     = initialUrl ?: "https://www.google.com"

    // ── Persistent WebView state — survives tab switches ──────────────────────
    // rememberSaveable keeps the WebViewState object alive across recompositions
    // within the same NavBackStackEntry lifetime (i.e. tab switches).
    val webViewState = remember { WebViewState(startUrl) }

    // ── UI state (updated by WebViewClient callbacks) ─────────────────────────
    var urlBarText      by rememberSaveable { mutableStateOf(startUrl) }
    var isLoading       by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0f) }
    var canGoBack       by remember { mutableStateOf(false) }
    var canGoForward    by remember { mutableStateOf(false) }

    // ── Shield / DNS state ────────────────────────────────────────────────────
    var isDohEnabled by remember { mutableStateOf(dnsManager.isDohEnabled()) }
    var showDnsMenu  by remember { mutableStateOf(false) }

    val shieldTint by animateColorAsState(
        targetValue   = if (isDohEnabled) Color(0xFF00F0FF) else Color(0xFF9E9E9E),
        animationSpec = tween(durationMillis = 350),
        label         = "shieldTint"
    )

    // ── Restore shield from persisted prefs on first composition ──────────────
    LaunchedEffect(Unit) {
        if (dnsManager.isShieldPersistedOn() && !dnsManager.isDohEnabled()) {
            try {
                dnsManager.enableDoh(DnsManager.DnsProvider.CLOUDFLARE)
                isDohEnabled = true
                onShieldStateChanged(true)
                Log.d(TAG, "Shield restored from prefs")
            } catch (e: Exception) {
                Log.e(TAG, "Shield restore failed: ${e.message}")
            }
        } else if (dnsManager.isDohEnabled()) {
            onShieldStateChanged(true)
        }
    }

    // ── Notify parent when URL changes (for Grabber auto-fill) ───────────────
    LaunchedEffect(webViewState.currentUrl) {
        onUrlChanged(webViewState.currentUrl)
    }

    // ── Root layout — Column fills entire screen, zero extra padding ──────────
    Column(modifier = Modifier.fillMaxSize()) {

        // ─────────────────────────────────────────────────────────────────────
        // TOP BAR — 2 rows
        // ─────────────────────────────────────────────────────────────────────
        Surface(
            modifier        = Modifier.fillMaxWidth(),
            color           = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp)
            ) {
                // ── ROW 1: Logo | Status Pill | DNS Selector | Theme Toggle ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.SpaceBetween
                ) {
                    // LEFT — App logo + name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Shield,
                            contentDescription = "LinkShield Logo",
                            tint               = shieldTint,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text(
                            text       = "LinkShield",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface,
                            fontSize   = 13.sp
                        )
                    }

                    // RIGHT — Status pill + DNS menu + Theme toggle
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // ── Shield status pill ────────────────────────────────
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDohEnabled)
                                Color(0xFF00F0FF).copy(alpha = 0.14f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 8.dp, vertical = 3.dp
                                ),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            color = if (isDohEnabled)
                                                Color(0xFF00E676)
                                            else
                                                Color(0xFF9E9E9E),
                                            shape = RoundedCornerShape(50)
                                        )
                                )
                                Text(
                                    text       = if (isDohEnabled) "Active" else "Off",
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (isDohEnabled)
                                        Color(0xFF00F0FF)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // ── DNS Provider selector button ──────────────────────
                        Box {
                            IconButton(
                                onClick  = { showDnsMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.Shield,
                                    contentDescription = "DNS Provider",
                                    tint               = shieldTint,
                                    modifier           = Modifier.size(19.dp)
                                )
                            }

                            DropdownMenu(
                                expanded         = showDnsMenu,
                                onDismissRequest = { showDnsMenu = false }
                            ) {
                                Text(
                                    text       = "DNS Shield",
                                    style      = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.padding(
                                        horizontal = 16.dp, vertical = 8.dp
                                    )
                                )
                                HorizontalDivider()

                                // Disable option
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text  = "Disabled",
                                            color = if (!isDohEnabled)
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (!isDohEnabled) {
                                            Icon(
                                                imageVector        = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint               = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    },
                                    onClick = {
                                        dnsManager.disableDoh()
                                        isDohEnabled = false
                                        onShieldStateChanged(false)
                                        showDnsMenu  = false
                                        webViewState.webView?.reload()
                                    }
                                )

                                HorizontalDivider()

                                // All providers
                                DnsManager.DnsProvider.entries.forEach { provider ->
                                    val isSelected = isDohEnabled &&
                                        dnsManager.getCurrentProvider() == provider

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text  = provider.displayName,
                                                color = if (isSelected)
                                                    Color(0xFF00F0FF)
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector        = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint               = Color(0xFF00F0FF)
                                                )
                                            }
                                        },
                                        onClick = {
                                            try {
                                                dnsManager.enableDoh(provider)
                                                isDohEnabled = true
                                                onShieldStateChanged(true)
                                                showDnsMenu  = false
                                                webViewState.webView?.reload()
                                                Log.d(TAG, "Provider switched: ${provider.displayName}")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Provider switch failed: ${e.message}")
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // ── Light / Dark theme toggle ──────────────────────────
                        IconButton(
                            onClick  = onToggleTheme,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector        = if (isDarkTheme)
                                    Icons.Default.LightMode
                                else
                                    Icons.Default.DarkMode,
                                contentDescription = if (isDarkTheme)
                                    "Switch to Light mode"
                                else
                                    "Switch to Dark mode",
                                tint               = if (isDarkTheme)
                                    Color(0xFFFFB300)
                                else
                                    Color(0xFF5F6B7A),
                                modifier           = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // ── ROW 2: Back | Forward | Refresh | URL Bar ─────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Back
                    IconButton(
                        onClick  = { webViewState.webView?.goBack() },
                        enabled  = canGoBack,
                        modifier = Modifier
                            .size(36.dp)
                            .alpha(if (canGoBack) 1f else 0.30f)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier           = Modifier.size(18.dp)
                        )
                    }

                    // Forward
                    IconButton(
                        onClick  = { webViewState.webView?.goForward() },
                        enabled  = canGoForward,
                        modifier = Modifier
                            .size(36.dp)
                            .alpha(if (canGoForward) 1f else 0.30f)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            modifier           = Modifier.size(18.dp)
                        )
                    }

                    // Refresh
                    IconButton(
                        onClick  = { webViewState.webView?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier           = Modifier.size(18.dp)
                        )
                    }

                    // URL address bar — weight(1f) takes all remaining space
                    OutlinedTextField(
                        value         = urlBarText,
                        onValueChange = { urlBarText = it },
                        modifier      = Modifier
                            .weight(1f)
                            .height(44.dp),
                        placeholder   = {
                            Text(
                                text     = "Search or enter URL",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        },
                        singleLine      = true,
                        textStyle       = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction    = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                val nav = resolveNavUrl(urlBarText)
                                webViewState.webView?.loadUrl(nav)
                            }
                        ),
                        shape  = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme
                                .surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = MaterialTheme.colorScheme
                                .surfaceVariant.copy(alpha = 0.30f),
                            focusedBorderColor      = MaterialTheme.colorScheme
                                .primary.copy(alpha = 0.55f),
                            unfocusedBorderColor    = MaterialTheme.colorScheme
                                .outline.copy(alpha = 0.15f)
                        )
                    )
                }

                // Loading progress strip — 2dp, zero extra vertical space
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

        // ─────────────────────────────────────────────────────────────────────
        // WEBVIEW — fills ALL remaining vertical space
        //
        // PERSISTENCE STRATEGY:
        //   The WebView instance is created once inside `remember {}` via the
        //   `webViewState.webView` field and stored there. `AndroidView`'s
        //   `factory` lambda runs only ONCE per composition lifetime.
        //   Navigating to another tab and back does NOT call factory again —
        //   the same WebView object is reused, preserving scroll position,
        //   session cookies, and page state. No blank screen, no reload.
        // ─────────────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)         // takes every pixel below the header
        ) {
            AndroidView(
                factory = { ctx ->
                    buildWebView(
                        context          = ctx,
                        webViewState     = webViewState,
                        dnsManager       = dnsManager,
                        isDohEnabledRef  = { isDohEnabled },
                        onPageStarted    = { url ->
                            isLoading   = true
                            urlBarText  = url
                            webViewState.currentUrl = url
                        },
                        onPageFinished   = { url, wv ->
                            isLoading    = false
                            canGoBack    = wv?.canGoBack()    ?: false
                            canGoForward = wv?.canGoForward() ?: false
                            url?.let { webViewState.currentUrl = it }
                            wv?.evaluateJavascript(JS_HTML5_EXTRACTOR, null)
                        },
                        onProgressChanged = { progress ->
                            loadingProgress = progress / 100f
                            isLoading       = progress < 100
                        },
                        onUrlOverride = { url ->
                            urlBarText = url
                            webViewState.currentUrl = url
                        },
                        onVideoExtracted = onVideoExtracted
                    )
                },
                // update lambda — called on every recomposition; we update
                // the dnsManager reference inside webViewClient via the closure
                // so the correct shield state is always read at interception time
                update = { _ ->
                    // Nothing to update — all live state is read via lambdas
                    // inside the WebViewClient closures, which capture the
                    // latest `isDohEnabled` and `dnsManager` by reference.
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // ── Hardware back key — navigate WebView history, not the app ────────────
    BackHandler(enabled = canGoBack) {
        webViewState.webView?.goBack()
    }

    // ── Cleanup — save WebView state bundle on disposal ──────────────────────
    DisposableEffect(Unit) {
        onDispose {
            webViewState.webView?.let { wv ->
                val bundle = Bundle()
                wv.saveState(bundle)
                webViewState.savedBundle = bundle
                Log.d(TAG, "WebView state saved on dispose")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// buildWebView — constructs and fully configures the WebView.
// Extracted to keep the composable body readable.
// Returns the WebView and stores it in webViewState.webView.
// ─────────────────────────────────────────────────────────────────────────────
@SuppressLint("SetJavaScriptEnabled")
private fun buildWebView(
    context:           Context,
    webViewState:      WebViewState,
    dnsManager:        DnsManager,
    isDohEnabledRef:   () -> Boolean,
    onPageStarted:     (String) -> Unit,
    onPageFinished:    (String?, WebView?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onUrlOverride:     (String) -> Unit,
    onVideoExtracted:  (String) -> Unit
): WebView {
    // Reuse existing instance if it already exists (tab switch back)
    webViewState.webView?.let { existing ->
        Log.d(TAG, "Reusing existing WebView instance")
        return existing
    }

    val wv = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // ── Hardware Acceleration ─────────────────────────────────────────
        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

        // ── WebSettings ───────────────────────────────────────────────────
        settings.apply {
            // JavaScript — required for modern sites
            javaScriptEnabled        = true
            javaScriptCanOpenWindowsAutomatically = false

            // Storage — prevents login/session loss on navigation
            domStorageEnabled        = true
            databaseEnabled          = true

            // Caching — use network cache headers, not force-cache or no-cache
            cacheMode                = WebSettings.LOAD_DEFAULT

            // Viewport & zoom
            loadWithOverviewMode     = true
            useWideViewPort          = true
            setSupportZoom(true)
            builtInZoomControls      = true
            displayZoomControls      = false

            // Chrome Mobile UA — fixes reCAPTCHA, Error 400, and "browser
            // not supported" walls on sites that check user-agent strings
            userAgentString          = CHROME_MOBILE_UA

            // Media — allow autoplay for HTML5 video
            mediaPlaybackRequiresUserGesture = false

            // Mixed content — needed for http:// embeds on https:// pages
            mixedContentMode         = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // Parallel network requests
            blockNetworkImage        = false
            loadsImagesAutomatically = true

            // File access — keep off for security
            allowFileAccess          = false
        }

        // ── JavaScript bridge — HTML5 <video> extractor ───────────────────
        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onVideoFound(src: String) {
                    if (src.isNotBlank() && src.startsWith("http")) {
                        Log.d(TAG, "HTML5 video extracted: $src")
                        onVideoExtracted(src)
                    }
                }
            },
            "VideoExtractor"
        )

        // ── WebChromeClient — progress updates ────────────────────────────
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress)
            }
        }

        // ── WebViewClient — navigation + DoH interception ─────────────────
        webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view:    WebView?,
                request: WebResourceRequest?
            ): Boolean {
                request?.url?.toString()?.let { url ->
                    onUrlOverride(url)
                }
                return false    // let WebView handle navigation internally
            }

            override fun onPageStarted(
                view:    WebView?,
                url:     String?,
                favicon: Bitmap?
            ) {
                url?.let { onPageStarted(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                onPageFinished(url, view)
                // Inject JS extractor on every page load
                view?.evaluateJavascript(JS_HTML5_EXTRACTOR, null)
            }

            // ── DoH Network Interception ──────────────────────────────────
            // When shield is ON, all eligible HTTP/S requests are routed through
            // the OkHttp DoH client so domain names resolve via Cloudflare/AdGuard
            // instead of the ISP's DNS — bypassing DNS-level blocks natively.
            //
            // CRITICAL STREAMING BYPASS:
            // Media streams (.m3u8, .ts, .mp4, googlevideo.com, videoplayback)
            // are NEVER intercepted. Intercepting them causes "Playback ID"
            // errors on YouTube and bot-detection blocks on other video CDNs.
            // Static assets (images, fonts) are also skipped for performance.
            override fun shouldInterceptRequest(
                view:    WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                // Only intercept when shield is active
                if (!isDohEnabledRef()) return null

                val url = request?.url?.toString() ?: return null
                if (!url.startsWith("http")) return null

                val lower = url.lowercase()

                // Skip streaming hosts — pass directly to WebView native socket
                if (STREAM_HOST_BYPASS.any { lower.contains(it) })       return null
                // Skip streaming file extensions
                if (STREAM_EXTENSION_BYPASS.any { lower.contains(it) })  return null
                // Skip static assets (images, fonts) for performance
                if (STATIC_ASSET_BYPASS.any { lower.endsWith(it) })      return null

                return try {
                    val requestBuilder = Request.Builder().url(url)

                    // Forward original request headers except Host (OkHttp sets it)
                    request.requestHeaders?.forEach { (key, value) ->
                        if (!key.equals("host", ignoreCase = true)) {
                            try {
                                requestBuilder.addHeader(key, value)
                            } catch (_: Exception) { /* skip invalid headers */ }
                        }
                    }

                    val response = dnsManager.getClient()
                        .newCall(requestBuilder.build())
                        .execute()

                    if (!response.isSuccessful) {
                        response.close()
                        return null     // let WebView retry via native stack
                    }

                    val contentType = response.body?.contentType()
                    val mimeType    = if (contentType != null)
                        "${contentType.type}/${contentType.subtype}"
                    else
                        "text/html"
                    val charset     = contentType?.charset()?.name()

                    val responseHeaders = mutableMapOf<String, String>()
                    response.headers.forEach { (k, v) -> responseHeaders[k] = v }

                    WebResourceResponse(
                        mimeType,
                        charset,
                        response.code,
                        response.message.ifEmpty { "OK" },
                        responseHeaders,
                        response.body?.byteStream()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "DoH intercept failed for $url: ${e.message}")
                    null    // fall back to WebView native handling silently
                }
            }
        }

        // ── Restore saved state or load initial URL ────────────────────────
        if (webViewState.savedBundle != null) {
            restoreState(webViewState.savedBundle!!)
            Log.d(TAG, "WebView state restored from bundle")
        } else {
            loadUrl(webViewState.currentUrl)
        }
    }

    webViewState.webView = wv
    return wv
}

// ─────────────────────────────────────────────────────────────────────────────
// resolveNavUrl — turns user input into a navigable URL.
//   - Already a full URL → use as-is
//   - Contains a dot but no scheme → prepend https://
//   - Otherwise → Google search
// ─────────────────────────────────────────────────────────────────────────────
private fun resolveNavUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://")  -> trimmed
        trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".")          -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${
            java.net.URLEncoder.encode(trimmed, "UTF-8")
        }"
    }
}
