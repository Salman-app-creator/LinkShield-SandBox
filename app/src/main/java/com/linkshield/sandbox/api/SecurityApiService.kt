package com.linkshield.sandbox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ThreatCheckResult(
    val checkedUrl: String,
    val isMalicious: Boolean,
    val isSuspicious: Boolean,
    val threatType: String = "",
    val message: String = "",
    val source: String = ""
)

data class ExpandedUrlResult(
    val originalUrl: String,
    val expandedUrl: String,
    val success: Boolean,
    val error: String? = null
)

class SecurityApiService {

    suspend fun checkUrl(
        url: String
    ): ThreatCheckResult =
        withContext(Dispatchers.IO) {

            if (url.isBlank()) {
                return@withContext ThreatCheckResult(
                    checkedUrl = url,
                    isMalicious = false,
                    isSuspicious = true,
                    message = "URL is empty"
                )
            }

            try {
                val result =
                    checkWithGoogleSafeBrowsing(
                        url.trim()
                    )

                if (result != null) {
                    return@withContext result
                }
            } catch (_: Exception) {
            }

            return@withContext ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = false,
                message = "No threat detected",
                source = "local-check"
            )
        }

    private fun checkWithGoogleSafeBrowsing(
        url: String
    ): ThreatCheckResult? {

        /*
         * API key can be supplied through BuildConfig
         * when Safe Browsing is configured.
         *
         * We deliberately don't hard-code a secret key.
         */

        val apiKey =
            runCatching {
                Class
                    .forName(
                        "com.linkshield.sandbox.BuildConfig"
                    )
                    .getField(
                        "SAFE_BROWSING_API_KEY"
                    )
                    .get(null)
                    ?.toString()
            }.getOrNull()

        if (
            apiKey.isNullOrBlank() ||
            apiKey == "null"
        ) {
            return null
        }

        val endpoint =
            "https://safebrowsing.googleapis.com/" +
                "v4/threatMatches:find" +
                "?key=" +
                URLEncoder.encode(
                    apiKey,
                    "UTF-8"
                )

        val connection =
            (URL(endpoint).openConnection()
                as HttpURLConnection).apply {

                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true

                setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                setRequestProperty(
                    "Accept",
                    "application/json"
                )
            }

        val body =
            JSONObject().apply {
                put(
                    "client",
                    JSONObject().apply {
                        put(
                            "clientId",
                            "LinkShield"
                        )
                        put(
                            "clientVersion",
                            "1.0"
                        )
                    }
                )

                put(
                    "threatInfo",
                    JSONObject().apply {
                        put(
                            "threatTypes",
                            org.json.JSONArray().apply {
                                put(
                                    "MALWARE"
                                )
                                put(
                                    "SOCIAL_ENGINEERING"
                                )
                                put(
                                    "UNWANTED_SOFTWARE"
                                )
                                put(
                                    "POTENTIALLY_HARMFUL_APPLICATION"
                                )
                            }
                        )

                        put(
                            "platformTypes",
                            org.json.JSONArray().apply {
                                put(
                                    "ANY_PLATFORM"
                                )
                            }
                        )

                        put(
                            "threatEntryTypes",
                            org.json.JSONArray().apply {
                                put(
                                    "URL"
                                )
                            }
                        )

                        put(
                            "threatEntries",
                            org.json.JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put(
                                            "url",
                                            url
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }

        connection.outputStream.use {
            it.write(
                body.toString()
                    .toByteArray(
                        Charsets.UTF_8
                    )
            )
        }

        val status =
            connection.responseCode

        val response =
            if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }?.bufferedReader()
                ?.use {
                    it.readText()
                }
                .orEmpty()

        connection.disconnect()

        if (status !in 200..299) {
            return null
        }

        val json =
            if (response.isBlank()) {
                JSONObject()
            } else {
                JSONObject(response)
            }

        val matches =
            json.optJSONArray(
                "matches"
            )

        if (
            matches == null ||
            matches.length() == 0
        ) {
            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = false,
                message = "No threat detected",
                source = "Google Safe Browsing"
            )
        }

        val first =
            matches.optJSONObject(0)

        val threatType =
            first?.optString(
                "threatType"
            ).orEmpty()

        return ThreatCheckResult(
            checkedUrl = url,
            isMalicious = true,
            isSuspicious = true,
            threatType = threatType,
            message =
                "This URL may be dangerous",
            source = "Google Safe Browsing"
        )
    }

    suspend fun expandUrl(
        originalUrl: String
    ): ExpandedUrlResult =
        withContext(Dispatchers.IO) {

            val clean =
                originalUrl.trim()

            if (clean.isBlank()) {
                return@withContext ExpandedUrlResult(
                    originalUrl = clean,
                    expandedUrl = clean,
                    success = false,
                    error = "URL is empty"
                )
            }

            try {
                val expanded =
                    resolveRedirects(clean)

                ExpandedUrlResult(
                    originalUrl = clean,
                    expandedUrl = expanded,
                    success = true
                )
            } catch (e: Exception) {
                ExpandedUrlResult(
                    originalUrl = clean,
                    expandedUrl = clean,
                    success = false,
                    error =
                        e.message
                            ?: "Unable to expand URL"
                )
            }
        }

    private fun resolveRedirects(
        initialUrl: String
    ): String {

        var current = initialUrl
        var redirects = 0

        while (redirects < 8) {
            val connection =
                (URL(current).openConnection()
                    as HttpURLConnection).apply {

                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    connectTimeout = 8_000
                    readTimeout = 8_000

                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0"
                    )
                }

            val code =
                connection.responseCode

            val location =
                connection.getHeaderField(
                    "Location"
                )

            connection.disconnect()

            if (
                code in 300..399 &&
                !location.isNullOrBlank()
            ) {
                current =
                    URL(
                        URL(current),
                        location
                    ).toString()

                redirects++
            } else {
                break
            }
        }

        return current
    }
        suspend fun checkAndExpand(
        url: String
    ): Pair<ThreatCheckResult, ExpandedUrlResult> =
        withContext(Dispatchers.IO) {

            val expanded =
                expandUrl(url)

            val target =
                if (
                    expanded.success &&
                    expanded.expandedUrl.isNotBlank()
                ) {
                    expanded.expandedUrl
                } else {
                    url
                }

            val threat =
                checkUrl(target)

            threat to expanded
        }

    fun isShortenedUrl(
        url: String
    ): Boolean {
        val host =
            runCatching {
                URL(url).host
                    .lowercase()
                    .removePrefix("www.")
            }.getOrNull()
                ?: return false

        return host in setOf(
            "bit.ly",
            "tinyurl.com",
            "t.co",
            "goo.gl",
            "is.gd",
            "ow.ly",
            "buff.ly",
            "cutt.ly",
            "shorturl.at",
            "rebrand.ly",
            "rb.gy",
            "lnkd.in",
            "s.id"
        )
    }
}
