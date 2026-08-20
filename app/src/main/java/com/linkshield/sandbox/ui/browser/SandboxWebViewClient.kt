package com.linkshield.sandbox.ui.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.linkshield.sandbox.ui.grabber.MediaSnifferState
import java.io.ByteArrayInputStream
import java.util.Locale

class SandboxWebViewClient(
    private val onPageChanged:
        ((String) -> Unit)? = null
) : WebViewClient() {

    // Common ad, tracker, and telemetry domains to block automatically
    private val blockedDomains = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adnxs.com",
        "moatads.com",
        "googletagmanager.com",
        "facebook.com/tr",
        "analytics.google.com",
        "hotjar.com"
    )

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val url = request.url.toString()
        if (url.isBlank()) {
            return false
        }
        return false
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        url: String
    ): Boolean {
        if (url.isBlank()) {
            return false
        }
        return false
    }

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: Bitmap?
    ) {
        super.onPageStarted(view, url, favicon)
        onPageChanged?.invoke(url)
    }

    override fun onPageFinished(
        view: WebView,
        url: String
    ) {
        super.onPageFinished(view, url)
        onPageChanged?.invoke(view.url ?: url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()

        // 1. AdGuard Filtering: Check if request is an ad or tracker
        if (isAdOrTracker(url)) {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                200,
                "OK",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0))
            )
        }

        // 2. Media Sniffer Inspection
        inspectResource(
            url,
            request.requestHeaders["Content-Type"]
        )

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        url: String
    ): WebResourceResponse? {
        // 1. AdGuard Filtering: Check if request is an ad or tracker
        if (isAdOrTracker(url)) {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                200,
                "OK",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0))
            )
        }

        // 2. Media Sniffer Inspection
        inspectResource(url, null)

        return super.shouldInterceptRequest(view, url)
    }

    private fun isAdOrTracker(url: String): Boolean {
        return blockedDomains.any { domain ->
            url.contains(domain, ignoreCase = true)
        }
    }

    private fun inspectResource(
        url: String,
        contentType: String?
    ) {
        if (url.isBlank()) {
            return
        }

        val lowerUrl = url.lowercase(Locale.US)
        val lowerType = contentType?.lowercase(Locale.US).orEmpty()

        val media =
            lowerType.startsWith("video/") ||
            lowerType.startsWith("audio/") ||
            MEDIA_EXTENSIONS.any {
                lowerUrl
                    .substringBefore("?")
                    .substringBefore("#")
                    .endsWith(it)
            } ||
            lowerUrl.contains(".m3u8") ||
            lowerUrl.contains(".mpd") ||
            lowerUrl.startsWith("blob:")

        if (!media) {
            return
        }

        MediaSnifferState.publish(
            url = url,
            mimeType = contentType.orEmpty(),
            extension = detectExtension(lowerUrl)
        )
    }

    private fun detectExtension(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#")
        return clean.substringAfterLast('.', "").take(10)
    }

    companion object {
        private val MEDIA_EXTENSIONS =
            setOf(
                ".mp4",
                ".webm",
                ".mkv",
                ".mov",
                ".mp3",
                ".m4a",
                ".aac",
                ".ogg",
                ".wav"
            )
    }
}

fun createSandboxWebViewClient(
    onPageChanged: ((String) -> Unit)? = null
): SandboxWebViewClient {
    return SandboxWebViewClient(onPageChanged = onPageChanged)
}
