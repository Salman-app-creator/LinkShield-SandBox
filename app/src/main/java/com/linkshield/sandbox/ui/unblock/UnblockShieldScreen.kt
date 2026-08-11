package com.linkshield.sandbox.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import java.net.URLDecoder

// ─────────────────────────────────────────────────────────────────────────────
// UnblockShieldScreen.kt
//
// Responsibilities:
//   1. TAB SWITCHING & BROWSER STATE RETENTION
//      WebView instances are hoisted into UnblockShieldViewModel and keyed
//      by tab index.  When the user switches bottom tabs the Browser WebView
//      is simply detached / re-attached; it is never destroyed or reloaded.
//   2. AUTOMATIC ACTIVE MEDIA PASS-THROUGH
//      A @JavascriptInterface bridge ("LinkShieldBridge") is injected on every
//      page finish.  A MutationObserver watches for <video> nodes, reads
//      currentSrc/src, and forwards exact stream URLs to the Grabber tab.
//   3. TIKTOK & CUSTOM DEEP-LINK INTERCEPTION
//      shouldOverrideUrlLoading catches non-http(s) schemes (snssdk://,
//      intent://, fb://, tiktok://, etc.).  intent:// URLs are parsed for
//      S.browser_fallback_url; everything else is swallowed so the WebView
//      never surfaces net::ERR_UNKNOWN_URL_SCHEME.
//   4. PREVENT RECAPTCHA / BOT DETECTION
//      Realistic Chrome Mobile UA, DOM storage, database storage, persistent
//      third-party cookies, and mixed-content tolerance are all enabled.
//   5. DNS SHIELD INTEGRATION
//      shouldInterceptRequest routes every GET/HEAD subresource through
//      DnsManager.getClient() so the DoH / TLS-fragmentation pipeline
//      protects the entire browsing session.  Set-Cookie headers are synced
//      back into CookieManager so login state is preserved.
// ─────────────────────────────────────────────────────────────────────────────

// ── ViewModel (hoists WebViews so they survive tab switches & config changes) ─

class UnblockShieldViewModel : ViewModel() {

    private val _webViews = mutableStateMapOf<Int, WebView>()
    val webViews: Map<Int, WebView> = _webViews

    var selectedTabIndex by mutableIntStateOf(0)
        private set

    var currentUrl by mutableStateOf("https://www.google.com")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var canGoBack by mutableStateOf(false)
        private set

    var canGoForward by mutableStateOf(false)
        private set

    private val _mediaUrls = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaUrls: StateFlow<List<MediaItem>> = _mediaUrls.asStateFlow()

    data class MediaItem(
        val url: String,
        val title: String,
        val pageUrl: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun selectTab(index: Int) {
        selectedTabIndex = index
    }

    fun getOrCreateWebView(context: Context, tabIndex: Int, dnsManager: DnsManager): WebView {
        return _webViews.getOrPut(tabIndex) {
            createWebView(context, dnsManager, this)
        }
    }

    fun updateUrl(url: String) {
        currentUrl = url
    }

    fun updateLoading(loading: Boolean) {
        isLoading = loading
    }

    fun updateNavigationState(back: Boolean, forward: Boolean) {
        canGoBack = back
        canGoForward = forward
    }

    fun onMediaFound(url: String, title: String, pageUrl: String) {
        if (url.isBlank()) return
        val item = MediaItem(url, title, pageUrl)
        _mediaUrls.value = _mediaUrls.value + item
    }

    fun clearMedia() {
        _mediaUrls.value = emptyList()
    }

    fun loadUrl(url: String) {
        val fixed = when {
            url.startsWith("http://", ignoreCase = true) -> url
            url.startsWith("https://", ignoreCase = true) -> url
            else -> "https://$url"
        }
        currentUrl = fixed
        _webViews[0]?.loadUrl(fixed)
    }

    fun goBack() {
        _webViews[0]?.goBack()
    }

    fun goForward() {
        _webViews[0]?.goForward()
    }

    fun reload() {
        _webViews[0]?.reload()
    }

    override fun onCleared() {
        super.onCleared()
        _webViews.values.forEach { it.destroy() }
        _webViews.clear()
    }
}

// ── Media bridge ─────────────────────────────────────────────────────────────

private class MediaBridge(
    private val onMediaFound: (url: String, title: String, pageUrl: String) -> Unit
) {
    @JavascriptInterface
    fun onVideoFound(url: String, title: String, pageUrl: String) {
        if (url.isBlank()) return
        onMediaFound(url, title, pageUrl)
    }
}

// ── WebView factory ──────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    dnsManager: DnsManager,
    viewModel: UnblockShieldViewModel
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true

            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                allowFileAccess = false
            }

            userAgentString =
                "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = ShieldWebViewClient(
            dnsManager = dnsManager,
            onPageStarted = { url ->
                viewModel.updateLoading(true)
                viewModel.updateUrl(url ?: "")
            },
            onPageFinished = { url ->
                viewModel.updateLoading(false)
                viewModel.updateNavigationState(canGoBack(), canGoForward())
                injectMediaInterceptor(this)
            },
            onUpdateNavigation = { back, forward ->
                viewModel.updateNavigationState(back, forward)
            }
        )

        webChromeClient = ShieldWebChromeClient()

        addJavascriptInterface(
            MediaBridge { url, title, pageUrl ->
                viewModel.onMediaFound(url, title, pageUrl)
            },
            "LinkShieldBridge"
        )
    }
}

