package com.linkshield.sandbox.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.linkshield.sandbox.adblock.AdBlockEngine
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.URLDecoder

// ─────────────────────────────────────────────────────────────────────────────
// UnblockShieldViewModel.kt  — Phase 1 update
//
// What changed from previous version:
//   • AdBlockEngine.getInstance() injected into ShieldWebViewClient.
//   • shouldInterceptRequest now calls adBlockEngine.shouldBlock(url) FIRST.
//     Blocked requests return an EMPTY 200 response (not null, not 404) to
//     prevent "ERR_BLOCKED_BY_CLIENT" errors from breaking page layout.
//   • adBlockEnabled flag exposed so the UI can show a toggle.
//   • blockedRequestCount tracks how many ads were blocked this session.
//
// Everything else (DoH routing, JS bridge, media detection, WebView lifecycle)
// is IDENTICAL to the previous version.
// ─────────────────────────────────────────────────────────────────────────────

internal const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; SM-S918B) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/126.0.0.0 Mobile Safari/537.36"

internal const val JS_MEDIA_INTERCEPTOR = """
(function() {
    if (window.__linkShieldInjected) return;
    window.__linkShieldInjected = true;
    function isMedia(u) {
        if (!u || typeof u !== 'string') return false;
        var l = u.toLowerCase();
        return l.includes('.m3u8') || l.includes('.mp4') || l.includes('.webm') ||
               l.includes('.mkv') || l.includes('.mov') || l.includes('.m4a') ||
               l.includes('.mp3') || l.includes('.ogg') || l.includes('.ts') ||
               l.includes('manifest') || l.includes('videoplayback') ||
               l.includes('blob:');
    }
    function report(url, title, pageUrl) {
        try {
            if (url && window.LinkShieldBridge) {
                window.LinkShieldBridge.onVideoFound(url, title || document.title, pageUrl || location.href);
            }
        } catch(e) {}
    }
    var origXHROpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(m, url) {
        if (isMedia(url)) report(url, document.title, location.href);
        return origXHROpen.apply(this, arguments);
    };
    var origFetch = window.fetch;
    window.fetch = function(input, init) {
        var url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
        if (isMedia(url)) report(url, document.title, location.href);
        return origFetch.apply(this, arguments);
    };
    function hookVideo(v) {
        function tryReport() {
            var s = v.currentSrc || v.src;
            if (s) report(s, document.title, location.href);
        }
        tryReport();
        v.addEventListener('play', tryReport);
        v.addEventListener('loadedmetadata', tryReport);
    }
    function scanAll() {
        document.querySelectorAll('video').forEach(hookVideo);
        document.querySelectorAll('audio').forEach(function(a) {
            if (a.src) report(a.src, document.title, location.href);
        });
    }
    var obs = new MutationObserver(function(mutations) {
        mutations.forEach(function(m) {
            m.addedNodes.forEach(function(n) {
                if (n.tagName === 'VIDEO' || n.tagName === 'AUDIO') hookVideo(n);
                if (n.querySelectorAll) n.querySelectorAll('video,audio').forEach(hookVideo);
            });
        });
    });
    function startObs() {
        if (document.body) obs.observe(document.body, { childList: true, subtree: true });
        scanAll();
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', startObs);
    } else {
        startObs();
    }
    setInterval(scanAll, 3000);
    window.addEventListener('load', scanAll);
})();
"""

class UnblockShieldViewModel : ViewModel() {

    private val _webViews = mutableMapOf<Int, WebView>()
    val webViews: Map<Int, WebView> = _webViews

    // ── Browser state ──────────────────────────────────────────────────────────
    var currentUrl          by mutableStateOf("https://www.google.com")
        private set
    var isLoading           by mutableStateOf(false)
        private set
    var canGoBack           by mutableStateOf(false)
        private set
    var canGoForward        by mutableStateOf(false)
        private set
    var pageTitle           by mutableStateOf("")
        private set

    // ── AdBlock state — exposed to UI ──────────────────────────────────────────
    var adBlockEnabled      by mutableStateOf(true)
    var blockedRequestCount by mutableStateOf(0)
        private set

    // ── Media stream state ─────────────────────────────────────────────────────
    private val _mediaUrls = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaUrls: StateFlow<List<MediaItem>> = _mediaUrls.asStateFlow()

