package com.linkshield.sandbox.api

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class MediaResult(
    val success: Boolean,
    val url: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val error: String? = null
)

class CobaltApiService(context: Context) {

    // FIX: Clean client WITHOUT DnsManager's SNI fragmentation / DoH
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false) // fail fast
            .build()
    }

    private fun isYouTubeUrl(host: String) =
        host.contains("youtube.com") || host.contains("youtu.be")

    fun cleanVideoUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase() ?: ""
            when {
                host.contains("youtube.com") -> {
                    val videoId = uri.getQueryParameter("v")
                    if (!videoId.isNullOrEmpty()) "https://www.youtube.com/watch?v=$videoId"
                    else trimmed
                }
                host.contains("youtu.be") -> {
                    val videoId = uri.lastPathSegment
                    if (!videoId.isNullOrEmpty()) "https://www.youtube.com/watch?v=$videoId"
                    else trimmed
                }
                host.contains("instagram.com") -> {
                    val path = uri.path ?: ""
                    "https://www.instagram.com$path"
                }
                host.contains("tiktok.com") -> {
                    val path = uri.path ?: ""
                    "https://www.tiktok.com$path"
                }
                host.contains("facebook.com") || host.contains("fb.com") || host.contains("fb.watch") -> {
                    val path = uri.path ?: ""
                    "https://www.facebook.com$path"
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
                val host = Uri.parse(cleanedUrl).host?.lowercase() ?: ""

                // FIX: Added /api/json path — Cobalt API standard endpoint
                val apiUrl = if (isYouTubeUrl(host)) {
                    "http://141.148.223.177:9002/api/json"
                } else {
                    "http://141.148.223.177:9001/api/json"
                }

                val bodyJson = JSONObject().apply {
                    put("url", cleanedUrl)
                    put("downloadMode", if (audioOnly) "audio" else "auto")
                    put("videoQuality", "1080")
                    put("filenameStyle", "pretty")
                    if (audioOnly) put("aFormat", "mp3")
                }.toString()

                val request = Request.Builder()
                    .url(apiUrl)
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "LinkShieldSandbox/2.3")
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        return@withContext MediaResult(
                            success = false,
                            error = "Server error HTTP ${response.code}. Cobalt instance may be down or API path changed."
                        )
                    }
                    if (body.isBlank()) {
                        return@withContext MediaResult(
                            success = false,
                            error = "Empty response from Cobalt server."
                        )
                    }

                    val json = JSONObject(body)
                    val status = json.optString("status", "unknown")

                    when (status) {
                        "stream", "redirect", "tunnel" -> {
                            val mediaUrl = json.optString("url")
                            val filename = json.optString("filename").ifBlank {
                                "LinkShield_${System.currentTimeMillis()}.${if (audioOnly) "mp3" else "mp4"}"
                            }
                            val mime = if (audioOnly) "audio/mpeg" else "video/mp4"
                            if (mediaUrl.isBlank()) {
                                MediaResult(success = false, error = "Server returned empty media URL")
                            } else {
                                MediaResult(
                                    success = true,
                                    url = mediaUrl,
                                    filename = filename,
                                    mimeType = mime
                                )
                            }
                        }
                        "picker" -> {
                            val arr = json.optJSONArray("picker")
                            if (arr == null || arr.length() == 0) {
                                MediaResult(success = false, error = "Picker response contained no streams")
                            } else {
                                val first = arr.optJSONObject(0)
                                val mediaUrl = first?.optString("url") ?: ""
                                if (mediaUrl.isBlank()) {
                                    MediaResult(success = false, error = "Picker stream URL was empty")
                                } else {
                                    MediaResult(
                                        success = true,
                                        url = mediaUrl,
                                        filename = "LinkShield_${System.currentTimeMillis()}.mp4",
                                        mimeType = "video/mp4"
                                    )
                                }
                            }
                        }
                        "error" -> {
                            val errCode = json.optJSONObject("error")?.optString("code") ?: "unknown"
                            val errCtx  = json.optJSONObject("error")?.optString("context") ?: ""
                            MediaResult(
                                success = false,
                                error = "Cobalt API error [$errCode] $errCtx"
                            )
                        }
                        else -> MediaResult(
                            success = false,
                            error = "Unexpected Cobalt status: '$status'"
                        )
                    }
                }
            } catch (e: SocketTimeoutException) {
                MediaResult(
                    success = false,
                    error = "Connection timed out. Cobalt server unreachable or blocked."
                )
            } catch (e: Exception) {
                MediaResult(
                    success = false,
                    error = e.localizedMessage ?: "Network request failed"
                )
            }
        }
}
