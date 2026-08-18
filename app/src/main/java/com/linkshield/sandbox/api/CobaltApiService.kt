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
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object {
        const val API_BASE = "https://api.cobalt.tools"
    }

    data class MediaResult(
        val success: Boolean,
        val url: String? = null,
        val filename: String? = null,
        val error: String? = null,
        val isDirectDownload: Boolean = false
    )

    suspend fun fetchMediaUrl(pageUrl: String): MediaResult = withContext(Dispatchers.IO) {
        try {
            // Current Cobalt API schema. The older /api/json v7 endpoint is no longer
            // a reliable public dependency, so the current root POST endpoint is used.
            val jsonBody = JSONObject().apply {
                put("url", pageUrl)
                put("downloadMode", "auto")
                put("videoQuality", "720")
                put("audioFormat", "mp3")
                put("audioBitrate", "128")
                put("filenameStyle", "classic")
            }

            val request = Request.Builder()
                .url(API_BASE)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    return@withContext MediaResult(
                        success = false,
                        error = "Cobalt API error: HTTP ${response.code}"
                    )
                }

                val json = JSONObject(responseBody)
                when (json.optString("status")) {
                    "redirect", "tunnel", "stream" -> {
                        val url = json.optString("url")
                        if (url.isBlank()) {
                            MediaResult(false, error = "Cobalt returned an empty media URL")
                        } else {
                            MediaResult(
                                success = true,
                                url = url,
                                filename = json.optString("filename", "download"),
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
                                success = true,
                                url = url,
                                filename = "download"
                            )
                        }
                    }

                    "local-processing" -> {
                        val tunnels = json.optJSONArray("tunnel")
                        val url = tunnels?.optString(0).orEmpty()
                        if (url.isBlank()) {
                            MediaResult(false, error = "Cobalt requires local media processing")
                        } else {
                            MediaResult(
                                success = true,
                                url = url,
                                filename = json.optJSONObject("output")?.optString("filename", "download")
                            )
                        }
                    }

                    "error" -> {
                        val errorObject = json.optJSONObject("error")
                        MediaResult(
                            success = false,
                            error = errorObject?.optString("code")
                                ?: json.optString("text", "Cobalt rejected the URL")
                        )
                    }

                    else -> MediaResult(false, error = "Unexpected Cobalt response")
                }
            }
        } catch (e: IOException) {
            MediaResult(success = false, error = "Network error: ${e.message}")
        } catch (e: Exception) {
            MediaResult(success = false, error = "Error: ${e.message}")
        }
    }

    fun startDownload(url: String, filename: String, mimeType: String = "video/mp4"): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(filename)
            .setDescription("Downloading via LinkShield")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LinkShield/$filename")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return downloadManager.enqueue(request)
    }

    fun isDirectFileLink(url: String): Boolean {
        val directExtensions = listOf(".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".pdf", ".zip", ".jpg", ".png")
        return directExtensions.any { url.lowercase().endsWith(it) }
    }
}
