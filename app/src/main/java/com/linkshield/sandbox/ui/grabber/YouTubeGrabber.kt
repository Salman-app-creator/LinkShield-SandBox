package com.linkshield.sandbox.ui.grabber

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/grabber/YouTubeGrabber.kt

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
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

    // Piped public instances — fallback when yt-dlp fails
    private val PIPED_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://piped-api.garudalinux.org",
        "https://api.piped.projectsegfault.com"
    )

    private val initialized = AtomicBoolean(false)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

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

    fun extractVideoId(rawUrl: String): String? {
        return try {
            val uri = Uri.parse(rawUrl.trim())
            val host = uri.host?.lowercase().orEmpty()
            when {
                host == "youtu.be" -> uri.lastPathSegment
                host.endsWith("youtube.com") ->
                    uri.getQueryParameter("v")
                        ?: uri.path?.substringAfter("/shorts/")?.substringBefore("/")
                else -> null
            }
        } catch (_: Throwable) { null }
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

        val quality = when (resolution.uppercase().trim()) {
            "4K", "2160P" -> 2160
            "1440P"       -> 1440
            "1080P"       -> 1080
            "720P"        -> 720
            "480P"        -> 480
            "360P"        -> 360
            else          -> 1080
        }

        // Method 1: yt-dlp with Android player client
        val ytdlpResult = tryYtDlp(context, pageUrl, quality, audioOnly)
        if (ytdlpResult.success) {
            Log.i(TAG, "yt-dlp succeeded")
            return@withContext ytdlpResult
        }
        Log.w(TAG, "yt-dlp failed: ${ytdlpResult.error} — trying Piped fallback")

        // Method 2: Piped API fallback
        val videoId = extractVideoId(pageUrl)
        if (!videoId.isNullOrBlank()) {
            val pipedResult = tryPiped(videoId, quality, audioOnly)
            if (pipedResult.success) {
                Log.i(TAG, "Piped fallback succeeded")
                return@withContext pipedResult
            }
            Log.w(TAG, "Piped also failed: ${pipedResult.error}")
        }

        YouTubeResult(
            false,
            error = "YouTube extraction failed. Try again later.\n(${ytdlpResult.error})"
        )
    }

    // -----------------------------------------------------------------------
    // Method 1: yt-dlp with Android YouTube client
    // Spoofs request as Android YouTube app — bypasses bot detection
    // -----------------------------------------------------------------------
    private suspend fun tryYtDlp(
        context: Context,
        pageUrl: String,
        quality: Int,
        audioOnly: Boolean
    ): YouTubeResult = withContext(Dispatchers.IO) {
        try {
            initialize(context)
        } catch (t: Throwable) {
            return@withContext YouTubeResult(false, error = "yt-dlp init failed: ${t.message}")
        }

        return@withContext try {
            val request = YoutubeDLRequest(pageUrl).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--no-check-certificates")

                // KEY FIX: Spoof as Android YouTube app
                // YouTube thinks request is from official Android app — no sign-in needed
                addOption("--extractor-args", "youtube:player_client=android")
                addOption(
                    "--user-agent",
                    "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"
                )

                if (audioOnly) {
                    addOption("-f", "bestaudio/best")
                } else {
                    addOption(
                        "-f",
                        "best[height<=$quality][ext=mp4]/best[height<=$quality]/best"
                    )
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
                YouTubeResult(
                    success  = true,
                    url      = url,
                    filename = "YouTube_${System.currentTimeMillis()}.$ext",
                    mimeType = mime
                )
            } else {
                YouTubeResult(false, error = response.err?.trim()
                    ?.lines()?.firstOrNull { it.isNotBlank() }
                    ?: "yt-dlp returned no URL")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "yt-dlp error", t)
            YouTubeResult(false, error = t.localizedMessage ?: "yt-dlp failed")
        }
    }

    // -----------------------------------------------------------------------
    // Method 2: Piped API — open source YouTube frontend
    // No auth needed, multiple public instances
    // -----------------------------------------------------------------------
    private suspend fun tryPiped(
        videoId: String,
        quality: Int,
        audioOnly: Boolean
    ): YouTubeResult = withContext(Dispatchers.IO) {

        for (instance in PIPED_INSTANCES) {
            try {
                val request = Request.Builder()
                    .url("$instance/streams/$videoId")
                    .header("User-Agent", "LinkShield/2.6")
                    .build()

                val body = httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: continue

                val json = JSONObject(body)

                if (audioOnly) {
                    val audioStreams = json.optJSONArray("audioStreams") ?: continue
                    val best = (0 until audioStreams.length())
                        .mapNotNull { audioStreams.optJSONObject(it) }
                        .maxByOrNull { it.optInt("bitrate", 0) }
                    val url = best?.optString("url").orEmpty()
                    if (url.isNotBlank()) {
                        return@withContext YouTubeResult(
                            success  = true,
                            url      = url,
                            filename = "YouTube_${System.currentTimeMillis()}.mp3",
                            mimeType = "audio/mpeg"
                        )
                    }
                } else {
                    val videoStreams = json.optJSONArray("videoStreams") ?: continue
                    val streams = (0 until videoStreams.length())
                        .mapNotNull { videoStreams.optJSONObject(it) }
                        .filter { it.optString("format").contains("MPEG_4", ignoreCase = true) }

                    // Pick stream closest to requested quality
                    val best = streams
                        .filter { it.optInt("height", 0) <= quality }
                        .maxByOrNull { it.optInt("height", 0) }
                        ?: streams.minByOrNull {
                            Math.abs(it.optInt("height", 0) - quality)
                        }

                    val url = best?.optString("url").orEmpty()
                    if (url.isNotBlank()) {
                        return@withContext YouTubeResult(
                            success  = true,
                            url      = url,
                            filename = "YouTube_${System.currentTimeMillis()}.mp4",
                            mimeType = "video/mp4"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Piped instance $instance failed: ${e.message}")
                continue
            }
        }

        YouTubeResult(false, error = "All Piped instances failed")
    }
}
