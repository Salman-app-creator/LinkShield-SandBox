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

object YtDlpEngine {
    private const val TAG = "LinkShieldYtDlp"
    private const val RESOLVE_TIMEOUT_MS = 45_000L
    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (initialized.get()) return
        synchronized(initialized) {
            if (initialized.get()) return
            YtDlp.init(context.applicationContext)
            initialized.set(true)
            Log.i(TAG, "yt-dlp initialized")
        }
    }

    fun isYouTubeUrl(rawUrl: String): Boolean {
        val host = try { Uri.parse(rawUrl.trim()).host?.lowercase().orEmpty() } catch (_: Throwable) { "" }
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host.endsWith(".youtu.be")
    }

    suspend fun resolveMedia(
        context: Context,
        pageUrl: String,
        resolution: String = "1080p",
        audioOnly: Boolean = false
    ): YtDlpResolveResult = withContext(Dispatchers.IO) {
        if (!isYouTubeUrl(pageUrl)) return@withContext YtDlpResolveResult(false, error = "Not a YouTube URL")
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

        try {
            if (audioOnly) {
                val audio = resolveFirstUrl(pageUrl, "bestaudio/best")
                if (audio == null) return@withContext YtDlpResolveResult(false, error = "yt-dlp could not resolve the audio stream.")
                return@withContext YtDlpResolveResult(
                    true, url = audio, filename = "LinkShield_YouTube_${System.currentTimeMillis()}.mp3",
                    mimeType = "audio/mpeg"
                )
            }

            // First try a progressive MP4 so ordinary 360p-1080p downloads remain simple.
            val progressive = resolveFirstUrl(pageUrl, "best[height<=${quality}][ext=mp4]/best[height<=${quality}]")
            if (progressive != null) {
                return@withContext YtDlpResolveResult(
                    true, url = progressive,
                    filename = "LinkShield_YouTube_${System.currentTimeMillis()}.mp4",
                    mimeType = "video/mp4"
                )
            }

            // High resolutions commonly require separate video/audio streams.
            val video = resolveFirstUrl(pageUrl, "bestvideo[height<=${quality}]/bestvideo")
            val audio = resolveFirstUrl(pageUrl, "bestaudio/best")
            if (video != null && audio != null) {
                return@withContext YtDlpResolveResult(
                    true, url = video, secondaryUrl = audio,
                    filename = "LinkShield_YouTube_${System.currentTimeMillis()}.mp4",
                    mimeType = "video/mp4"
                )
            }
            YtDlpResolveResult(false, error = "yt-dlp could not resolve a compatible YouTube stream.")
        } catch (t: Throwable) {
            Log.e(TAG, "yt-dlp resolve failed", t)
            YtDlpResolveResult(false, error = t.localizedMessage ?: "yt-dlp could not resolve this YouTube URL.")
        }
    }

    suspend fun resolveDirectUrl(context: Context, pageUrl: String, resolution: String = "1080p") =
        resolveMedia(context, pageUrl, resolution, false)

    private suspend fun resolveFirstUrl(pageUrl: String, format: String): String? {
        val request = YtDlpRequest(pageUrl)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--no-check-certificates")
            .addOption("-f", format)
            .addOption("-g")

        val result = CompletableDeferred<String?>()
        YtDlp.executeAsync(request) { _, _, line ->
            val candidate = line?.trim().orEmpty()
            if ((candidate.startsWith("http://") || candidate.startsWith("https://")) && !result.isCompleted) {
                result.complete(candidate)
            }
        }
        return withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { result.await() }
    }
}

data class YtDlpResolveResult(
    val success: Boolean,
    val url: String? = null,
    val secondaryUrl: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val error: String? = null
)