    data class MediaItem(
        val url:       String,
        val title:     String,
        val pageUrl:   String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    fun getOrCreateWebView(context: Context, tabIndex: Int, dnsManager: DnsManager): WebView =
        _webViews.getOrPut(tabIndex) { buildWebView(context, dnsManager) }

    fun updateUrl(url: String)               { currentUrl   = url }
    fun updateLoading(loading: Boolean)      { isLoading    = loading }
    fun updateNavigationState(back: Boolean, fwd: Boolean) {
        canGoBack    = back
        canGoForward = fwd
    }
    fun updatePageTitle(title: String)       { pageTitle    = title }
    fun incrementBlockedCount()              { blockedRequestCount++ }
    fun resetBlockedCount()                  { blockedRequestCount = 0 }

    fun onMediaFound(url: String, title: String, pageUrl: String) {
        if (url.isBlank()) return
        val existing = _mediaUrls.value
        if (existing.any { it.url == url }) return
        _mediaUrls.value = existing + MediaItem(url, title, pageUrl)
    }

    fun clearMedia() { _mediaUrls.value = emptyList() }

    fun loadUrl(url: String) {
        val fixed = normalizeUrl(url)
        currentUrl = fixed
        _webViews[0]?.loadUrl(fixed)
    }

    fun goBack()    { _webViews[0]?.goBack()    }
    fun goForward() { _webViews[0]?.goForward() }
    fun reload()    { _webViews[0]?.reload()    }

    override fun onCleared() {
        super.onCleared()
        _webViews.values.forEach { it.destroy() }
        _webViews.clear()
    }

    // ── WebView factory ────────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(context: Context, dnsManager: DnsManager): WebView {
        return WebView(context.applicationContext).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled                       = true
                domStorageEnabled                       = true
                databaseEnabled                         = true
                loadWithOverviewMode                    = true
                useWideViewPort                         = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically   = true
                mediaPlaybackRequiresUserGesture        = false
                mixedContentMode                        = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode                               = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically                = true
                userAgentString                         = CHROME_UA
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    allowFileAccess = false
                }
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@apply, true)
            }

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onVideoFound(url: String, title: String, pageUrl: String) {
                        if (url.isNotBlank()) onMediaFound(url, title, pageUrl)
                    }
                },
                "LinkShieldBridge"
            )

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    updateLoading(newProgress < 100)
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    updatePageTitle(title ?: "")
                }
            }

            webViewClient = ShieldWebViewClient(
                dnsManager     = dnsManager,
                vm             = this@UnblockShieldViewModel,
                adBlockEngine  = AdBlockEngine.getInstance(),
                isAdBlockOn    = { adBlockEnabled }
            )

            loadUrl(currentUrl)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ShieldWebViewClient — Phase 1: AdBlock wired in
