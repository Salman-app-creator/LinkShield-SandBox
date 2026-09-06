package com.linkshield.sandbox.ui.grabber

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/grabber/YouTubeGrabber.kt

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class YouTubeResult(
    val success: Boolean,
    val url: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val error: String? = null
)

object YouTubeGrabber {

    private const val TAG = "YouTubeGrabber"

    private val PIPED_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://piped-api.garudalinux.org",
        "https://api.piped.projectsegfault.com"
    )

    private val INVIDIOUS_INSTANCES = listOf(
        "https://invidious.privacyredirect.com",
        "https://iv.datura.network",
        "https://invidious.nerdvpn.de"
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
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

        val videoId = extractVideoId(pageUrl)
            ?: return@withContext YouTubeResult(false, error = "Could not extract video ID")

        // Method 1: Piped API
        val pipedResult = tryPiped(videoId, quality, audioOnly)
        if (pipedResult.success) {
            Log.i(TAG, "Piped succeeded")
            return@withContext pipedResult
        }
        Log.w(TAG, "Piped failed: ${pipedResult.error} — trying Invidious")

        // Method 2: Invidious API
        val invidiousResult = tryInvidious(videoId, quality, audioOnly)
        if (invidiousResult.success) {
            Log.i(TAG, "Invidious succeeded")
            return@withContext invidiousResult
        }
        Log.w(TAG, "Invidious failed: ${invidiousResult.error}")

        YouTubeResult(
            false,
            error = "YouTube extraction failed. Try again later.\n(${pipedResult.error})"
        )
    }

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

    private suspend fun tryInvidious(
        videoId: String,
        quality: Int,
        audioOnly: Boolean
    ): YouTubeResult = withContext(Dispatchers.IO) {
        for (instance in INVIDIOUS_INSTANCES) {
            try {
                val request = Request.Builder()
                    .url("$instance/api/v1/videos/$videoId")
                    .header("User-Agent", "LinkShield/2.6")
                    .build()

                val body = httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: continue

                val json = JSONObject(body)
                val title = json.optString("title", "video")

                if (audioOnly) {
                    val formats = json.optJSONArray("adaptiveFormats") ?: continue
                    var bestAudio: JSONObject? = null
                    var bestBitrate = 0
                    for (i in 0 until formats.length()) {
                        val fmt = formats.optJSONObject(i) ?: continue
                        if (!fmt.optString("type").startsWith("audio/")) continue
                        val bitrate = fmt.optInt("bitrate", 0)
                        if (bitrate > bestBitrate) {
                            bestBitrate = bitrate
                            bestAudio = fmt
                        }
                    }
                    val url = bestAudio?.optString("url").orEmpty()
                    if (url.isNotBlank()) {
                        return@withContext YouTubeResult(
                            success  = true,
                            url      = url,
                            filename = "${title}.mp3",
                            mimeType = "audio/mpeg"
                        )
                    }
                } else {
                    val adaptive = json.optJSONArray("adaptiveFormats")
                    val muxed = json.optJSONArray("formatStreams")

                    if (adaptive != null) {
                        var bestVideo: JSONObject? = null
                        var bestHeight = 0
                        for (i in 0 until adaptive.length()) {
                            val fmt = adaptive.optJSONObject(i) ?: continue
                            if (!fmt.optString("type").startsWith("video/")) continue
                            val h = fmt.optInt("height", 0)
                            if (h <= quality && h > bestHeight) {
                                bestHeight = h
                                bestVideo = fmt
                            }
                        }
                        val url = bestVideo?.optString("url").orEmpty()
                        if (url.isNotBlank()) {
                            return@withContext YouTubeResult(
                                success  = true,
                                url      = url,
                                filename = "${title}_${bestHeight}p.mp4",
                                mimeType = "video/mp4"
                            )
                        }
                    }

                    if (muxed != null) {
                        var bestMuxed: JSONObject? = null
                        var bestHeight = 0
                        for (i in 0 until muxed.length()) {
                            val fmt = muxed.optJSONObject(i) ?: continue
                            val h = fmt.optInt("height", 0)
                            if (h <= quality && h > bestHeight) {
                                bestHeight = h
                                bestMuxed = fmt
                            }
                        }
                        val url = bestMuxed?.optString("url").orEmpty()
                        if (url.isNotBlank()) {
                            return@withContext YouTubeResult(
                                success  = true,
                                url      = url,
                                filename = "${title}_${bestHeight}p.mp4",
                                mimeType = "video/mp4"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious instance $instance failed: ${e.message}")
                continue
            }
        }
        YouTubeResult(false, error = "All Invidious instances failed")
    }
}
