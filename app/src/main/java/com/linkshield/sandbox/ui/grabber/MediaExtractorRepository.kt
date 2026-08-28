package com.linkshield.sandbox.ui.grabber

import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MediaExtractorRepository {

    suspend fun extract(
        mediaUrl: String,
        title: String = ""
    ): List<MediaQualityOption> = withContext(Dispatchers.IO) {

        if (mediaUrl.isBlank() || mediaUrl.startsWith("blob:", ignoreCase = true)) {
            return@withContext emptyList()
        }

        // 1. Direct Cobalt API Execution
        try {
            val appClass = Class.forName("android.app.ActivityThread")
            val currentApp = appClass.getMethod("currentApplication").invoke(null) as android.content.Context
            val cobaltApi = CobaltApiService(currentApp)

            // Fix: Changed parameter name from pageUrl to rawUrl
            val result = cobaltApi.fetchMediaUrl(rawUrl = mediaUrl)
            
            if (result.success && !result.url.isNullOrBlank()) {
                val extractedUrl = result.url
                val mimeType = result.mimeType ?: detectMimeType(extractedUrl)
                val quality = detectQuality(extractedUrl)

                return@withContext listOf(
                    MediaQualityOption(
                        url = extractedUrl,
                        quality = if (quality.isNotBlank()) quality else "1080p",
                        label = if (quality.isNotBlank()) quality else "High Quality",
                        mimeType = mimeType,
                        title = if (title.isNotBlank()) title else (result.filename ?: "Media File"),
                        isAudio = mimeType.startsWith("audio/")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Local Direct Extension Check (Only for raw .mp4 / .mp3 links)
        val mimeType = detectMimeType(mediaUrl)
        val quality = detectQuality(mediaUrl)

        if (mimeType.isNotBlank()) {
            listOf(
                MediaQualityOption(
                    url = mediaUrl,
                    quality = quality,
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
