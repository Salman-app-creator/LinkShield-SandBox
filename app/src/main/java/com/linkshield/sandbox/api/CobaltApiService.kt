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

    // Invidious instances for YouTube (phone calls these directly, not Oracle)
    private val invidiousInstances = listOf(
        "https://invidious.privacyredirect.com",
        "https://iv.datura.network",
        "https://invidious.nerdvpn.de"
    )

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

    private fun extractYouTubeId(cleanedUrl: String): String? {
        return try {
            val uri = Uri.parse(cleanedUrl)
            uri.getQueryParameter("v")
                ?: uri.pathSegments.lastOrNull()?.takeIf { it.length == 11 }
        } catch (_: Exception) { null }
    }

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
                host == "instagram.com" || host.endsWith(".instagram.com") ->
                    "https://www.instagram.com${uri.path.orEmpty()}"
                host == "tiktok.com" || host.endsWith(".tiktok.com") ->
                    "https://www.tiktok.com${uri.path.orEmpty()}"
                host == "facebook.com" || host.endsWith(".facebook.com") ||
                    host == "fb.com" || host.endsWith(".fb.com") || host == "fb.watch" ->
                    "https://www.facebook.com${uri.path.orEmpty()}"
                else -> trimmed
            }
        } catch (_: Exception) { trimmed }
    }

    // ── YouTube via Invidious (runs on phone, not Oracle) ───────────────────
    private suspend fun fetchYouTubeViaInvidious(
        videoId: String,
        audioOnly: Boolean,
        resolution: String
    ): MediaResult = withContext(Dispatchers.IO) {
        val targetHeight = when (resolution.lowercase()) {
            "4k", "2160p" -> 2160
            "1440p" -> 1440
            "1080p" -> 1080
            "720p"  -> 720
            "480p"  -> 480
            "360p"  -> 360
            else    -> 1080
        }

        for (instance in invidiousInstances) {
            try {
                val apiUrl = "$instance/api/v1/videos/$videoId"
                val request = Request.Builder()
                    .url(apiUrl)
                    .get()
                    .header("User-Agent", "LinkShieldSandbox/2.3")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use

                    val json = JSONObject(body)
                    val title = json.optString("title", "video")

                    if (audioOnly) {
                        // Pick best audio stream
                        val audioFormats = json.optJSONArray("adaptiveFormats") ?: return@use
                        var bestAudio: JSONObject? = null
                        var bestBitrate = 0
                        for (i in 0 until audioFormats.length()) {
                            val fmt = audioFormats.optJSONObject(i) ?: continue
                            val mime = fmt.optString("type", "")
                            if (!mime.startsWith("audio/")) continue
                            val bitrate = fmt.optInt("bitrate", 0)
                            if (bitrate > bestBitrate) {
                                bestBitrate = bitrate
                                bestAudio = fmt
                            }
                        }
                        val audioUrl = bestAudio?.optString("url") ?: return@use
                        if (audioUrl.isBlank()) return@use
                        return@withContext MediaResult(
                            success = true,
                            url = audioUrl,
                            filename = "${title}.mp3",
                            mimeType = "audio/mpeg"
                        )
                    } else {
                        // Pick best video stream at or below target height
                        val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                        val formatStreams = json.optJSONArray("formatStreams")

                        // Try adaptive (video-only) streams first
                        if (adaptiveFormats != null) {
                            var bestVideo: JSONObject? = null
                            var bestHeight = 0
                            for (i in 0 until adaptiveFormats.length()) {
                                val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                                val mime = fmt.optString("type", "")
                                if (!mime.startsWith("video/")) continue
                                val h = fmt.optInt("height", 0)
                                if (h <= targetHeight && h > bestHeight) {
                                    bestHeight = h
                                    bestVideo = fmt
                                }
                            }
                            val videoUrl = bestVideo?.optString("url") ?: ""
                            if (videoUrl.isNotBlank()) {
                                return@withContext MediaResult(
                                    success = true,
                                    url = videoUrl,
                                    filename = "${title}_${bestHeight}p.mp4",
                                    mimeType = "video/mp4"
                                )
                            }
                        }

                        // Fallback: muxed formatStreams
                        if (formatStreams != null) {
                            var bestMuxed: JSONObject? = null
                            var bestHeight = 0
                            for (i in 0 until formatStreams.length()) {
                                val fmt = formatStreams.optJSONObject(i) ?: continue
                                val h = fmt.optInt("height", 0)
                                if (h <= targetHeight && h > bestHeight) {
                                    bestHeight = h
                                    bestMuxed = fmt
                                }
                            }
                            val muxedUrl = bestMuxed?.optString("url") ?: ""
                            if (muxedUrl.isNotBlank()) {
                                return@withContext MediaResult(
                                    success = true,
                                    url = muxedUrl,
                                    filename = "${title}_${bestHeight}p.mp4",
                                    mimeType = "video/mp4"
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                continue // Try next instance
            }
        }
        MediaResult(false, error = "YouTube extraction failed. All Invidious instances unavailable.")
    }

    // ── Main entry point ─────────────────────────────────────────────────────
    suspend fun fetchMediaUrl(
        rawUrl: String,
        audioOnly: Boolean = false,
        resolution: String = "1080p"
    ): MediaResult = withContext(Dispatchers.IO) {
        try {
            val cleanedUrl = cleanVideoUrl(rawUrl)
            val host = Uri.parse(cleanedUrl).host?.lowercase().orEmpty()

            if (cleanedUrl.isBlank() || !cleanedUrl.startsWith("http", ignoreCase = true)) {
                return@withContext MediaResult(false, error = "Invalid media URL")
            }

            // YouTube → Invidious (phone-side, bypasses Oracle block)
            if (isYouTubeUrl(host)) {
                val videoId = extractYouTubeId(cleanedUrl)
                    ?: return@withContext MediaResult(false, error = "Could not extract YouTube video ID.")
                return@withContext fetchYouTubeViaInvidious(videoId, audioOnly, resolution)
            }

            // All other platforms → Oracle Cobalt server
            val apiUrl = baseUrl()
            val bodyJson = JSONObject().apply {
                put("url", cleanedUrl)
                put("downloadMode", if (audioOnly) "audio" else "auto")
                put("videoQuality", when (resolution.lowercase()) {
                    "4k", "2160p" -> "2160"
                    "1440p" -> "1440"
                    "1080p" -> "1080"
                    "720p"  -> "720"
                    "480p"  -> "480"
                    "360p"  -> "360"
                    else    -> "1080"
                })
                put("filenameStyle", "pretty")
                put("audioFormat", "mp3")
                put("audioBitrate", "128")
                put("localProcessing", "disabled")
            }.toString()

            val builder = Request.Builder()
                .url(apiUrl)
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
                        val mediaUrl = normalizeCobaltMediaUrl(json.optString("url"), apiUrl)
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
                            var chosen = picker.optJSONObject(0)
                            for (i in 0 until picker.length()) {
                                val item = picker.optJSONObject(i)
                                if (item?.optString("type") == "video") { chosen = item; break }
                            }
                            val mediaUrl = normalizeCobaltMediaUrl(chosen?.optString("url").orEmpty(), apiUrl)
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
                    "local-processing" -> MediaResult(
                        success = false,
                        error = "Cobalt requires local processing. Configure server to return tunnel/redirect."
                    )
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
            MediaResult(false, error = "Request timed out.")
        } catch (e: Exception) {
            MediaResult(false, error = e.localizedMessage ?: "Network request failed.")
        }
    }

    private fun normalizeCobaltMediaUrl(raw: String, apiUrl: String): String {
        if (raw.isBlank()) return raw
        return try {
            val media = Uri.parse(raw)
            val api = Uri.parse(apiUrl)
            if (media.host.equals(api.host, ignoreCase = true) &&
                media.port == 9000 && api.port == 9001
            ) {
                raw.replaceFirst(Regex("^([a-zA-Z][a-zA-Z0-9+.-]*://[^/:]+):9000(?=/|$)"), "$1:9001")
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
