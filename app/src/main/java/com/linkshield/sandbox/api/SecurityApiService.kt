package com.linkshield.sandbox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URI
import java.net.URL
import java.net.URLEncoder

data class ThreatCheckResult(
    val checkedUrl: String,
    val isMalicious: Boolean,
    val isSuspicious: Boolean,
    val isError: Boolean = false,
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

    /**
     * Security contract:
     * - A successful reputation response with no threat is SAFE.
     * - A detected threat is MALICIOUS.
     * - Local heuristics are SUSPICIOUS, never SAFE.
     * - Network/API/configuration failure is ERROR, never SAFE.
     */
    suspend fun checkUrl(url: String): ThreatCheckResult =
        withContext(Dispatchers.IO) {
            val normalized = normalizeUrl(url)

            if (normalized == null) {
                return@withContext ThreatCheckResult(
                    checkedUrl = url.trim(),
                    isMalicious = false,
                    isSuspicious = true,
                    message = "Malformed or unsupported URL",
                    source = "local-validation"
                )
            }

            val initialLocalResult = runLocalChecks(normalized)
            if (initialLocalResult != null) {
                return@withContext initialLocalResult
            }

            val expanded = resolveRedirectsSafely(normalized)

            if (!expanded.success) {
                return@withContext ThreatCheckResult(
                    checkedUrl = normalized,
                    isMalicious = false,
                    isSuspicious = false,
                    isError = true,
                    message = expanded.error ?: "Unable to verify URL redirects",
                    source = "redirect-resolution"
                )
            }

            val target = normalizeUrl(expanded.expandedUrl)
                ?: return@withContext ThreatCheckResult(
                    checkedUrl = normalized,
                    isMalicious = false,
                    isSuspicious = false,
                    isError = true,
                    message = "Redirect target could not be normalized",
                    source = "redirect-resolution"
                )

            val localResult = runLocalChecks(target)
            if (localResult != null) {
                return@withContext localResult
            }

            runCatching {
                checkWithGoogleSafeBrowsing(target)
            }.getOrElse { e ->
                ThreatCheckResult(
                    checkedUrl = target,
                    isMalicious = false,
                    isSuspicious = false,
                    isError = true,
                    message = "Security check unavailable: ${e.message ?: "network error"}",
                    source = "Google Web Risk"
                )
            }
        }

    /**
     * Returns a local warning only for characteristics that should not be
     * silently trusted. A null result means reputation lookup is required.
     */
    private fun runLocalChecks(url: String): ThreatCheckResult? {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = true,
                message = "Malformed URL",
                source = "local-validation"
            )

        val host = uri.host?.lowercase()?.removeSuffix(".")
            ?: return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = true,
                message = "URL does not contain a valid hostname",
                source = "local-validation"
            )

        if (!uri.userInfo.isNullOrBlank()) {
            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = true,
                message = "URL contains embedded credentials",
                source = "local-check"
            )
        }

        if (
            host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            isIpv4Address(host) ||
            host.contains(":")
        ) {
            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = true,
                message = "Local/private or direct IP host detected",
                source = "local-check"
            )
        }

        if (host.startsWith("xn--") || host.contains(".xn--")) {
            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = true,
                message = "Internationalized/punycode domain detected",
                source = "local-check"
            )
        }

        return null
    }

    private fun normalizeUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null

        val host = uri.host ?: return null
        if (host.isBlank()) return null

        val asciiHost = runCatching {
            IDN.toASCII(host.trimEnd('.'), IDN.USE_STD3_ASCII_RULES)
        }.getOrNull() ?: return null

        if (asciiHost.isBlank()) return null

        val port = uri.port
        if (port !in -1..65535) return null

        // Rebuild the URI without fragment; fragments are client-side and are
        // not part of server-side reputation identity.
        return runCatching {
            URI(
                scheme,
                uri.userInfo,
                asciiHost.lowercase(),
                port,
                uri.path,
                uri.query,
                null
            ).toASCIIString()
        }.getOrNull()
    }

    private fun isIpv4Address(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        return parts.all {
            val value = it.toIntOrNull() ?: return false
            value in 0..255
        }
    }

    private fun checkWithGoogleSafeBrowsing(url: String): ThreatCheckResult {
        val apiKey = runCatching {
            Class.forName("com.linkshield.sandbox.BuildConfig")
                .getField("SAFE_BROWSING_API_KEY")
                .get(null)
                ?.toString()
        }.getOrNull()

        if (apiKey.isNullOrBlank() || apiKey == "null") {
            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = false,
                isError = true,
                message = "Google Web Risk API key is not configured",
                source = "Google Web Risk"
            )
        }

        /*
         * Google Web Risk Lookup API:
         * one URL per request; multiple threatTypes are supplied as repeated
         * query parameters. This replaces the deprecated Safe Browsing v4
         * threatMatches:find endpoint while keeping the existing BuildConfig
         * secret name for compatibility.
         */
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val endpoint =
            "https://webrisk.googleapis.com/v1/uris:search" +
                "?threatTypes=MALWARE" +
                "&threatTypes=SOCIAL_ENGINEERING" +
                "&threatTypes=UNWANTED_SOFTWARE" +
                "&uri=$encodedUrl" +
                "&key=${URLEncoder.encode(apiKey, "UTF-8")}"

        val connection =
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LinkShieldSandbox/1.0 Android")
            }

        try {
            val status = connection.responseCode
            val response = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(response).optJSONObject("error")
                        ?.optString("message")
                        .orEmpty()
                }.getOrNull().orEmpty()

                return ThreatCheckResult(
                    checkedUrl = url,
                    isMalicious = false,
                    isSuspicious = false,
                    isError = true,
                    message = if (apiMessage.isNotBlank()) {
                        "Google Web Risk API error: $apiMessage"
                    } else {
                        "Google Web Risk API returned HTTP $status"
                    },
                    source = "Google Web Risk"
                )
            }

            val threatTypes = runCatching {
                JSONObject(response)
                    .optJSONObject("threat")
                    ?.optJSONArray("threatTypes")
            }.getOrNull()

            if (threatTypes == null || threatTypes.length() == 0) {
                return ThreatCheckResult(
                    checkedUrl = url,
                    isMalicious = false,
                    isSuspicious = false,
                    isError = false,
                    message = "No known threat detected",
                    source = "Google Web Risk"
                )
            }

            val firstThreat = threatTypes.optString(0)

            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = true,
                isSuspicious = true,
                isError = false,
                threatType = firstThreat,
                message = "Google Web Risk identified this URL as unsafe",
                source = "Google Web Risk"
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun expandUrl(originalUrl: String): ExpandedUrlResult =
        withContext(Dispatchers.IO) {
            val clean = normalizeUrl(originalUrl)
                ?: return@withContext ExpandedUrlResult(
                    originalUrl = originalUrl.trim(),
                    expandedUrl = "",
                    success = false,
                    error = "Malformed or unsupported URL"
                )

            resolveRedirectsSafely(clean)
        }

    private fun resolveRedirectsSafely(initialUrl: String): ExpandedUrlResult {
        var current = initialUrl
        var redirects = 0

        while (redirects < MAX_REDIRECTS) {
            val connection = runCatching {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Android; LinkShieldSandbox)"
                    )
                }
            }.getOrElse {
                return ExpandedUrlResult(
                    originalUrl = initialUrl,
                    expandedUrl = current,
                    success = false,
                    error = "Unable to connect while resolving redirects"
                )
            }

            try {
                val code = connection.responseCode

                // Some servers reject HEAD. Retry once with GET without following
                // redirects, using a small range so we don't download a page body.
                val redirectResponse =
                    if (code == HttpURLConnection.HTTP_BAD_METHOD ||
                        code == HttpURLConnection.HTTP_NOT_IMPLEMENTED
                    ) {
                        connection.disconnect()
                        requestRedirectWithGet(current)
                    } else {
                        code to connection.getHeaderField("Location")
                    }

                val effectiveCode = redirectResponse.first
                val location = redirectResponse.second

                if (effectiveCode in 300..399 && !location.isNullOrBlank()) {
                    val next = runCatching {
                        URI(current).resolve(location).toString()
                    }.getOrNull() ?: return ExpandedUrlResult(
                        originalUrl = initialUrl,
                        expandedUrl = current,
                        success = false,
                        error = "Invalid redirect target"
                    )

                    val redirectSafety = runLocalChecks(next)
                    if (redirectSafety != null) {
                        return ExpandedUrlResult(
                            originalUrl = initialUrl,
                            expandedUrl = next,
                            success = false,
                            error = redirectSafety.message.ifBlank {
                                "Redirect target failed local security checks"
                            }
                        )
                    }

                    current = next
                    redirects++
                    continue
                }

                return ExpandedUrlResult(
                    originalUrl = initialUrl,
                    expandedUrl = current,
                    success = true
                )
            } catch (e: Exception) {
                return ExpandedUrlResult(
                    originalUrl = initialUrl,
                    expandedUrl = current,
                    success = false,
                    error = e.message ?: "Unable to resolve redirects"
                )
            } finally {
                runCatching { connection.disconnect() }
            }
        }

        return ExpandedUrlResult(
            originalUrl = initialUrl,
            expandedUrl = current,
            success = false,
            error = "Too many redirects"
        )
    }

    private fun requestRedirectWithGet(url: String): Pair<Int, String?> {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty(
                    "Range",
                    "bytes=0-0"
                )
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Android; LinkShieldSandbox)"
                )
            }

        return try {
            connection.responseCode to connection.getHeaderField("Location")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun checkAndExpand(
        url: String
    ): Pair<ThreatCheckResult, ExpandedUrlResult> =
        withContext(Dispatchers.IO) {
            val expanded = expandUrl(url)

            if (!expanded.success) {
                val errorResult = ThreatCheckResult(
                    checkedUrl = url.trim(),
                    isMalicious = false,
                    isSuspicious = false,
                    isError = true,
                    message = expanded.error ?: "Unable to verify URL",
                    source = "redirect-resolution"
                )
                return@withContext errorResult to expanded
            }

            val threat = checkUrl(expanded.expandedUrl)
            threat to expanded
        }

    fun isShortenedUrl(url: String): Boolean {
        val host = runCatching {
            URL(url).host.lowercase().removePrefix("www.")
        }.getOrNull() ?: return false

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

    companion object {
        private const val MAX_REDIRECTS = 8
    }
}
