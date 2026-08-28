package com.linkshield.sandbox.api

// REPO PATH: app/src/main/java/com/linkshield/sandbox/api/CobaltApiService.kt

import android.content.Context
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CobaltApiService(context: Context, dnsManager: DnsManager) {

    private val client = dnsManager.getClient().newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    companion object {
        // Cobalt API — Oracle server port 9001
        private const val COBALT_API = "http://141.148.223.177:9001/"
        private val JSON_TYPE = "application/json".toMediaType()
    }

    data class MediaResult(
        val success: Boolean,
        val url: String? = null,
        val filename: String? = null,
        val mimeType: String? = null,
        val error: String? = null
    )

    // YouTube ?si= aur baaki tracking params strip karo
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
        videoQuality: String = "1080"
    ): MediaResult = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = cleanVideoUrl(pageUrl)

            // Cobalt API POST body
            val bodyJson = JSONObject().apply {
                put("url", cleanUrl)
                put("downloadMode", downloadMode)   // "auto", "audio", "mute"
                put("videoQuality", videoQuality)   // "144","360","720","1080","2160"
                put("filenameStyle", "pretty")
            }.toString()

            val requestBody = bodyJson.toRequestBody(JSON_TYPE)

            val request = Request.Builder()
                .url(COBALT_API)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "LinkShieldSandbox/2.2")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()

                if (!response.isSuccessful || body.isNullOrBlank()) {
                    return@withContext MediaResult(
                        false,
                        error = "Server Error: HTTP ${response.code}"
                    )
                }

                val json = JSONObject(body)
                val status = json.optString("status")

                when (status) {
                    "stream", "redirect", "tunnel" -> {
                        val mediaUrl = json.optString("url")
                        val filename = json.optString("filename").ifBlank {
                            val ext = if (downloadMode == "audio") "mp3" else "mp4"
                            "LinkShield_${System.currentTimeMillis()}.$ext"
                        }
                        val mime = if (downloadMode == "audio") "audio/mpeg" else "video/mp4"

                        if (mediaUrl.isBlank()) {
                            MediaResult(false, error = "No download URL received")
                        } else {
                            MediaResult(
                                success  = true,
                                url      = mediaUrl,
                                filename = filename,
                                mimeType = mime
                            )
                        }
                    }
                    "picker" -> {
                        // Multiple streams — pehla le lo
                        val picker = json.optJSONArray("picker")
                        val first = picker?.optJSONObject(0)
                        val mediaUrl = first?.optString("url") ?: ""
                        val filename = "LinkShield_${System.currentTimeMillis()}.mp4"
                        if (mediaUrl.isBlank()) {
                            MediaResult(false, error = "No stream found")
                        } else {
                            MediaResult(success = true, url = mediaUrl, filename = filename, mimeType = "video/mp4")
                        }
                    }
                    "error" -> {
                        val errorCode = json.optJSONObject("error")?.optString("code") ?: "unknown"
                        MediaResult(false, error = "Cobalt error: $errorCode")
                    }
                    else -> {
                        MediaResult(false, error = "Unexpected response: $status")
                    }
                }
            }
        } catch (e: IOException) {
            MediaResult(false, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            MediaResult(false, error = "Error: ${e.message}")
        }
    }
}
