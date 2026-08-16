package com.linkshield.sandbox.ui.grabber

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Locale

class WebSnifferManager(
    private val onMediaCaptured: (
        CapturedMediaItem
    ) -> Unit
) {

    private val extensions = setOf(
        ".mp4", ".m3u8", ".mp3", ".webm",
        ".mkv", ".mov", ".m4a", ".ogg",
        ".aac", ".wav", ".mpd", ".ts"
    )

    fun isMediaUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()

        if (url.isBlank()) return false

        if (url.startsWith("blob:", true)) {
            return true
        }

        val clean = url
            .substringBefore("?")
            .substringBefore("#")
            .lowercase(Locale.US)

        return extensions.any {
            clean.endsWith(it)
        } || listOf(
            "videoplayback",
            "manifest",
            "playlist",
            "media",
            "audio",
            "video"
        ).any {
            url.lowercase(Locale.US)
                .contains(it)
        }
    }

    fun createClient(
        pageTitle: () -> String,
        pageUrl: () -> String
    ): WebViewClient {

        return object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {

                val url =
                    request?.url?.toString()
                        .orEmpty()

                capture(
                    url = url,
                    title = pageTitle(),
                    pageUrl = pageUrl()
                )

                return super.shouldInterceptRequest(
                    view,
                    request
                )
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                url: String?
            ): WebResourceResponse? {

                capture(
                    url = url.orEmpty(),
                    title = pageTitle(),
                    pageUrl = pageUrl()
                )

                return super.shouldInterceptRequest(
                    view,
                    url
                )
            }
        }
    }

    private fun capture(
        url: String,
        title: String,
        pageUrl: String
    ) {
        if (!isMediaUrl(url)) return

        val cleanUrl =
            url.trim()

        if (cleanUrl.isBlank()) return

        onMediaCaptured(
            CapturedMediaItem(
                url = cleanUrl,
                title = title,
                pageUrl = pageUrl,
                mimeType = guessMimeType(
                    cleanUrl
                ),
                extension = guessExtension(
                    cleanUrl
                )
            )
        )
    }
        private fun guessExtension(
        url: String
    ): String {

        val clean =
            url.substringBefore("?")
                .substringBefore("#")
                .lowercase(Locale.US)

        return extensions.firstOrNull {
            clean.endsWith(it)
        }?.removePrefix(".")
            .orEmpty()
    }

    private fun guessMimeType(
        url: String
    ): String {

        return when {
            url.contains(
                ".m3u8",
                true
            ) ->
                "application/vnd.apple.mpegurl"

            url.contains(
                ".mp3",
                true
            ) ->
                "audio/mpeg"

            url.contains(
                ".m4a",
                true
            ) ->
                "audio/mp4"

            url.contains(
                ".webm",
                true
            ) ->
                "video/webm"

            url.contains(
                ".mp4",
                true
            ) ->
                "video/mp4"

            url.contains(
                ".mpd",
                true
            ) ->
                "application/dash+xml"

            else -> ""
        }
    }
}
