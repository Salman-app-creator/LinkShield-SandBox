package com.linkshield.sandbox.ui.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.linkshield.sandbox.ui.grabber.MediaSnifferState
import com.linkshield.sandbox.adblock.AdBlockEngine
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
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "ad.doubleclick.net",
        "g.doubleclick.net",
        "stats.g.doubleclick.net",
        "cm.g.doubleclick.net",
        "tpc.googlesyndication.com",
        "googletagmanager.com",
        "googletagservices.com",
        "google-analytics.com",
        "adnxs.com",
        "appnexus.com",
        "adsrvr.org",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "moatads.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "analytics.facebook.com",
        "hotjar.com",
        "clarity.ms",
        "fullstory.com",
        "mouseflow.com",
        "mixpanel.com",
        "amplitude.com",
        "segment.com",
        "sentry.io"
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
            request.requestHeaders["Content-Type"],
            view.url.orEmpty()
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
        inspectResource(url, null, view.url.orEmpty())

        return super.shouldInterceptRequest(view, url)
    }

    private fun isAdOrTracker(url: String): Boolean {
        if (url.isBlank()) {
            return false
        }

        val lower = url.lowercase(Locale.US)

        // Use the application's full AdBlockEngine ruleset first.
        // It is initialized by LinkShieldApp and includes the bundled rules.
        if (AdBlockEngine.getInstance().shouldBlock(url)) {
            return true
        }

        // Keep the lightweight WebView-specific domain rules as a fallback.
        if (blockedDomains.any { domain ->
                lower.contains(domain.lowercase(Locale.US))
            }
        ) {
            return true
        }

        // YouTube-specific ad/telemetry request patterns.
        // Do not block youtube.com itself; only known ad endpoints.
        if (lower.contains("youtube.com")) {
            val youtubeAdPatterns = listOf(
                "/pagead/",
                "/api/stats/ads",
                "/youtube.com/api/stats/ads",
                "/ptracking",
                "/youtubei/v1/log_event",
                "/youtubei/v1/feedback"
            )

            if (youtubeAdPatterns.any { lower.contains(it) }) {
                return true
            }
        }

        // Some YouTube ad metadata is carried on googlevideo playback URLs.
        if (lower.contains("googlevideo.com")) {
            val adParameters = listOf(
                "adformat=",
                "oad=",
                "ctier=",
                "afs=",
                "ad_type=",
                "adunit="
            )

            if (adParameters.any { lower.contains(it) }) {
                return true
            }
        }

        return false
    }

    private fun inspectResource(
        url: String,
        contentType: String?,
        pageUrl: String
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
            pageUrl = pageUrl,
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
