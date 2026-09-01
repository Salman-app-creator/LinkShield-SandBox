package com.linkshield.sandbox.api

import android.content.Context
import android.net.Uri
import com.linkshield.sandbox.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * LinkShield's self-hosted Cobalt client.
 *
 * IMPORTANT: This file deliberately does not touch any Compose/UI code.
 * The current Cobalt API uses POST / (not the legacy /api/json endpoint).
 */
data class MediaResult(
    val success: Boolean,
    val url: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val error: String? = null
)

class CobaltApiService(context: Context) {

    private val appContext = context.applicationContext

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun baseUrl(): String {
        return BuildConfig.COBALT_BASE_URL.trim().trimEnd('/') + "/"
    }

    private fun isYouTubeUrl(host: String): Boolean =
        host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host.endsWith(".youtu.be")

    fun cleanVideoUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase().orEmpty()
            when {
                host == "youtube.com" || host.endsWith(".youtube.com") -> {
                    val videoId = uri.getQueryParameter("v")
                    when {
                        !videoId.isNullOrBlank() -> "https://www.youtube.com/watch?v=$videoId"
                        uri.path?.startsWith("/shorts/") == true -> "https://www.youtube.com${uri.path}"
                        else -> trimmed
                    }
                }
                host == "youtu.be" || host.endsWith(".youtu.be") -> {
                    val videoId = uri.lastPathSegment
                    if (!videoId.isNullOrBlank()) "https://www.youtube.com/watch?v=$videoId" else trimmed
                }
                host == "instagram.com" || host.endsWith(".instagram.com") -> {
                    "https://www.instagram.com${uri.path.orEmpty()}"
                }
                host == "tiktok.com" || host.endsWith(".tiktok.com") -> {
                    "https://www.tiktok.com${uri.path.orEmpty()}"
                }
                host == "facebook.com" || host.endsWith(".facebook.com") ||
                    host == "fb.com" || host.endsWith(".fb.com") || host == "fb.watch" -> {
                    "https://www.facebook.com${uri.path.orEmpty()}"
                }
                else -> trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    suspend fun fetchMediaUrl(
        rawUrl: String,
        audioOnly: Boolean = false
    ): MediaResult = withContext(Dispatchers.IO) {
        try {
            val cleanedUrl = cleanVideoUrl(rawUrl)
            val host = Uri.parse(cleanedUrl).host?.lowercase().orEmpty()

            if (cleanedUrl.isBlank() || !cleanedUrl.startsWith("http", ignoreCase = true)) {
                return@withContext MediaResult(false, error = "Invalid media URL")
            }

            // Current Cobalt API endpoint is POST /.
            val apiUrl = baseUrl()

            val bodyJson = JSONObject().apply {
                put("url", cleanedUrl)
                put("downloadMode", if (audioOnly) "audio" else "auto")
                put("videoQuality", "1080")
                put("filenameStyle", "pretty")
                put("audioFormat", "mp3")
                put("audioBitrate", "128")
                // Prefer a server-generated downloadable result. This avoids
                // requiring local remuxing for the normal path.
                put("localProcessing", "disabled")
                if (isYouTubeUrl(host)) {
                    put("youtubeVideoCodec", "h264")
                    put("youtubeVideoContainer", "mp4")
                }
            }.toString()

            val builder = Request.Builder()
                .url(apiUrl)
                .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "LinkShieldSandbox/2.3")

            // Private Cobalt instances can require authentication. We do not
            // hard-code a secret; the value is intentionally empty by default.
            if (BuildConfig.COBALT_API_KEY.isNotBlank()) {
                builder.header("Authorization", "Api-Key ${BuildConfig.COBALT_API_KEY}")
            }

            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext MediaResult(
                        success = false,
                        error = when (response.code) {
                            401, 403 -> "Cobalt authentication/access denied (HTTP ${response.code})."
                            429 -> "Cobalt rate limit reached. Please try again shortly."
                            else -> "Cobalt server returned HTTP ${response.code}."
                        }
                    )
                }
                if (body.isBlank()) {
                    return@withContext MediaResult(false, error = "Cobalt returned an empty response.")
                }

                val json = JSONObject(body)
                when (json.optString("status")) {
                    "tunnel", "redirect" -> {
                        val mediaUrl = json.optString("url")
                        if (mediaUrl.isBlank()) {
                            MediaResult(false, error = "Cobalt returned no media URL.")
                        } else {
                            val fallbackExt = if (audioOnly) "mp3" else "mp4"
                            val filename = json.optString("filename").ifBlank {
                                "LinkShield_${System.currentTimeMillis()}.$fallbackExt"
                            }
                            MediaResult(
                                success = true,
                                url = mediaUrl,
                                filename = filename,
                                mimeType = if (audioOnly) "audio/mpeg" else guessMime(filename)
                            )
                        }
                    }

                    "picker" -> {
                        val picker = json.optJSONArray("picker")
                        if (picker == null || picker.length() == 0) {
                            MediaResult(false, error = "Cobalt returned an empty media picker.")
                        } else {
                            // Preserve the current UI's single-result behaviour;
                            // choose the first video item, otherwise the first item.
                            var chosen = picker.optJSONObject(0)
                            for (i in 0 until picker.length()) {
                                val item = picker.optJSONObject(i)
                                if (item?.optString("type") == "video") {
                                    chosen = item
                                    break
                                }
                            }
                            val mediaUrl = chosen?.optString("url").orEmpty()
                            if (mediaUrl.isBlank()) {
                                MediaResult(false, error = "Cobalt picker item contained no media URL.")
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

                    "local-processing" -> {
                        MediaResult(
                            success = false,
                            error = "This Cobalt request requires local media processing. The server should be configured to return a tunnel/redirect result."
                        )
                    }

                    "error" -> {
                        val errorObject = json.optJSONObject("error")
                        val code = errorObject?.optString("code").orEmpty()
                        val context = errorObject?.optString("context").orEmpty()
                        val detail = listOf(code, context).filter { it.isNotBlank() }.joinToString(" ")
                        MediaResult(false, error = "Cobalt error${if (detail.isNotBlank()) " [$detail]" else ""}.")
                    }

                    else -> MediaResult(false, error = "Unexpected Cobalt status: '${json.optString("status", "unknown")}'.")
                }
            }
        } catch (_: SocketTimeoutException) {
            MediaResult(false, error = "Cobalt request timed out. Check that the self-hosted instance is running.")
        } catch (e: Exception) {
            MediaResult(false, error = e.localizedMessage ?: "Cobalt network request failed.")
        }
    }

    private fun guessMime(filename: String): String {
        return when (filename.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "webm" -> "video/webm"
            "gif" -> "image/gif"
            else -> "video/mp4"
        }
    }
}
