package com.linkshield.sandbox.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.net.URLEncoder

internal const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; SM-S918B) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

internal const val JS_MEDIA_INTERCEPTOR = """
(function() {
    if (window.__linkShieldInjected) return;
    window.__linkShieldInjected = true;

    function absolute(u) {
        try { return new URL(u, location.href).href; } catch(e) { return u; }
    }

    function isMedia(u) {
        if (!u || typeof u !== 'string') return false;
        var l = u.toLowerCase();
        return l.includes('.m3u8') || l.includes('.mp4') || l.includes('.webm') ||
               l.includes('.mkv') || l.includes('.mov') || l.includes('.m4a') ||
               l.includes('.mp3') || l.includes('.ogg') || l.includes('.ts') ||
               l.includes('.mpd') || l.includes('manifest') ||
               l.includes('videoplayback') || l.startsWith('blob:');
    }

    function report(u) {
        try {
            if (!u || !window.LinkShieldBridge) return;
            window.LinkShieldBridge.onVideoFound(
                absolute(u),
                document.title || '',
                location.href
            );
        } catch(e) {}
    }

    var origXHROpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        if (isMedia(url)) report(url);
        return origXHROpen.apply(this, arguments);
    };

    var origFetch = window.fetch;
    window.fetch = function(input, init) {
        var url = typeof input === 'string'
            ? input
            : (input && input.url ? input.url : '');
        if (isMedia(url)) report(url);
        return origFetch.apply(this, arguments);
    };

    function hookMedia(el) {
        function scan() {
            var src = el.currentSrc || el.src;
            if (src) report(src);
            var sources = el.querySelectorAll ? el.querySelectorAll('source') : [];
            for (var i = 0; i < sources.length; i++) {
                if (sources[i].src) report(sources[i].src);
            }
        }

        scan();
        el.addEventListener('loadedmetadata', scan);
        el.addEventListener('play', scan);
    }

    function scanAll() {
        document.querySelectorAll('video,audio').forEach(hookMedia);
        document.querySelectorAll('video source,audio source').forEach(function(s) {
            if (s.src) report(s.src);
        });
    }

    var observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(m) {
            m.addedNodes.forEach(function(n) {
                if (!n || !n.tagName) return;

                if (n.tagName === 'VIDEO' || n.tagName === 'AUDIO') {
                    hookMedia(n);
                }

                if (n.querySelectorAll) {
                    n.querySelectorAll('video,audio').forEach(hookMedia);
                }
            });
        });
    });

    function start() {
        if (document.body) {
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
        }

        scanAll();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }

    setInterval(scanAll, 3000);
    window.addEventListener('load', scanAll);
})();
"""

class UnblockShieldViewModel : ViewModel() {

    private val webViews = mutableMapOf<Int, WebView>()

    var currentUrl by mutableStateOf("https://www.google.com")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var canGoBack by mutableStateOf(false)
        private set

    var canGoForward by mutableStateOf(false)
        private set

    var pageTitle by mutableStateOf("")
        private set

    data class MediaItem(
        val url: String,
        val title: String,
        val pageUrl: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _mediaUrls = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaUrls: StateFlow<List<MediaItem>> = _mediaUrls.asStateFlow()

    fun getOrCreateWebView(
        context: Context,
        tabIndex: Int,
        dnsManager: DnsManager
    ): WebView = webViews.getOrPut(tabIndex) {
        buildWebView(context, dnsManager)
    }

    fun updateUrl(url: String) {
        if (url.isNotBlank()) {
            currentUrl = url
        }
    }

    fun updateLoading(loading: Boolean) {
        isLoading = loading
    }

    fun updateNavigationState(
        back: Boolean,
        forward: Boolean
    ) {
        canGoBack = back
        canGoForward = forward
    }

    fun updatePageTitle(title: String) {
        pageTitle = title
    }

    fun onMediaFound(
        url: String,
        title: String,
        pageUrl: String
    ) {
        if (url.isBlank()) return

        val normalized = url.trim()

        if (_mediaUrls.value.any { it.url == normalized }) {
            return
        }

        _mediaUrls.value = (
            _mediaUrls.value + MediaItem(
                url = normalized,
                title = title,
                pageUrl = pageUrl
            )
        ).takeLast(30)
    }

    fun clearMedia() {
        _mediaUrls.value = emptyList()
    }

    fun loadUrl(url: String) {
        val fixed = normalizeUrl(url)
        currentUrl = fixed
        webViews[0]?.loadUrl(fixed)
    }

    fun goBack() {
        webViews[0]?.goBack()
    }

    fun goForward() {
        webViews[0]?.goForward()
    }

    fun reload() {
        webViews[0]?.reload()
    }

    override fun onCleared() {
        webViews.values.forEach {
            it.destroy()
        }

        webViews.clear()

        super.onCleared()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(
        context: Context,
        dnsManager: DnsManager
    ): WebView {

        // IMPORTANT:
        // Use the Activity/Compose context instead of applicationContext.
        // WebChromeClient needs an Activity-backed context to enter native
        // fullscreen and attach the custom video view to the activity decor.
        return WebView(context).apply {

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true

                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)

                mediaPlaybackRequiresUserGesture = false

                cacheMode = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true

                // Responsive viewport configuration.
                useWideViewPort = true
                loadWithOverviewMode = true

                // Modern Chrome Mobile User-Agent.
                userAgentString = CHROME_UA

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode =
                        WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    allowFileAccess = false
                }
            }

            CookieManager.getInstance().setAcceptCookie(true)

            CookieManager.getInstance()
                .setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(
                object {

                    @JavascriptInterface
                    fun onVideoFound(
                        url: String,
                        title: String,
                        pageUrl: String
                    ) {
                        onMediaFound(
                            url,
                            title,
                            pageUrl
                        )
                    }

                },
                "LinkShieldBridge"
            )

            webChromeClient =
                LinkShieldWebChromeClient(
                    this@UnblockShieldViewModel
                )

            // Let WebView use Android's native DNS/TLS stack.
            // DoH is used by app-owned OkHttp requests,
            // not by WebView internals.
            webViewClient =
                ShieldWebViewClient(
                    dnsManager,
                    this@UnblockShieldViewModel
                )

            loadUrl(currentUrl)
        }
    }
}
/**
 * Handles WebView-native video fullscreen
 * (e.g. YouTube HTML5 player).
 *
 * The fullscreen view is attached to the activity decor view
 * so it can cover the Compose hierarchy, while immersive system
 * UI is applied for a Chrome-like experience.
 *
 * State is restored in onHideCustomView().
 */
