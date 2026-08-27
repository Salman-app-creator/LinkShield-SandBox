package com.linkshield.sandbox.api

// REPO PATH: app/src/main/java/com/linkshield/sandbox/api/CobaltApiService.kt

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CobaltApiService(context: Context, dnsManager: DnsManager) {
    private val client = dnsManager.getClient().newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object {
        private const val ORACLE_API_BASE = "http://141.148.223.177:8000/extract"
    }

    data class MediaResult(
        val success: Boolean,
        val url: String? = null,
        val filename: String? = null,
        val mimeType: String? = null,
        val error: String? = null,
        val isDirectDownload: Boolean = false
    )

    // YouTube/Instagram/TikTok tracking params strip karo
    private fun cleanVideoUrl(rawUrl: String): String {
        return try {
            val uri = android.net.Uri.parse(rawUrl.trim())
            val host = uri.host?.lowercase() ?: return rawUrl.trim()
            when {
                host.contains("youtube.com") -> {
                    val videoId = uri.getQueryParameter("v")
                    if (!videoId.isNullOrBlank()) "https://www.youtube.com/watch?v=$videoId"
                    else rawUrl.trim()
                }
                host.contains("youtu.be") -> {
                    val videoId = uri.path?.trimStart('/') ?: return rawUrl.trim()
                    "https://www.youtube.com/watch?v=$videoId"
                }
                host.contains("instagram.com") -> "${uri.scheme}://${uri.host}${uri.path}"
                host.contains("tiktok.com")    -> "${uri.scheme}://${uri.host}${uri.path}"
                else -> rawUrl.trim()
            }
        } catch (e: Exception) {
            rawUrl.trim()
        }
    }

    suspend fun fetchMediaUrl(
        pageUrl: String,
        downloadMode: String = "auto",
        videoQuality: String = "720"
    ): MediaResult = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanVideoUrl(pageUrl)
            val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
            val targetUrl = "$ORACLE_API_BASE?url=$encodedUrl"

            val request = Request.Builder()
                .url(targetUrl)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "LinkShieldSandbox/2.1")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext MediaResult(false, error = "Server Error: HTTP ${response.code}")
                }
                val json = JSONObject(body)
                val status = json.optString("status")
                if (status == "success") {
                    val mediaUrl = json.optString("url")
                    val title = json.optString("title").ifBlank { "LinkShield_Media" }
                    val ext = if (downloadMode == "audio") "mp3" else "mp4"
                    val safeTitle = title.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
                    val filename = "$safeTitle.$ext"
                    if (mediaUrl.isBlank()) {
                        MediaResult(false, error = "Server returned empty URL")
                    } else {
                        MediaResult(success = true, url = mediaUrl, filename = filename,
                            mimeType = if (downloadMode == "audio") "audio/mpeg" else "video/mp4",
                            isDirectDownload = true)
                    }
                } else {
                    val detail = json.optString("detail").ifBlank { "Media extraction failed" }
                    MediaResult(false, error = detail)
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
        return listOf(".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".pdf", ".zip", ".jpg", ".png")
            .any { clean.endsWith(it) }
    }
}
