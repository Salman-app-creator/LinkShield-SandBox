package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class YouTubeResult(
    val success: Boolean,
    val url: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val error: String? = null
)

object YouTubeGrabber {

    private const val TAG = "YouTubeGrabber"
    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (initialized.get()) return
        synchronized(this) {
            if (initialized.get()) return
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                initialized.set(true)
                Log.i(TAG, "yt-dlp initialized")
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

    suspend fun extract(
        context: Context,
        pageUrl: String,
        resolution: String = "1080p",
        audioOnly: Boolean = false
    ): YouTubeResult = withContext(Dispatchers.IO) {

        if (!isYouTubeUrl(pageUrl)) {
            return@withContext YouTubeResult(false, error = "Not a YouTube URL")
        }

        try { initialize(context) } catch (t: Throwable) {
            return@withContext YouTubeResult(false, error = "yt-dlp init failed: ${t.message}")
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
            val request = YoutubeDLRequest(pageUrl).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--no-check-certificates")
                if (audioOnly) {
                    addOption("-f", "bestaudio/best")
                } else {
                    addOption("-f", "best[height<=$quality][ext=mp4]/best[height<=$quality]/best")
                }
                addOption("-g")
            }

            val response = YoutubeDL.getInstance().execute(
                request, null
            ) { _: Float, _: Long, _: String -> }

            val output = response.out?.trim().orEmpty()
            val url = output.lines()
                .map { it.trim() }
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

            if (!url.isNullOrBlank()) {
                val ext  = if (audioOnly) "mp3" else "mp4"
                val mime = if (audioOnly) "audio/mpeg" else "video/mp4"
                YouTubeResult(true, url = url,
                    filename = "YouTube_${System.currentTimeMillis()}.$ext", mimeType = mime)
            } else {
                YouTubeResult(false, error = "yt-dlp: no stream URL found")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "extraction failed", t)
            YouTubeResult(false, error = t.localizedMessage ?: "yt-dlp extraction failed")
        }
    }
}
