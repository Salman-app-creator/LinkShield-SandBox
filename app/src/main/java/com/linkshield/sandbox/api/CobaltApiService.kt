// app/src/main/java/com/linkshield/sandbox/api/CobaltApiService.kt
package com.linkshield.sandbox.api

import android.content.Context
import android.net.Uri
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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
        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase() ?: ""
            when {
                host.contains("youtube.com") -> {
                    val videoId = uri.getQueryParameter("v")
                    if (!videoId.isNullOrEmpty()) "https://www.youtube.com/watch?v=$videoId" else trimmed
                }
                host.contains("youtu.be") -> {
                    val videoId = uri.lastPathSegment
                    if (!videoId.isNullOrEmpty()) "https://www.youtube.com/watch?v=$videoId" else trimmed
                }
                host.contains("instagram.com") || host.contains("tiktok.com") -> {
                    val scheme = uri.scheme ?: "https"
                    val path = uri.path ?: ""
                    "$scheme://$host$path"
                }
                else -> trimmed
            }
        } catch (e: Exception) {
            trimmed
        }
    }

    suspend fun fetchMediaUrl(rawUrl: String, audioOnly: Boolean = false): MediaResult =
        withContext(Dispatchers.IO) {
            try {
                val cleanedUrl = cleanVideoUrl(rawUrl)

                val bodyJson = JSONObject().apply {
                    put("url", cleanedUrl)
                    put("downloadMode", if (audioOnly) "audio" else "auto")
                    put("videoQuality", "1080")
                    put("filenameStyle", "pretty")
                }.toString()

                val request = Request.Builder()
                    .url("http://141.148.223.177:9001/")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "LinkShieldSandbox/2.3")
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful || body.isEmpty()) {
                        return@withContext MediaResult(false, error = "Server error HTTP ${response.code}")
                    }

                    val json = JSONObject(body)
                    val status = json.optString("status")

                    when (status) {
                        "stream", "redirect", "tunnel" -> {
                            val mediaUrl = json.optString("url")
                            val filename = json.optString("filename").ifBlank {
                                "LinkShield_${System.currentTimeMillis()}.${if (audioOnly) "mp3" else "mp4"}"
                            }
                            val mime = if (audioOnly) "audio/mpeg" else "video/mp4"
                            MediaResult(success = true, url = mediaUrl, filename = filename, mimeType = mime)
                        }
                        "picker" -> {
                            val first = json.optJSONArray("picker")?.optJSONObject(0)
                            val mediaUrl = first?.optString("url") ?: ""
                            if (mediaUrl.isBlank()) MediaResult(false, error = "No stream found")
                            else MediaResult(success = true, url = mediaUrl,
                                filename = "LinkShield_${System.currentTimeMillis()}.mp4", mimeType = "video/mp4")
                        }
                        "error" -> {
                            val code = json.optJSONObject("error")?.optString("code") ?: "unknown"
                            MediaResult(false, error = "Error: $code")
                        }
                        else -> MediaResult(false, error = "Unexpected response: $status")
                    }
                }
            } catch (e: Exception) {
                MediaResult(false, error = e.localizedMessage ?: "Network request failed")
            }
        }
}
