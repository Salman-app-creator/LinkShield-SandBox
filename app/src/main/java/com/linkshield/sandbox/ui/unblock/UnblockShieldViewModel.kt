package com.linkshield.sandbox.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.grabber.CapturedMediaItem
import com.linkshield.sandbox.ui.grabber.WebSnifferManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.net.URLEncoder

internal const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; SM-S918B) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

class UnblockShieldViewModel : ViewModel() {

    private val webViews =
        mutableMapOf<Int, WebView>()

    private val _mediaUrls =
        MutableStateFlow<List<CapturedMediaItem>>(
            emptyList()
        )

    val mediaUrls: StateFlow<List<CapturedMediaItem>> =
        _mediaUrls.asStateFlow()

    private val _latestMedia =
        MutableStateFlow<CapturedMediaItem?>(null)

    val latestMedia: StateFlow<CapturedMediaItem?> =
        _latestMedia.asStateFlow()

    var currentUrl by mutableStateOf(
        "https://www.google.com"
    )
        private set

    var isLoading by mutableStateOf(false)
        private set

    var canGoBack by mutableStateOf(false)
        private set

    var canGoForward by mutableStateOf(false)
        private set

    var pageTitle by mutableStateOf("")
        private set

    fun getOrCreateWebView(
        context: Context,
        tabIndex: Int,
        dnsManager: DnsManager
    ): WebView {
        return webViews.getOrPut(tabIndex) {
            buildWebView(
                context.applicationContext,
                dnsManager
            )
        }
    }

    fun getExistingWebView(
        tabIndex: Int = 0
    ): WebView? {
        return webViews[tabIndex]
    }

    fun updateUrl(url: String) {
        if (url.isNotBlank()) {
            currentUrl = url
        }
    }

    fun updateLoading(
        loading: Boolean
    ) {
        isLoading = loading
    }

    fun updateNavigationState(
        back: Boolean,
        forward: Boolean
    ) {
        canGoBack = back
        canGoForward = forward
    }

    fun updatePageTitle(
        title: String
    ) {
        pageTitle = title
    }

    fun onMediaFound(
        url: String,
        title: String = "",
        pageUrl: String = currentUrl
    ) {
        val cleanUrl =
            url.trim()

        if (cleanUrl.isBlank()) {
            return
        }

        val item =
            CapturedMediaItem(
                url = cleanUrl,
                title = title.ifBlank {
                    pageTitle
                },
                pageUrl = pageUrl.ifBlank {
                    currentUrl
                }
            )

        val existing =
            _mediaUrls.value

        if (
            existing.any {
                it.url == item.url
            }
        ) {
            _latestMedia.value =
                existing.lastOrNull {
                    it.url == item.url
                } ?: item
            return
        }

        val updated =
            (existing + item).takeLast(30)

        _mediaUrls.value = updated
        _latestMedia.value = item
    }

    fun clearMedia() {
        _mediaUrls.value =
            emptyList()

        _latestMedia.value =
            null
    }

    fun consumeLatestMedia() {
        _latestMedia.value = null
    }

    fun loadUrl(
        rawUrl: String
    ) {
        val url =
            normalizeUrl(rawUrl)

        currentUrl = url

        webViews[0]?.loadUrl(url)
    }

    fun goBack() {
        webViews[0]?.let { webView ->
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }
    }

    fun goForward() {
        webViews[0]?.let { webView ->
            if (webView.canGoForward()) {
                webView.goForward()
            }
        }
    }

    fun reload() {
        webViews[0]?.reload()
    }

    private fun buildWebView(
        context: Context,
        dnsManager: DnsManager
    ): WebView {
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
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = CHROME_UA

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.LOLLIPOP
                ) {
                    mixedContentMode =
                        WebSettings
                            .MIXED_CONTENT_COMPATIBILITY_MODE
                }

