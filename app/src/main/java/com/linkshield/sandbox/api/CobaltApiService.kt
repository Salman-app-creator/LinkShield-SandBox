package com.linkshield.sandbox.api

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.grabber.MediaQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * LinkShield's media extraction bridge.
 *
 * Cobalt performs the actual extraction on its backend (yt-dlp/stream
 * extractors/ffmpeg depending on the source). The Android app only presents
 * the returned media choices and downloads the selected result.
 */
class CobaltApiService(
    context: Context,
    dnsManager: DnsManager
) {
    private val client = dnsManager.getClient().newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object {
        // Keep this configurable in one place if a self-hosted Cobalt instance
        // is used later. The app talks to the JSON API only through this class.
        const val API_BASE = "https://api.cobalt.tools"

        private val VIDEO_QUALITIES = listOf("1080", "720", "480", "360")
    }

    data class MediaResult(
        val success: Boolean,
        val url: String? = null,
        val filename: String? = null,
        val mimeType: String? = null,
        val error: String? = null,
        val isDirectDownload: Boolean = false
    )

    /**
     * Fetch one concrete Cobalt result.
     */
    suspend fun fetchMediaUrl(
        pageUrl: String,
        downloadMode: String = "auto",
        videoQuality: String = "720"
    ): MediaResult = withContext(Dispatchers.IO) {
        requestCobalt(
            pageUrl = pageUrl,
            downloadMode = downloadMode,
            videoQuality = videoQuality
        )
    }

    /**
     * Fetch the useful choices for the Grabber UI.
     *
     * We deliberately ask Cobalt for several quality targets rather than
     * pretending that the source URL itself contains all qualities. Cobalt
     * decides which qualities are actually available and may return the same
     * stream for multiple requests; duplicates are removed here.
     */
    suspend fun fetchMediaOptions(
        pageUrl: String,
        audioOnly: Boolean = false
    ): List<MediaQualityOption> = withContext(Dispatchers.IO) {
        val cleanUrl = pageUrl.trim()
        if (cleanUrl.isBlank()) return@withContext emptyList()

        if (audioOnly) {
            val result = requestCobalt(cleanUrl, "audio", "720")
            return@withContext result.toOptions(audioOnly = true)
        }

        val results = mutableListOf<MediaQualityOption>()
        for (quality in VIDEO_QUALITIES) {
            val result = requestCobalt(cleanUrl, "auto", quality)
            if (result.success) {
                results += result.toOptions(audioOnly = false, requestedQuality = quality)
            }
        }

        // Some Cobalt versions/services can expose audio separately.
        val audio = requestCobalt(cleanUrl, "audio", "720")
        if (audio.success) {
            results += audio.toOptions(audioOnly = true)
        }

        return@withContext results
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
            .sortedWith(
                compareByDescending<MediaQualityOption> { it.isAudio.not() }
                    .thenByDescending { it.height ?: qualityNumber(it.quality) }
            )
    }

    private suspend fun requestCobalt(
        pageUrl: String,
        downloadMode: String,
        videoQuality: String
    ): MediaResult {
        return try {
            val jsonBody = JSONObject().apply {
                put("url", pageUrl)
                put("downloadMode", downloadMode)
                put("videoQuality", videoQuality)
                put("audioFormat", "mp3")
                put("audioBitrate", "128")
                put("filenameStyle", "pretty")
                put("youtubeVideoCodec", "h264")
                put("youtubeVideoContainer", "mp4")
                put("youtubeBetterAudio", true)
                put("alwaysProxy", true)
            }

            val request = Request.Builder()
                .url(API_BASE)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LinkShieldSandbox/2.1")
                .post(
                    jsonBody.toString()
                        .toRequestBody("application/json".toMediaTypeOrNull())
                )
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return MediaResult(
                        success = false,
                        error = "Cobalt API error: HTTP ${response.code}"
                    )
                }

                parseResponse(JSONObject(body))
            }
        } catch (e: IOException) {
            MediaResult(false, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            MediaResult(false, error = "Cobalt error: ${e.message}")
        }
    }

    private fun parseResponse(json: JSONObject): MediaResult {
        return when (json.optString("status")) {
            "redirect", "tunnel", "stream", "success" -> {
                val url = json.optString("url")
                if (url.isBlank()) {
                    MediaResult(false, error = "Cobalt returned an empty media URL")
                } else {
                    val filename = json.optString("filename")
                        .ifBlank { "LinkShield_download" }
                    MediaResult(
                        success = true,
                        url = url,
                        filename = filename,
                        mimeType = mimeFromFilename(filename, url),
                        isDirectDownload = json.optString("status") == "tunnel"
                    )
                }
            }

            "picker" -> {
                val picker = json.optJSONArray("picker")
                val first = picker?.optJSONObject(0)
                val url = first?.optString("url").orEmpty()
                if (url.isBlank()) {
                    MediaResult(false, error = "Cobalt returned no selectable media")
                } else {
                    MediaResult(
                        true,
                        url = url,
                        filename = "LinkShield_download.mp4",
                        mimeType = "video/mp4"
                    )
                }
            }

            "error" -> {
                val errorObject = json.optJSONObject("error")
                MediaResult(
                    false,
                    error = errorObject?.optString("code")?.ifBlank { null }
                        ?: json.optString("text").ifBlank { "Cobalt rejected the URL" }
                )
            }

            else -> MediaResult(
                false,
                error = json.optString("text").ifBlank { "Unsupported Cobalt response" }
            )
        }
    }

    private fun MediaResult.toOptions(
        audioOnly: Boolean,
        requestedQuality: String = ""
    ): List<MediaQualityOption> {
        val mediaUrl = url ?: return emptyList()
        val mime = mimeType ?: mimeFromFilename(filename.orEmpty(), mediaUrl)
        val actualQuality = if (audioOnly) "MP3" else qualityFromText(filename.orEmpty(), mediaUrl, requestedQuality)
        val label = if (audioOnly) "Audio Only • MP3" else "${actualQuality} • MP4"

        return listOf(
            MediaQualityOption(
                url = mediaUrl,
                quality = actualQuality,
                label = label,
                mimeType = mime,
                title = filename.orEmpty(),
                isAudio = audioOnly || mime.startsWith("audio/")
            )
        )
    }

    private fun qualityFromText(
        filename: String,
        url: String,
        requestedQuality: String
    ): String {
        val value = "$filename $url".lowercase()
        return listOf("2160", "1440", "1080", "720", "480", "360", "240", "144")
            .firstOrNull { value.contains(it) }
            ?.plus("p")
            ?: requestedQuality.takeIf { it.isNotBlank() }?.plus("p")
            ?: "Available"
    }

    private fun qualityNumber(value: String): Int =
        value.filter(Char::isDigit).toIntOrNull() ?: 0

    fun startDownload(
        url: String,
        filename: String,
        mimeType: String = "video/mp4"
    ): Long {
        val safeName = filename.ifBlank { "LinkShield_download.mp4" }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeName)
            .setDescription("Downloading via LinkShield")
            .setMimeType(mimeType)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "LinkShield/$safeName"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return downloadManager.enqueue(request)
    }

    fun isDirectFileLink(url: String): Boolean {
        val clean = url.substringBefore("?").substringBefore("#").lowercase()
        return listOf(
            ".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".mov", ".aac", ".ogg"
        ).any(clean::endsWith)
    }

    private fun mimeFromFilename(filename: String, url: String): String {
        val value = "$filename $url".lowercase()
        return when {
            ".mp3" in value -> "audio/mpeg"
            ".m4a" in value -> "audio/mp4"
            ".aac" in value -> "audio/aac"
            ".ogg" in value -> "audio/ogg"
            ".webm" in value -> "video/webm"
            ".m3u8" in value -> "application/vnd.apple.mpegurl"
            ".mpd" in value -> "application/dash+xml"
            ".mp4" in value -> "video/mp4"
            else -> "video/mp4"
        }
    }
}