private class LinkShieldWebChromeClient(
    private val vm: UnblockShieldViewModel
) : android.webkit.WebChromeClient() {

    private var customView: View? = null

    private var originalSystemUiVisibility: Int = 0

    override fun onProgressChanged(
        view: WebView?,
        newProgress: Int
    ) {
        vm.updateLoading(newProgress < 100)

        super.onProgressChanged(
            view,
            newProgress
        )
    }

    override fun onReceivedTitle(
        view: WebView?,
        title: String?
    ) {
        vm.updatePageTitle(title.orEmpty())

        super.onReceivedTitle(
            view,
            title
        )
    }

    override fun onShowCustomView(
        view: View?,
        callback: CustomViewCallback?
    ) {
        if (view == null) {
            callback?.onCustomViewHidden()
            return
        }

        val activity =
            view.context.findActivity()
                ?: run {
                    callback.onCustomViewHidden()
                    return
                }

        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        customView = view

        originalSystemUiVisibility =
            activity.window.decorView.systemUiVisibility

        val decor =
            activity.window.decorView as? ViewGroup

        decor?.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        enterFullscreen(activity.window)
    }

    override fun onHideCustomView() {

        val view = customView
            ?: return

        val activity =
            view.context.findActivity()

        (view.parent as? ViewGroup)
            ?.removeView(view)

        customView = null

        activity?.let {
            restoreSystemUi(it.window)
        }
    }

    private fun enterFullscreen(
        window: Window
    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            window.setDecorFitsSystemWindows(false)

            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
            )

            window.insetsController?.systemBarsBehavior =
                android.view.WindowInsetsController
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        } else {

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun restoreSystemUi(
        window: Window
    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            window.setDecorFitsSystemWindows(true)

            window.insetsController?.show(
                android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
            )

        } else {

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                originalSystemUiVisibility
        }
    }

    private fun Context.findActivity(): Activity? {

        var current: Context = this

        while (current is android.content.ContextWrapper) {

            if (current is Activity) {
                return current
            }

            current.baseContext?.let {
                current = it
            } ?: break
        }

        return current as? Activity
    }
}

private class ShieldWebViewClient(
    private val dnsManager: DnsManager,
    private val vm: UnblockShieldViewModel
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {

        val uri =
            request?.url
                ?: return false

        return handleScheme(
            view,
            uri
        )
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        url: String?
    ): Boolean {

        return if (url == null) {
            false
        } else {
            handleScheme(
                view,
                Uri.parse(url)
            )
        }
    }

    private fun handleScheme(
        view: WebView?,
        uri: Uri
    ): Boolean {

        return when (uri.scheme?.lowercase()) {

            "http",
            "https" -> false

            "intent" -> {

                val raw = uri.toString()

                val fallback =
                    Regex(
                        ";S\\.browser_fallback_url=([^;]+)"
                    )
                        .find(raw)
                        ?.groupValues
                        ?.getOrNull(1)

                if (!fallback.isNullOrBlank()) {

                    runCatching {

                        val decoded =
                            URLDecoder.decode(
                                fallback,
                                "UTF-8"
                            )

                        if (
                            decoded.startsWith(
                                "http",
                                ignoreCase = true
                            )
                        ) {
                            view?.loadUrl(decoded)
                        }
                    }
                }

                true
            }

            else -> true
        }
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: android.graphics.Bitmap?
    ) {

        vm.updateLoading(true)

        url?.let(vm::updateUrl)
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?
    ) {

        vm.updateLoading(false)

        vm.updateNavigationState(
            view?.canGoBack() == true,
            view?.canGoForward() == true
        )

        url?.let(vm::updateUrl)

        view?.evaluateJavascript(
            JS_MEDIA_INTERCEPTOR,
            null
        )
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {

        super.onReceivedError(
            view,
            request,
            error
        )

        // Do not append "?retry=true" to the user's URL.
        // That can change the request and create
        // endless/incorrect retries.
        if (request?.isForMainFrame == true) {
            vm.updateLoading(false)
        }
    }
}

internal fun normalizeUrl(
    raw: String
): String {

    val value = raw.trim()

    if (value.isBlank()) {
        return "https://www.google.com"
    }

    return when {

        value.startsWith(
            "https://",
            ignoreCase = true
        ) -> value

        value.startsWith(
            "http://",
            ignoreCase = true
        ) -> value

        value.contains("://") -> value

        value.contains(".") ->
            "https://$value"

        else ->
            "https://www.google.com/search?q=${
                URLEncoder.encode(
                    value,
                    "UTF-8"
                )
            }"
    }
}