                @Suppress("DEPRECATION")
                allowFileAccess = false
            }

            CookieManager
                .getInstance()
                .setAcceptCookie(true)

            CookieManager
                .getInstance()
                .setAcceptThirdPartyCookies(
                    this,
                    true
                )

            webChromeClient =
                LinkShieldChromeClient(
                    this@UnblockShieldViewModel
                )

            webViewClient =
                LinkShieldWebViewClient(
                    this@UnblockShieldViewModel,
                    dnsManager
                )

            loadUrl(currentUrl)
        }
    }

    override fun onCleared() {
        webViews.values.forEach { webView ->
            runCatching {
                webView.stopLoading()
                webView.webViewClient = null
                webView.webChromeClient = null
                webView.removeAllViews()
                webView.destroy()
            }
        }

        webViews.clear()
        super.onCleared()
    }
}
private class LinkShieldChromeClient(
    private val vm: UnblockShieldViewModel
) : WebChromeClient() {

    private var customView: View? = null
    private var oldUiVisibility = 0

    override fun onProgressChanged(
        view: WebView?,
        newProgress: Int
    ) {
        vm.updateLoading(newProgress < 100)
        super.onProgressChanged(view, newProgress)
    }

    override fun onReceivedTitle(
        view: WebView?,
        title: String?
    ) {
        vm.updatePageTitle(title.orEmpty())
        super.onReceivedTitle(view, title)
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

        if (activity == null || customView != null) {
            callback.onCustomViewHidden()
            return
        }

        customView = view

        oldUiVisibility =
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

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {
            activity.window.setDecorFitsSystemWindows(false)
            activity.window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onHideCustomView() {
        val view =
            customView ?: return

        val activity =
            view.context.findActivity()

        (view.parent as? ViewGroup)
            ?.removeView(view)

        customView = null

        activity?.let {
            restoreWindow(it.window)
        }
    }

    private fun restoreWindow(
        window: Window
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(
                android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                oldUiVisibility
        }
    }
}

private class LinkShieldWebViewClient(
    private val vm: UnblockShieldViewModel,
    private val dnsManager: DnsManager
) : WebViewClient() {

    private val sniffer =
        WebSnifferManager()

    private fun capture(
        url: String?
    ) {
        if (url.isNullOrBlank()) return

        if (sniffer.isMediaUrl(url)) {
            vm.onMediaFound(
                url = url,
                title = vm.pageTitle,
                pageUrl = vm.currentUrl
            )
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        capture(request?.url?.toString())

        return super.shouldInterceptRequest(
            view,
            request
        )
    }

    @Deprecated("Deprecated in Java")
    override fun shouldInterceptRequest(
        view: WebView?,
        url: String?
    ): WebResourceResponse? {
        capture(url)

        return super.shouldInterceptRequest(
            view,
            url
        )
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        return handleUri(
            view,
            request?.url
        )
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        url: String?
    ): Boolean {
        return handleUri(
            view,
            url?.let(Uri::parse)
        )
    }

    private fun handleUri(
        view: WebView?,
        uri: Uri?
    ): Boolean {
        uri ?: return false

        return when (
            uri.scheme?.lowercase()
        ) {
            "http", "https" -> false

            "intent" -> {
                val fallback =
                    Regex(
                        ";S\\.browser_fallback_url=([^;]+)"
                    )
                        .find(uri.toString())
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
                                true
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

        super.onPageStarted(
            view,
            url,
            favicon
        )
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

        sniffer.inject(
            view,
            vm.pageTitle,
            vm.currentUrl,
            vm::onMediaFound
        )

        super.onPageFinished(
            view,
            url
        )
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        if (request?.isForMainFrame == true) {
            vm.updateLoading(false)
        }

        super.onReceivedError(
            view,
            request,
            error
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this

    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }

        current.baseContext?.let {
            current = it
        } ?: break
    }

    return current as? Activity
}

internal fun normalizeUrl(
    raw: String
): String {
    val value = raw.trim()

    if (value.isBlank()) {
        return "https://www.google.com"
    }

    return when {
        value.startsWith("https://", true) ->
            value

        value.startsWith("http://", true) ->
            value

        value.contains("://") ->
            value

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
