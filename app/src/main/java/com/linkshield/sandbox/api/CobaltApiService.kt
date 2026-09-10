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
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun cobaltUrl(): String =
        BuildConfig.COBALT_BASE_URL.trim().trimEnd('/') + "/"

    private fun ytApiUrl(): String = "http://141.148.223.177:9002/"

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
                        uri.path?.startsWith("/shorts/") == true ->
                            "https://www.youtube.com${uri.path}"
                        else -> trimmed
                    }
                }
                host == "youtu.be" || host.endsWith(".youtu.be") -> {
                    val videoId = uri.lastPathSegment
                    if (!videoId.isNullOrBlank())
                        "https://www.youtube.com/watch?v=$videoId"
                    else trimmed
                }
                host == "instagram.com" || host.endsWith(".instagram.com") ->
                    "https://www.instagram.com${uri.path.orEmpty()}"
                host == "tiktok.com" || host.endsWith(".tiktok.com") ->
                    "https://www.tiktok.com${uri.path.orEmpty()}"
                host == "facebook.com" || host.endsWith(".facebook.com") ||
                    host == "fb.com" || host.endsWith(".fb.com") ||
                    host == "fb.watch" ->
                    "https://www.facebook.com${uri.path.orEmpty()}"
                else -> trimmed
            }
        } catch (_: Exception) { trimmed }
    }

    // ── YouTube → yt-dlp server (port 9002) ─────────────────────────────────
    private suspend fun fetchYouTubeViaServer(
        cleanedUrl: String,
        audioOnly: Boolean,
        resolution: String
    ): MediaResult = withContext(Dispatchers.IO) {
        try {
            val quality = when (resolution.lowercase()) {
                "4k", "2160p" -> "2160"
                "1440p"       -> "1440"
                "1080p"       -> "1080"
                "720p"        -> "720"
                "480p"        -> "480"
                "360p"        -> "360"
                else          -> "720"
            }

            val bodyJson = JSONObject().apply {
                put("url", cleanedUrl)
                put("downloadMode", if (audioOnly) "audio" else "video")
                put("quality", quality)
            }.toString()

            val request = Request.Builder()
                .url(ytApiUrl())
                .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Content-Type", "application/json")
                .header("User-Agent", "LinkShieldSandbox/2.3")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    return@withContext MediaResult(
                        false,
                        error = "Yeh video abhi available nahi hai. Doosri video try karein."
                    )
                }

                val json = JSONObject(body)
                when (json.optString("status")) {
                    "redirect" -> {
                        val url = json.optString("url")
                        val filename = json.optString("filename").ifBlank {
                            "YouTube_${System.currentTimeMillis()}.${if (audioOnly) "mp3" else "mp4"}"
                        }
                        if (url.isBlank()) {
                            MediaResult(false, error = "Video ka download link nahi mila. Doosri video try karein.")
                        } else {
                            MediaResult(
                                success  = true,
                                url      = url,
                                filename = filename,
                                mimeType = if (audioOnly) "audio/mpeg" else "video/mp4"
                            )
                        }
                    }
                    "error" -> {
                        MediaResult(false, error = "Video extract nahi ho payi. Network check karein ya doosri video try karein.")
                    }
                    else -> MediaResult(false, error = "Server ne unexpected response diya. Thodi der baad try karein.")
                }
            }
        } catch (_: SocketTimeoutException) {
            MediaResult(false, error = "Server slow hai. Thodi der baad try karein.")
        } catch (e: Exception) {
            MediaResult(false, error = "Internet connection check karein.")
        }
    }

    // ── All other platforms → Cobalt (port 9001) ─────────────────────────────
    suspend fun fetchMediaUrl(
        rawUrl: String,
        audioOnly: Boolean = false,
        resolution: String = "1080p"
    ): MediaResult = withContext(Dispatchers.IO) {
        try {
            val cleanedUrl = cleanVideoUrl(rawUrl)
            val host = Uri.parse(cleanedUrl).host?.lowercase().orEmpty()

            if (cleanedUrl.isBlank() || !cleanedUrl.startsWith("http", ignoreCase = true)) {
                return@withContext MediaResult(false, error = "Yeh link valid nahi hai. Dobara check karein.")
            }

            // YouTube → yt-dlp server
            if (isYouTubeUrl(host)) {
                return@withContext fetchYouTubeViaServer(cleanedUrl, audioOnly, resolution)
            }

            // All other platforms → Cobalt
            val bodyJson = JSONObject().apply {
                put("url", cleanedUrl)
                put("downloadMode", if (audioOnly) "audio" else "auto")
                put("videoQuality", when (resolution.lowercase()) {
                    "4k", "2160p" -> "2160"
                    "1440p"       -> "1440"
                    "1080p"       -> "1080"
                    "720p"        -> "720"
                    "480p"        -> "480"
                    "360p"        -> "360"
                    else          -> "1080"
                })
                put("filenameStyle", "pretty")
                put("audioFormat", "mp3")
                put("audioBitrate", "128")
                put("localProcessing", "disabled")
            }.toString()

            val builder = Request.Builder()
                .url(cobaltUrl())
                .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "LinkShieldSandbox/2.3")

            if (BuildConfig.COBALT_API_KEY.isNotBlank()) {
                builder.header("Authorization", "Api-Key ${BuildConfig.COBALT_API_KEY}")
            }

            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext MediaResult(
                        success = false,
                        error = when (response.code) {
                            401, 403 -> "Yeh content accessible nahi hai. Doosra link try karein."
                            429      -> "Bohat zyada requests. Thodi der baad try karein."
                            else     -> "Server abhi busy hai. Thodi der baad try karein."
                        }
                    )
                }
                if (body.isBlank()) {
                    return@withContext MediaResult(
                        false, error = "Server se koi response nahi aaya. Dobara try karein."
                    )
                }

                val json = JSONObject(body)
                when (json.optString("status")) {
                    "tunnel", "redirect" -> {
                        val mediaUrl = normalizeCobaltMediaUrl(json.optString("url"), cobaltUrl())
                        if (mediaUrl.isBlank()) {
                            MediaResult(false, error = "Media ka link nahi mila. Doosra link try karein.")
                        } else {
                            val fallbackExt = if (audioOnly) "mp3" else "mp4"
                            val filename = json.optString("filename").ifBlank {
                                "LinkShield_${System.currentTimeMillis()}.$fallbackExt"
                            }
                            MediaResult(
                                success  = true,
                                url      = mediaUrl,
                                filename = filename,
                                mimeType = if (audioOnly) "audio/mpeg" else guessMime(filename)
                            )
                        }
                    }
                    "picker" -> {
                        val picker = json.optJSONArray("picker")
                        if (picker == null || picker.length() == 0) {
                            MediaResult(false, error = "Is link par koi media nahi mili.")
                        } else {
                            var chosen = picker.optJSONObject(0)
                            for (i in 0 until picker.length()) {
                                val item = picker.optJSONObject(i)
                                if (item?.optString("type") == "video") {
                                    chosen = item; break
                                }
                            }
                            val mediaUrl = normalizeCobaltMediaUrl(
                                chosen?.optString("url").orEmpty(), cobaltUrl()
                            )
                            if (mediaUrl.isBlank()) {
                                MediaResult(false, error = "Media ka link nahi mila. Doosra link try karein.")
                            } else {
                                MediaResult(
                                    success  = true,
                                    url      = mediaUrl,
                                    filename = "LinkShield_${System.currentTimeMillis()}.mp4",
                                    mimeType = "video/mp4"
                                )
                            }
                        }
                    }
                    "local-processing" -> MediaResult(
                        success = false,
                        error   = "Yeh link abhi supported nahi hai. Doosra link try karein."
                    )
                    "error" -> {
                        MediaResult(
                            false,
                            error = "Yeh media download nahi ho payi. Doosra link try karein."
                        )
                    }
                    else -> MediaResult(
                        false,
                        error = "Server ne unexpected response diya. Thodi der baad try karein."
                    )
                }
            }
        } catch (_: SocketTimeoutException) {
            MediaResult(false, error = "Server slow hai. Thodi der baad try karein.")
        } catch (e: Exception) {
            MediaResult(false, error = "Internet connection check karein.")
        }
    }

    private fun normalizeCobaltMediaUrl(raw: String, apiUrl: String): String {
        if (raw.isBlank()) return raw
        return try {
            val media = Uri.parse(raw)
            val api   = Uri.parse(apiUrl)
            if (media.host.equals(api.host, ignoreCase = true) &&
                media.port == 9000 && api.port == 9001
            ) {
                raw.replaceFirst(
                    Regex("^([a-zA-Z][a-zA-Z0-9+.-]*://[^/:]+):9000(?=/|$)"), "$1:9001"
                )
            } else raw
        } catch (_: Exception) { raw }
    }

    private fun guessMime(filename: String): String {
        return when (filename.substringAfterLast('.', "").lowercase()) {
            "mp3"  -> "audio/mpeg"
            "m4a"  -> "audio/mp4"
            "ogg"  -> "audio/ogg"
            "wav"  -> "audio/wav"
            "webm" -> "video/webm"
            "gif"  -> "image/gif"
            else   -> "video/mp4"
        }
    }
}
