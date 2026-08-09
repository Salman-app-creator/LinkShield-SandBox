package com.linkshield.sandbox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CobaltApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchMediaUrl(pageUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("url", pageUrl)
                put("vCodec", "h264")
                put("vQuality", "720")
                put("filenamePattern", "classic")
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.cobalt.tools/api/json")
                .post(body)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val json = JSONObject(response.body?.string() ?: "")
                when (json.optString("status")) {
                    "tunnel", "redirect" -> {
                        val url = json.optString("url", "")
                        if (url.isNotBlank()) Result.success(url)
                        else Result.failure(IOException("Empty URL"))
                    }
                    "error" -> Result.failure(IOException(json.optString("text", "API Error")))
                    else -> Result.failure(IOException("Unexpected response"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