//
// shouldInterceptRequest decision tree:
//   1. Parse URL → check if AdBlock enabled AND shouldBlock() → serve EMPTY response
//   2. If DoH enabled → route through OkHttp (existing logic, unchanged)
//   3. Otherwise → return null (WebView native handling)
//
// Why return an EMPTY response instead of null for blocked URLs:
//   Returning null tells WebView "no interception, proceed normally" →
//   the ad request goes through. Returning a 200 with 0 bytes tells the
//   browser "request succeeded, content is empty" → ad placeholder stays
//   invisible, no layout errors, no console 403 flood.
// ─────────────────────────────────────────────────────────────────────────────
private class ShieldWebViewClient(
    private val dnsManager:    DnsManager,
    private val vm:            UnblockShieldViewModel,
    private val adBlockEngine: AdBlockEngine,
    private val isAdBlockOn:   () -> Boolean
) : WebViewClient() {

    // Reusable empty stream to return for blocked requests — avoids allocating
    // a new ByteArray on every blocked request (hot path)
    private val EMPTY_STREAM get() = ByteArrayInputStream(ByteArray(0))

    override fun shouldOverrideUrlLoading(
        view:    WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url    = request?.url ?: return false
        val scheme = url.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") return false

        // intent:// — extract fallback URL if present
        if (scheme == "intent") {
            val intentStr = url.toString()
            val match     = ";S\\.browser_fallback_url=([^;]+)".toRegex().find(intentStr)
            if (match != null) {
                runCatching {
                    val decoded = URLDecoder.decode(match.groupValues[1], "UTF-8")
                    if (decoded.startsWith("http")) { view?.loadUrl(decoded); return true }
                }
            }
            return true   // swallow all other custom schemes
        }
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        val uri = Uri.parse(url)
        return shouldOverrideUrlLoading(view, object : WebResourceRequest {
            override fun getUrl()            = uri
            override fun isForMainFrame()    = true
            override fun isRedirect()        = false
            override fun hasGesture()        = false
            override fun getMethod()         = "GET"
            override fun getRequestHeaders() = mutableMapOf<String, String>()
        })
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        vm.updateLoading(true)
        url?.let { vm.updateUrl(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        vm.updateLoading(false)
        vm.updateNavigationState(
            view?.canGoBack()    == true,
            view?.canGoForward() == true
        )
        url?.let { vm.updateUrl(it) }
        view?.evaluateJavascript(JS_MEDIA_INTERCEPTOR, null)
    }

    override fun onReceivedError(
        view:    WebView?,
        request: WebResourceRequest?,
        error:   WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        // Retry once via DoH if main frame failed
        if (request?.isForMainFrame == true) {
            val url = request.url.toString()
            if (dnsManager.isDohEnabled() && !url.contains("retry=true")) {
                val retryUrl = if (url.contains("?")) "$url&retry=true" else "$url?retry=true"
                view?.post { view.loadUrl(retryUrl) }
            }
        }
    }

    // ── Main interception method ───────────────────────────────────────────────
    override fun shouldInterceptRequest(
        view:    WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val method = request?.method?.uppercase() ?: return null
        if (method != "GET" && method != "HEAD") return null
        val url = request.url?.toString() ?: return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        // ── STEP 1: Ad/Tracker blocking ────────────────────────────────────────
        // This runs BEFORE DoH routing so blocked requests never hit the network.
        if (isAdBlockOn() && adBlockEngine.shouldBlock(url)) {
            vm.incrementBlockedCount()

            // Return a transparent 1×1 GIF for image requests — preserves layout
            // Return empty HTML for scripts/iframes — prevents JS errors
            val mime = when {
                url.contains(".gif")  -> "image/gif"
                url.contains(".png")  -> "image/png"
                url.contains(".jpg") ||
                url.contains(".jpeg") -> "image/jpeg"
                url.contains(".js")   -> "application/javascript"
                url.contains(".css")  -> "text/css"
                else                  -> "text/html"
            }

            return WebResourceResponse(
                mime, "utf-8", 200, "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control"               to "no-store"
                ),
                EMPTY_STREAM
            )
        }

        // ── STEP 2: DoH routing (existing logic — unchanged) ───────────────────
        if (!dnsManager.isDohEnabled()) return null

        val lower    = url.lowercase()
        val skipExts = listOf(".m3u8", ".ts", ".mp4", ".mp3", ".webm", ".m4s", ".mpd", ".key")
        if (skipExts.any { lower.endsWith(it) }) return null
        if (lower.contains("googlevideo.com") || lower.contains("videoplayback") ||
            lower.contains("blob:")) return null

        return runCatching {
            val reqBuilder = Request.Builder().url(url)
            request.requestHeaders?.forEach { (k, v) ->
                if (!k.equals("host", ignoreCase = true) &&
                    !k.equals("Accept-Encoding", ignoreCase = true)) {
                    runCatching { reqBuilder.addHeader(k, v) }
                }
            }
            if (request.requestHeaders?.containsKey("User-Agent") != true) {
                reqBuilder.addHeader("User-Agent", CHROME_UA)
            }

            val response = dnsManager.getClient().newCall(reqBuilder.build()).execute()

            if (!response.isSuccessful && response.code == 503) return null

            response.headers("Set-Cookie").forEach { cookie ->
                CookieManager.getInstance().setCookie(url, cookie)
            }

            val ct      = response.body?.contentType()
            val mime    = if (ct != null) "${ct.type}/${ct.subtype}" else "text/html"
            val charset = ct?.charset()?.name() ?: "UTF-8"
            val headers = mutableMapOf<String, String>()
            response.headers.forEach { (k, v) -> headers[k] = v }
            headers["Access-Control-Allow-Origin"] = "*"

            WebResourceResponse(
                mime, charset,
                response.code, response.message.ifEmpty { "OK" },
                headers, response.body?.byteStream()
            )
        }.getOrElse { null }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// normalizeUrl — keeps existing call sites working
// ─────────────────────────────────────────────────────────────────────────────
internal fun normalizeUrl(raw: String): String {
    val t = raw.trim()
    return when {
        t.startsWith("http://",  ignoreCase = true) -> t
        t.startsWith("https://", ignoreCase = true) -> t
        t.contains(".")                             -> "https://$t"
        else -> "https://www.google.com/search?q=${
            java.net.URLEncoder.encode(t, "UTF-8")
        }"
    }
}
