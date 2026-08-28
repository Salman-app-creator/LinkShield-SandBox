// app/src/main/java/com/linkshield/sandbox/api/CobaltApiService.kt
package com.linkshield.sandbox.api

import android.content.Context
import android.net.Uri
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MediaResult(
    val success: Boolean,
    val url: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val error: String? = null
)

class CobaltApiService(context: Context) {

    private val dnsManager = DnsManager(context)
    private val client: OkHttpClient by lazy {
        dnsManager.getClient().newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun cleanVideoUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val uri = Uri.parse(trimmed)
        val host = uri.host?.lowercase() ?: ""

        return when {
            host.contains("youtube.com") -> {
                val videoId = uri.getQueryParameter("v")
                if (!videoId.isNull_or_empty()) "https://www.youtube.com/watch?v=$videoId" else trimmed
            }
            host.contains("youtu.be") -> {
                val videoId = uri.lastPathSegment
                if (!videoId.isNull_or_empty()) "https://www.youtube.com/watch?v=$videoId" else trimmed
            }
            host.contains("instagram.com") || host.contains("tiktok.com") -> {
                val scheme = uri.scheme ?: "https"
                val path = uri.path ?: ""
                "$scheme://$host$path"
            }
            else -> trimmed
        }
    }

    suspend fun fetchMediaUrl(rawUrl: String, audioOnly: Boolean = false): MediaResult = withContext(Dispatchers.IO) {
        try {
            val cleanedUrl = cleanVideoUrl(rawUrl)
            val encodedUrl = URLEncoder.encode(cleanedUrl, "UTF-8")
            val endpoint = "http://141.148.223.177:8000/extract?url=$encodedUrl"

            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "LinkShieldSandbox/2.1")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful || bodyString.isEmpty()) {
                    return@withContext MediaResult(
                        success = false,
                        error = "Server error HTTP ${response.code}"
                    )
                }

                val json = JSONObject(bodyString)
                val status = json.optString("status")

                if (status == "success") {
                    val mediaUrl = json.optString("url")
                    val rawTitle = json.optString("title", "downloaded_media")
                    val safeTitle = rawTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val ext = if (audioOnly) "mp3" else "mp4"
                    val mime = if (audioOnly) "audio/mpeg" else "video/mp4"

                    MediaResult(
                        success = true,
                        url = mediaUrl,
                        filename = "$safeTitle.$ext",
                        mimeType = mime
                    )
                } else {
                    MediaResult(
                        success = false,
                        error = json.optString("detail", "Extraction failed")
                    )
                }
            }
        } catch (e: Exception) {
            MediaResult(
                success = false,
                error = e.localizedMessage ?: "Network request failed"
            )
        }
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.isEmpty()
}
