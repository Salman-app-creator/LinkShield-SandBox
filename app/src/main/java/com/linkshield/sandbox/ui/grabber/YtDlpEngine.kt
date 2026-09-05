package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

data class YtDlpResult(
    val success: Boolean,
    val url: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val error: String? = null
)

object YtDlpEngine {

    private const val TAG             = "YtDlpEngine"
    private const val RESOLVE_TIMEOUT = 40_000L

    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (initialized.get()) return
        synchronized(this) {
            if (initialized.get()) return
            try {
                YtDlp.init(context.applicationContext)
                initialized.set(true)
                Log.i(TAG, "yt-dlp initialized successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "yt-dlp init failed: ${t.message}", t)
                throw t
            }
        }
    }

    fun isYouTubeUrl(rawUrl: String): Boolean {
        val host = try {
            Uri.parse(rawUrl.trim()).host?.lowercase().orEmpty()
        } catch (_: Throwable) { "" }
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
               host == "youtu.be"   || host.endsWith(".youtu.be")
    }

    suspend fun resolve(
        context: Context,
        pageUrl: String,
        resolution: String = "1080p",
        audioOnly: Boolean = false
    ): YtDlpResult = withContext(Dispatchers.IO) {

        if (!isYouTubeUrl(pageUrl)) {
            return@withContext YtDlpResult(false, error = "Not a YouTube URL")
        }

        try { initialize(context) } catch (t: Throwable) {
            return@withContext YtDlpResult(false, error = "yt-dlp init failed: ${t.message}")
        }

        val quality = when (resolution.uppercase().trim()) {
            "4K", "2160P" -> 2160
            "1440P"       -> 1440
            "1080P"       -> 1080
            "720P"        -> 720
            "480P"        -> 480
            "360P"        -> 360
            else          -> 1080
        }

        return@withContext try {
            if (audioOnly) {
                val url = resolveUrl(pageUrl, "bestaudio/best")
                    ?: return@withContext YtDlpResult(false, error = "yt-dlp: no audio stream found")
                YtDlpResult(
                    success  = true,
                    url      = url,
                    filename = "YT_${System.currentTimeMillis()}.mp3",
                    mimeType = "audio/mpeg"
                )
            } else {
                val progressive = resolveUrl(
                    pageUrl,
                    "best[height<=$quality][ext=mp4]/best[height<=$quality]/best"
                )
                if (progressive != null) {
                    return@withContext YtDlpResult(
                        success  = true,
                        url      = progressive,
                        filename = "YT_${System.currentTimeMillis()}.mp4",
                        mimeType = "video/mp4"
                    )
                }
                val fallback = resolveUrl(pageUrl, "best")
                if (fallback != null) {
                    YtDlpResult(
                        success  = true,
                        url      = fallback,
                        filename = "YT_${System.currentTimeMillis()}.mp4",
                        mimeType = "video/mp4"
                    )
                } else {
                    YtDlpResult(false, error = "yt-dlp: no stream found for $resolution")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "resolve failed", t)
            YtDlpResult(false, error = t.localizedMessage ?: "yt-dlp extraction failed")
        }
    }

    private suspend fun resolveUrl(pageUrl: String, format: String): String? {
        val request = YtDlpRequest(pageUrl)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--no-check-certificates")
            .addOption("-f", format)
            .addOption("-g")

        var resultUrl: String? = null

        return withTimeoutOrNull(RESOLVE_TIMEOUT) {
            YtDlp.executeAsync(request) { _, _, line ->
                val candidate = line?.trim().orEmpty()
                if (resultUrl == null &&
                    (candidate.startsWith("http://") || candidate.startsWith("https://"))
                ) {
                    resultUrl = candidate
                }
            }
            resultUrl
        }
    }
}
