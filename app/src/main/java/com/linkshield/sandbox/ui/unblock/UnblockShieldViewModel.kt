package com.linkshield.sandbox.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.linkshield.sandbox.dns.DohProvider
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import java.net.URLDecoder

// ─────────────────────────────────────────────────────────────────────────────
// UnblockShieldViewModel
//
// Single source of truth for:
//  • WebView instances keyed by tab index — never destroyed on tab switch
//  • URL, loading, navigation (back/forward) state observable by Compose
//  • Media URLs captured by the JS bridge — emitted to MediaGrabberScreen
//
// Lifecycle: scoped to the Activity (via viewModel() in MainActivity) so the
// same instance — and its cached WebViews — survive any number of tab changes.
// ─────────────────────────────────────────────────────────────────────────────

// Chrome Mobile UA — avoids reCAPTCHA / Error 400 / "browser not supported"
internal const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; SM-S918B) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/126.0.0.0 Mobile Safari/537.36"

// JS injected after every page load — reports media URLs via LinkShieldBridge
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
        v.addEventListener('play',          tryReport);
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

    // ── WebView cache ─────────────────────────────────────────────────────────
    private val _webViews = mutableStateMapOf<Int, WebView>()
    val webViews: Map<Int, WebView> = _webViews

    // ── Observable browser state ──────────────────────────────────────────────
    var currentUrl   by mutableStateOf("https://www.google.com")
        private set
    var isLoading    by mutableStateOf(false)
        private set
    var canGoBack    by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var pageTitle    by mutableStateOf("")
        private set

    // ── Media stream ──────────────────────────────────────────────────────────
    private val _mediaUrls = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaUrls: StateFlow<List<MediaItem>> = _mediaUrls.asStateFlow()

    data class MediaItem(
        val url:       String,
        val title:     String,
        val pageUrl:   String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // ── Public API — called from MainActivity / UnblockShieldScreen ───────────

    /**
     * Returns an existing WebView for [tabIndex] or creates a new one.
     * The WebView is fully configured with Chrome UA, DOM storage, cookies,
     * media bridge, and DoH interceptor before being returned.
     */
    fun getOrCreateWebView(
        context:    Context,
        tabIndex:   Int,
        dnsManager: DnsManager
    ): WebView = _webViews.getOrPut(tabIndex) {
        buildWebView(context, dnsManager)
    }

    fun updateUrl(url: String)                        { currentUrl   = url  }
    fun updateLoading(loading: Boolean)               { isLoading    = loading }
    fun updateNavigationState(back: Boolean, fwd: Boolean) {
        canGoBack    = back
        canGoForward = fwd
    }
    fun updatePageTitle(title: String)                { pageTitle    = title }

    fun onMediaFound(url: String, title: String, pageUrl: String) {
        if (url.isBlank()) return
        // Deduplicate by URL
        val existing = _mediaUrls.value
        if (existing.any { it.url == url }) return
        _mediaUrls.value = existing + MediaItem(url, title, pageUrl)
    }

    fun clearMedia() { _mediaUrls.value = emptyList() }

    /** Load a URL in the primary (Browser tab) WebView. */
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

    // ── WebView factory ───────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(context: Context, dnsManager: DnsManager): WebView {
        return WebView(context.applicationContext).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Hardware acceleration
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled                     = true
                domStorageEnabled                     = true
                databaseEnabled                       = true
                loadWithOverviewMode                  = true
                useWideViewPort                       = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture      = false
                mixedContentMode                      = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode                             = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically              = true
                userAgentString                       = CHROME_UA

                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    allowFileAccess = false
                }
            }

            // Enable persistent cookies (prevents reCAPTCHA re-triggers)
            CookieManager.getInstance().apply {
    setAcceptCookie(true)
    setAcceptThirdPartyCookies(this@apply, true)
            }
            

            // JS media bridge
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

            webViewClient = ShieldWebViewClient(dnsManager, this@UnblockShieldViewModel)

            loadUrl(currentUrl)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ShieldWebViewClient
// Handles: deep-link suppression, back-state updates, DoH interception
// ─────────────────────────────────────────────────────────────────────────────
private class ShieldWebViewClient(
    private val dnsManager: DnsManager,
    private val vm:         UnblockShieldViewModel
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url ?: return false
        val scheme = url.scheme?.lowercase() ?: return false

        // Normal http(s) — let WebView handle
        if (scheme == "http" || scheme == "https") return false

        // intent:// — extract S.browser_fallback_url if present
        if (scheme == "intent") {
            val intentStr = url.toString()
            val match = ";S\\.browser_fallback_url=([^;]+)".toRegex().find(intentStr)
            if (match != null) {
                runCatching {
                    val decoded = URLDecoder.decode(match.groupValues[1], "UTF-8")
                    if (decoded.startsWith("http")) { view?.loadUrl(decoded); return true }
                }
            }
            return true // swallow — prevents ERR_UNKNOWN_URL_SCHEME
        }

        // All other custom schemes (snssdk://, fb://, tiktok://) — swallow silently
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

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        vm.updateLoading(true)
        url?.let { vm.updateUrl(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        vm.updateLoading(false)
        vm.updateNavigationState(view?.canGoBack() == true, view?.canGoForward() == true)
        url?.let { vm.updateUrl(it) }
        // Inject JS media interceptor
        view?.evaluateJavascript(JS_MEDIA_INTERCEPTOR, null)
    }

    override fun shouldInterceptRequest(
        view:    WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (!dnsManager.isDohEnabled()) return null
        val method = request?.method?.uppercase() ?: return null
        if (method != "GET" && method != "HEAD") return null
        val url = request.url?.toString() ?: return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        // Never intercept streaming media — causes YouTube playback errors
        val lower = url.lowercase()
        val skipExts = listOf(
            ".m3u8", ".ts", ".mp4", ".mp3", ".webm", ".m4s",
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
            ".ico", ".woff", ".woff2", ".ttf", ".eot"
        )
        if (skipExts.any { lower.contains(it) }) return null
        if (lower.contains("googlevideo.com") || lower.contains("videoplayback")) return null

        return runCatching {
            val reqBuilder = Request.Builder().url(url)
            request.requestHeaders?.forEach { (k, v) ->
                if (!k.equals("host", ignoreCase = true)) {
                    runCatching { reqBuilder.addHeader(k, v) }
                }
            }
            val response = dnsManager.getClient().newCall(reqBuilder.build()).execute()
            // Sync Set-Cookie back into CookieManager so login sessions persist
            response.headers("Set-Cookie").forEach { cookie ->
                CookieManager.getInstance().setCookie(url, cookie)
            }
            if (!response.isSuccessful) return null
            val ct      = response.body?.contentType()
            val mime    = if (ct != null) "${ct.type}/${ct.subtype}" else "text/html"
            val charset = ct?.charset()?.name()
            val headers = mutableMapOf<String, String>()
            response.headers.forEach { (k, v) -> headers[k] = v }
            WebResourceResponse(
                mime, charset,
                response.code, response.message.ifEmpty { "OK" },
                headers, response.body?.byteStream()
            )
        }.getOrNull()
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

internal fun normalizeUrl(raw: String): String {
    val t = raw.trim()
    return when {
        t.startsWith("http://", ignoreCase = true)  -> t
        t.startsWith("https://", ignoreCase = true) -> t
        t.contains(".")                             -> "https://$t"
        else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(t, "UTF-8")}"
    }
}
