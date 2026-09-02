package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local yt-dlp bridge used by the grabber backend.
 *
 * UI is intentionally untouched. The current locked Grabber screen already
 * consumes a direct media URL, so this engine resolves YouTube URLs to a
 * single playable stream and lets the existing DownloadManager save it.
 */
object YtDlpEngine {

    private const val TAG = "LinkShieldYtDlp"
    private const val RESOLVE_TIMEOUT_MS = 45_000L
    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (initialized.get()) return
        synchronized(initialized) {
            if (initialized.get()) return
            try {
                YtDlp.init(context.applicationContext)
                initialized.set(true)
                Log.i(TAG, "yt-dlp initialized")
            } catch (t: Throwable) {
                Log.e(TAG, "yt-dlp initialization failed", t)
                throw t
            }
        }
    }

    fun isYouTubeUrl(rawUrl: String): Boolean {
        val host = try {
            Uri.parse(rawUrl.trim()).host?.lowercase().orEmpty()
        } catch (_: Throwable) {
            ""
        }
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host.endsWith(".youtu.be")
    }

    /**
     * Resolves a single direct URL using yt-dlp's -g/--get-url mode.
     * The free maintained library exposes progress, ETA and output line via
     * executeAsync; the first HTTP(S) output line is the playable URL.
     */
    suspend fun resolveDirectUrl(
        context: Context,
        pageUrl: String,
        resolution: String = "1080p"
    ): YtDlpResolveResult = withContext(Dispatchers.IO) {
        if (!isYouTubeUrl(pageUrl)) {
            return@withContext YtDlpResolveResult(false, error = "Not a YouTube URL")
        }

        initialize(context)

        val quality = when (resolution.lowercase()) {
            "4k", "2160p" -> 2160
            "1440p" -> 1440
            "1080p" -> 1080
            "720p" -> 720
            "480p" -> 480
            "360p" -> 360
            else -> 1080
        }

        // Prefer a progressive MP4 stream so the existing locked UI can pass
        // the returned URL directly to Android DownloadManager without a
        // local merge step. Fall back to the best stream if unavailable.
        val request = YtDlpRequest(pageUrl)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--no-check-certificates")
            .addOption("-f", "best[height<=${quality}][ext=mp4]/best[height<=${quality}]/best")
            .addOption("-g")

        val result = CompletableDeferred<YtDlpResolveResult>()

        try {
            YtDlp.executeAsync(request) { _, _, line ->
                val candidate = line?.trim().orEmpty()
                if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                    if (!result.isCompleted) {
                        result.complete(
                            YtDlpResolveResult(
                                success = true,
                                url = candidate,
                                filename = buildFilename(pageUrl, candidate),
                                mimeType = guessMime(candidate)
                            )
                        )
                    }
                }
            }

            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                result.await()
            } ?: YtDlpResolveResult(
                success = false,
                error = "yt-dlp timed out while resolving the YouTube media stream."
            )
        } catch (t: Throwable) {
            Log.e(TAG, "yt-dlp resolve failed", t)
            YtDlpResolveResult(
                success = false,
                error = t.localizedMessage ?: "yt-dlp could not resolve this YouTube URL."
            )
        }
    }

    private fun buildFilename(pageUrl: String, mediaUrl: String): String {
        val ext = mediaUrl.substringBefore('?').substringAfterLast('.', "mp4")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "mp4"
        return "LinkShield_YouTube_${System.currentTimeMillis()}.$ext"
    }

    private fun guessMime(url: String): String {
        return when (url.substringBefore('?').lowercase()) {
            else -> when {
                url.substringBefore('?').endsWith(".mp4") -> "video/mp4"
                url.substringBefore('?').endsWith(".webm") -> "video/webm"
                url.substringBefore('?').endsWith(".m4a") -> "audio/mp4"
                else -> "video/mp4"
            }
        }
    }
}

data class YtDlpResolveResult(
    val success: Boolean,
    val url: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val error: String? = null
)
