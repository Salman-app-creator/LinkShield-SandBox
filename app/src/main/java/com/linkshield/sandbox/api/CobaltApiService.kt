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
            // FIXED: Cobalt API v7 correct body format
            val jsonBody = JSONObject().apply {
                put("url", pageUrl)
                put("downloadMode", "auto")
                put("videoQuality", "720")
                put("audioFormat", "mp3")
                put("filenameStyle", "classic")
            }

            val request = Request.Builder()
                .url("$API_BASE/api/json")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext MediaResult(
                    success = false,
                    error = "API Error: ${response.code}"
                )
            }

            val json = JSONObject(responseBody)
            val status = json.optString("status")

            when (status) {
                "stream", "redirect", "tunnel" -> {
                    val url = json.optString("url")
                    val filename = json.optString("filename", "download")
                    MediaResult(
                        success = true,
                        url = url,
                        filename = filename,
                        isDirectDownload = status == "tunnel"
                    )
                }
                "error" -> {
                    MediaResult(
                        success = false,
                        error = json.optString("text", "Unknown error")
                    )
                }
                else -> {
                    val url = json.optString("url")
                    if (url.isNotEmpty()) {
                        MediaResult(success = true, url = url, filename = "download")
                    } else {
                        MediaResult(success = false, error = "Unexpected response format")
                    }
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
