package com.linkshield.sandbox.ui.grabber

import android.content.Context
import com.linkshield.sandbox.api.CobaltApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MediaExtractorRepository(private val context: Context? = null) {

    suspend fun extract(
        mediaUrl: String,
        title: String = "",
        audioOnly: Boolean = false,
        resolution: String = "1080p"
    ): List<MediaQualityOption> = withContext(Dispatchers.IO) {
        if (mediaUrl.isBlank() || mediaUrl.startsWith("blob:", ignoreCase = true)) {
            return@withContext emptyList()
        }

        val ctx = context?.applicationContext
        if (ctx != null) {
            try {
                val cobalt = CobaltApiService(ctx)
                val result = cobalt.fetchMediaUrl(rawUrl = mediaUrl, audioOnly = audioOnly, resolution = resolution)
                if (result.success && !result.url.isNullOrBlank()) {
                    val extractedUrl = result.url
                    val mime = result.mimeType ?: detectMimeType(extractedUrl)
                    val detectedQuality = detectQuality(extractedUrl)
                    return@withContext listOf(
                        MediaQualityOption(
                            url = extractedUrl,
                            quality = detectedQuality.ifBlank { resolution },
                            label = detectedQuality.ifBlank { resolution },
                            mimeType = mime.ifBlank { if (audioOnly) "audio/mpeg" else "video/mp4" },
                            title = title.ifBlank { result.filename ?: "Media File" },
                            isAudio = audioOnly || mime.startsWith("audio/")
                        )
                    )
                }
            } catch (_: Exception) {
                // Fall through to direct URL detection below.
            }
        }

        // Direct-media fallback remains useful for already-resolved links.
        val mimeType = detectMimeType(mediaUrl)
        val quality = detectQuality(mediaUrl)
        return@withContext if (mimeType.isNotBlank()) {
            listOf(
                MediaQualityOption(
                    url = mediaUrl,
                    quality = quality.ifBlank { resolution },
                    label = quality.ifBlank { "High Quality" },
                    mimeType = mimeType,
                    title = title,
                    isAudio = mimeType.startsWith("audio/")
                )
            )
        } else {
            emptyList()
        }
    }

    private fun detectMimeType(mediaUrl: String): String {
        val clean = mediaUrl.substringBefore("?").substringBefore("#").lowercase()
        return when {
            clean.endsWith(".mp3") -> "audio/mpeg"
            clean.endsWith(".m4a") -> "audio/mp4"
            clean.endsWith(".aac") -> "audio/aac"
            clean.endsWith(".ogg") -> "audio/ogg"
            clean.endsWith(".wav") -> "audio/wav"
            clean.endsWith(".webm") -> "video/webm"
            clean.endsWith(".mp4") -> "video/mp4"
            clean.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            clean.endsWith(".mpd") -> "application/dash+xml"
            else -> ""
        }
    }

    private fun detectQuality(mediaUrl: String): String {
        val url = mediaUrl.lowercase()
        return when {
            "4320" in url || "4k" in url -> "4K"
            "2160" in url -> "4K"
            "1440" in url -> "1440p"
            "1080" in url -> "1080p"
            "720" in url -> "720p"
            "480" in url -> "480p"
            "360" in url -> "360p"
            else -> ""
        }
    }

    suspend fun checkUrl(mediaUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(mediaUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            connection.disconnect()
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }
}