// ── JavaScript injection ─────────────────────────────────────────────────────

private fun injectMediaInterceptor(webView: WebView) {
    val script = """
        (function() {
            if (window.__linkShieldInjected) return;
            window.__linkShieldInjected = true;

            function report(el) {
                var src = el.currentSrc || el.src;
                if (src && src.length > 0) {
                    try {
                        LinkShieldBridge.onVideoFound(src, document.title, window.location.href);
                    } catch(e) {}
                }
            }

            function hookVideo(v) {
                report(v);
                v.addEventListener('play', function() { report(v); });
                v.addEventListener('loadedmetadata', function() { report(v); });
            }

            var obs = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    mutation.addedNodes.forEach(function(node) {
                        if (node.tagName === 'VIDEO') {
                            hookVideo(node);
                        }
                        if (node.querySelectorAll) {
                            node.querySelectorAll('video').forEach(hookVideo);
                        }
                    });
                });
            });

            var startObserving = function() {
                if (!document.body) return;
                obs.observe(document.body, { childList: true, subtree: true });
                document.querySelectorAll('video').forEach(hookVideo);
            };

            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', startObserving);
            } else {
                startObserving();
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(script, null)
}
// ── WebViewClient ────────────────────────────────────────────────────────────

private class ShieldWebViewClient(
    private val dnsManager: DnsManager,
    private val onPageStarted: (String?) -> Unit,
    private val onPageFinished: (String?) -> Unit,
    private val onUpdateNavigation: (Boolean, Boolean) -> Unit
) : WebViewClient() {

    // Modern callback (API 24+)
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url ?: return false
        val scheme = url.scheme?.lowercase() ?: return false

        // Allow normal http(s) navigation
        if (scheme == "http" || scheme == "https") {
            return false
        }

        // Handle intent:// URLs — extract fallback URL if present
        if (scheme == "intent") {
            val intentString = url.toString()
            val fallbackRegex = ";S\\.browser_fallback_url=([^;]+)".toRegex()
            val match = fallbackRegex.find(intentString)
            if (match != null) {
                val encoded = match.groupValues[1]
                try {
                    val decoded = URLDecoder.decode(encoded, "UTF-8")
                    if (decoded.startsWith("http")) {
                        view?.loadUrl(decoded)
                        return true
                    }
                } catch (_: Exception) { }
            }
            // Swallow intent:// without fallback to prevent ERR_UNKNOWN_URL_SCHEME
            return true
        }

        // Swallow all custom schemes: snssdk://, fb://, tiktok://, etc.
        if (scheme != "http" && scheme != "https" && scheme != "file") {
            return true
        }

        return false
    }

    // Legacy callback for older devices
    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        val uri = Uri.parse(url)
        return shouldOverrideUrlLoading(view, object : WebResourceRequest {
            override fun getUrl(): Uri = uri
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        })
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request == null) return null
        val method = request.method.uppercase()
        if (method != "GET" && method != "HEAD") return null

        val url = request.url.toString()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        return try {
            val client = dnsManager.getClient()
            val okRequest = Request.Builder()
                .url(url)
                .apply {
                    request.requestHeaders.forEach { (k, v) -> header(k, v) }
                }
                .build()

            val response = client.newCall(okRequest).execute()

            // Sync Set-Cookie back into CookieManager so login state survives
            response.headers("Set-Cookie").forEach { cookie ->
                CookieManager.getInstance().setCookie(url, cookie)
            }

            val contentType = response.body?.contentType()
            val mimeType = contentType?.toString() ?: "application/octet-stream"

            WebResourceResponse(
                mimeType,
                contentType?.charset()?.name() ?: "UTF-8",
                response.code,
                response.message.ifBlank { "OK" },
                response.headers.toMultimap().mapValues { it.value.joinToString(", ") },
                response.body?.byteStream()
            )
        } catch (e: Exception) {
            // Let WebView fall back to its own loader on failure
            null
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished(url)
        view?.let { onUpdateNavigation(it.canGoBack(), it.canGoForward()) }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onPageFinished(view?.url)
        }
    }
}

// ── WebChromeClient ──────────────────────────────────────────────────────────

private class ShieldWebChromeClient : WebChromeClient() {
    // Placeholder for future fullscreen video / permission handling
}

// ── Composable Screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnblockShieldScreen(
    dnsManager: DnsManager,
    viewModel: UnblockShieldViewModel = viewModel()
) {
    val context = LocalContext.current
    val mediaList by viewModel.mediaUrls.collectAsState()
    val tabs = listOf("Browser", "Grabber", "Settings")

    BackHandler(enabled = viewModel.canGoBack && viewModel.selectedTabIndex == 0) {
        viewModel.goBack()
    }

    Scaffold(
        topBar = {
            if (viewModel.selectedTabIndex == 0) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = viewModel.currentUrl,
                            onValueChange = { viewModel.updateUrl(it) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { viewModel.loadUrl(viewModel.currentUrl) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Go")
                                }
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.goBack() }, enabled = viewModel.canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = { viewModel.goForward() }, enabled = viewModel.canGoForward) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(onClick = { viewModel.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Home, contentDescription = title)
                                1 -> Icon(Icons.Default.Download, contentDescription = title)
                                else -> Icon(Icons.Default.Settings, contentDescription = title)
                            }
                        },
                        label = { Text(title) },
                        selected = viewModel.selectedTabIndex == index,
                        onClick = { viewModel.selectTab(index) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (viewModel.selectedTabIndex) {
                0 -> BrowserTab(viewModel, dnsManager, context)
                1 -> GrabberTab(mediaList, viewModel::clearMedia)
                else -> SettingsTab(dnsManager)
            }
        }
    }
}

// ── Browser Tab (state-retaining WebView) ────────────────────────────────────

@Composable
private fun BrowserTab(
    viewModel: UnblockShieldViewModel,
    dnsManager: DnsManager,
    context: Context
) {
    val webView = remember {
        viewModel.getOrCreateWebView(context, 0, dnsManager)
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    ) { wv ->
        wv.visibility = android.view.View.VISIBLE
    }

    LaunchedEffect(viewModel.currentUrl) {
        val current = webView.url
        if (current != viewModel.currentUrl && viewModel.currentUrl.isNotBlank()) {
            webView.loadUrl(viewModel.currentUrl)
        }
    }
}

// ── Grabber Tab ──────────────────────────────────────────────────────────────

@Composable
private fun GrabberTab(
    mediaList: List<UnblockShieldViewModel.MediaItem>,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Captured Media",
                style = MaterialTheme.typography.headlineSmall
            )
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (mediaList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Play a video in the Browser tab to capture stream URLs.")
            }
        } else {
            LazyColumn {
                items(mediaList.reversed()) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { /* TODO: start download */ }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                            Text(
                                text = item.url,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                            Text(
                                text = "From: ${item.pageUrl}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Settings Tab ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsTab(dnsManager: DnsManager) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Shield Settings",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        val isEnabled = remember { mutableStateOf(dnsManager.isDohEnabled()) }
        val provider = remember { mutableStateOf(dnsManager.getCurrentProvider().displayName) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DNS-over-HTTPS Shield")
            Switch(
                checked = isEnabled.value,
                onCheckedChange = { enable ->
                    isEnabled.value = enable
                    if (enable) {
                        try {
                            dnsManager.enableDoh()
                            provider.value = dnsManager.getCurrentProvider().displayName
                        } catch (e: Exception) {
                            isEnabled.value = false
                        }
                    } else {
                        dnsManager.disableDoh()
                        provider.value = "Off"
                    }
                }
            )
        }

        Text(
            text = "Active provider: ${provider.value}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Remaining downloads: ${if (dnsManager.isProUser()) "Unlimited" else dnsManager.getRemainingDownloads()}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
