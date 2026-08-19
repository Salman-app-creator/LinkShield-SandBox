package com.linkshield.sandbox.api

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.linkshield.sandbox.dns.DnsManager

class CobaltApiService(context: Context, dnsManager: DnsManager) {
    private val client = dnsManager.getClient().newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object { const val API_BASE = "https://api.cobalt.tools" }

    data class MediaResult(
        val success: Boolean,
        val url: String? = null,
        val filename: String? = null,
        val mimeType: String? = null,
        val error: String? = null,
        val isDirectDownload: Boolean = false
    )

    suspend fun fetchMediaUrl(
        pageUrl: String,
        downloadMode: String = "auto",
        videoQuality: String = "720"
    ): MediaResult = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("url", pageUrl)
                put("downloadMode", downloadMode)
                put("videoQuality", videoQuality)
                put("audioFormat", "mp3")
                put("audioBitrate", "128")
                put("filenameStyle", "pretty")
                put("youtubeVideoCodec", "h264")
                put("alwaysProxy", true)
                put("youtubeVideoContainer", "mp4")
                put("youtubeBetterAudio", true)
            }
            val request = Request.Builder()
                .url(API_BASE)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LinkShieldSandbox/2.1")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext MediaResult(false, error = "Cobalt API error: HTTP ${response.code}")
                }
                val json = JSONObject(body)
                when (json.optString("status")) {
                    "redirect", "tunnel", "stream" -> {
                        val url = json.optString("url")
                        if (url.isBlank()) MediaResult(false, error = "Cobalt returned an empty media URL")
                        else {
                            val filename = json.optString("filename").ifBlank { "download" }
                            MediaResult(
                                true,
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
                        if (url.isBlank()) MediaResult(false, error = "Cobalt returned no selectable media")
                        else MediaResult(true, url = url, filename = "download.mp4", mimeType = "video/mp4")
                    }
                    "local-processing" -> {
                        val tunnels = json.optJSONArray("tunnel")
                        val url = tunnels?.optString(0).orEmpty()
                        val output = json.optJSONObject("output")
                        val filename = output?.optString("filename").orEmpty().ifBlank { "download" }
                        val mime = output?.optString("type").orEmpty().ifBlank { mimeFromFilename(filename, url) }
                        if (url.isBlank()) MediaResult(false, error = "Cobalt requires local media processing")
                        else MediaResult(true, url = url, filename = filename, mimeType = mime)
                    }
                    "error" -> {
                        val errorObject = json.optJSONObject("error")
                        MediaResult(false, error = errorObject?.optString("code")?.ifBlank { null } ?: "Cobalt rejected the URL")
                    }
                    else -> MediaResult(false, error = "Unexpected Cobalt response")
                }
            }
        } catch (e: IOException) {
            MediaResult(false, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            MediaResult(false, error = "Error: ${e.message}")
        }
    }

    fun startDownload(url: String, filename: String, mimeType: String = "video/mp4"): Long {
        val safeName = filename.ifBlank { "LinkShield_download" }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeName)
            .setDescription("Downloading via LinkShield")
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LinkShield/$safeName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        return downloadManager.enqueue(request)
    }

    fun isDirectFileLink(url: String): Boolean {
        val clean = url.substringBefore("?").substringBefore("#").lowercase()
        return listOf(".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".pdf", ".zip", ".jpg", ".png").any { clean.endsWith(it) }
    }

    private fun mimeFromFilename(filename: String, url: String): String {
        val value = (filename + " " + url).lowercase()
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
