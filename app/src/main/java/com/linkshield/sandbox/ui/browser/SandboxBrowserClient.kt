package com.linkshield.sandbox.ui.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.linkshield.sandbox.ui.grabber.MediaSnifferState
import java.util.Locale

class SandboxBrowserClient(
    private val onStarted:
        ((String) -> Unit)? = null,
    private val onFinished:
        ((String, String, Boolean, Boolean) -> Unit)? = null
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        return false
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        url: String
    ): Boolean {
        return false
    }

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: Bitmap?
    ) {
        super.onPageStarted(
            view,
            url,
            favicon
        )

        onStarted?.invoke(url)
    }

    override fun onPageFinished(
        view: WebView,
        url: String
    ) {
        super.onPageFinished(
            view,
            url
        )

        onFinished?.invoke(
            view.url ?: url,
            view.title.orEmpty(),
            view.canGoBack(),
            view.canGoForward()
        )
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {

        capture(
            request.url.toString(),
            request.requestHeaders[
                "Content-Type"
            ]
        )

        return super.shouldInterceptRequest(
            view,
            request
        )
    }

    override fun shouldInterceptRequest(
        view: WebView,
        url: String
    ): WebResourceResponse? {

        capture(
            url,
            null
        )

        return super.shouldInterceptRequest(
            view,
            url
        )
    }

    private fun capture(
        url: String,
        contentType: String?
    ) {
        if (url.isBlank()) {
            return
        }

        val normalized =
            url.lowercase(Locale.US)

        val type =
            contentType
                ?.lowercase(Locale.US)
                .orEmpty()

        val isMedia =
            type.startsWith("audio/") ||
            type.startsWith("video/") ||
            MEDIA_EXTENSIONS.any {
                normalized
                    .substringBefore("?")
                    .substringBefore("#")
                    .endsWith(it)
            } ||
            normalized.contains(".m3u8") ||
            normalized.contains(".mpd") ||
            normalized.startsWith("blob:")

        if (!isMedia) {
            return
        }

        MediaSnifferState.publish(
            url = url,
            mimeType =
                contentType.orEmpty(),
            extension =
                extensionOf(normalized)
        )
    }

    private fun extensionOf(
        url: String
    ): String {
        val clean =
            url.substringBefore("?")
                .substringBefore("#")

        return clean.substringAfterLast(
            '.',
            ""
        )
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
fun createSandboxBrowserClient(
    onStarted:
        ((String) -> Unit)? = null,
    onFinished:
        ((String, String, Boolean, Boolean) -> Unit)? = null
): SandboxBrowserClient {
    return SandboxBrowserClient(
        onStarted = onStarted,
        onFinished = onFinished
    )
}
