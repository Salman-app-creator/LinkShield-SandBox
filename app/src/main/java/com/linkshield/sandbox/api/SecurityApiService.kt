package com.linkshield.sandbox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
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
     * SECURITY FIX (browser-security-fix branch):
     *
     * Pehle wala behavior: Safe Browsing API key missing / network error
     * hone par isError=true return hota tha, aur UI us error ko itna
     * seriously leti thi ke POORI WEBSITE load hi nahi hoti thi —
     * user ko "Security check unavailable" wali block screen mil ti thi
     * chahe internet theek ho.
     *
     * Naya behavior:
     *   - Remote check fail ho ya API key na ho  -> OFFLINE fallback
     *     (repo ka existing SecurityChecker engine, 100% local, no network).
     *   - isError sirf tab true jab URL hi malformed ho — aur woh case
     *     pehle hi upar handle ho jata hai.
     *   - Browsing kabhi isError ki wajah se block nahi hogi.
     */
    suspend fun checkUrl(url: String): ThreatCheckResult =
        withContext(Dispatchers.IO) {

            val cleanUrl = url.trim()

            if (cleanUrl.isBlank()) {
                return@withContext ThreatCheckResult(
                    checkedUrl = cleanUrl,
                    isMalicious = false,
                    isSuspicious = true,
                    message = "URL is empty",
                    source = "local-validation"
                )
            }

            // Never send dangerous/non-web schemes to a remote reputation service.
            val scheme = runCatching {
                URI(cleanUrl).scheme?.lowercase()
            }.getOrNull()

            if (scheme !in setOf("http", "https")) {
                return@withContext ThreatCheckResult(
                    checkedUrl = cleanUrl,
                    isMalicious = false,
                    isSuspicious = true,
                    message = "Unsupported or unsafe URL scheme",
                    source = "local-validation"
                )
            }

            // Local heuristic checks first.
            val localResult = runLocalChecks(cleanUrl)
            if (localResult != null) {
                return@withContext localResult
            }

            // Google Safe Browsing (authoritative) — with OFFLINE fallback.
            return@withContext try {
                val remote = checkWithGoogleSafeBrowsing(cleanUrl)
                if (remote.isError) {
                    // Key missing, quota exhausted, ya network down —
                    // offline engine se decide karo, browsing block NAHI.
                    offlineFallback(cleanUrl)
                } else {
                    remote
                }
            } catch (e: Exception) {
                offlineFallback(cleanUrl)
            }
        }

    /**
     * Fully offline decision engine. Yeh repo ke existing
     * [com.linkshield.sandbox.SecurityChecker] ko use karta hai:
     * score + warnings, koi network call nahi.
     *
     * isError = false by design — offline mode mein hum sirf warn karte
     * hain, page load nahi rokte (malicious hone pe bhi warning, block nahi).
     */
    private fun offlineFallback(url: String): ThreatCheckResult {
        val scan = com.linkshield.sandbox.SecurityChecker.analyzeUrl(url)

        return ThreatCheckResult(
            checkedUrl = url,
            isMalicious = scan.isDangerous,
            isSuspicious = scan.warnings.isNotEmpty(),
            isError = false,
            threatType = if (scan.isDangerous) "LOCAL_HEURISTIC" else "",
            message = when {
                scan.warnings.isEmpty() ->
                    "Offline protection active — no local threats detected"
                else ->
                    "Offline protection: ${scan.warnings.first()} (score ${scan.score}/100)"
            },
            source = "Offline SecurityChecker"
        )
    }

    /**
     * Returns a local security result only when something clearly suspicious
     * is detected. Otherwise null means "continue with reputation lookup".
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

        val host = uri.host?.lowercase()?.removePrefix("www.")

        if (host.isNullOrBlank()) {
            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = true,
                message = "URL does not contain a valid hostname",
                source = "local-validation"
            )
        }

        // Credentials embedded in a URL are a strong phishing indicator.
        if (!uri.userInfo.isNullOrBlank()) {
            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = true,
                message = "URL contains embedded credentials",
                source = "local-check"
            )
        }

        // Direct IP URLs are not automatically malicious, but should be
        // presented as suspicious rather than silently trusted.
        if (isIpv4Address(host) || host.contains(":")) {
            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = true,
                message = "Direct IP address detected",
                source = "local-check"
            )
        }

        // Punycode can be legitimate, but is worth warning about because
        // homograph domains can visually imitate trusted domains.
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
            // FIX: pehle yahan hard isError=true return hota tha jo poori
            // site ko block kar deta tha. Ab caller offlineFallback() pe
            // chala jata hai — key na ho to app phir bhi protect karta hai.
            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = false,
                isSuspicious = false,
                isError = true,
                message = "Safe Browsing API key is not configured — using offline protection",
                source = "Google Safe Browsing"
            )
        }

        val endpoint =
            "https://safebrowsing.googleapis.com/v4/threatMatches:find" +
                "?key=" +
                URLEncoder.encode(apiKey, "UTF-8")

        val connection =
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true

                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )
                setRequestProperty(
                    "Accept",
                    "application/json"
                )
            }

        try {
            val body = JSONObject().apply {
                put(
                    "client",
                    JSONObject().apply {
                        put("clientId", "LinkShield")
                        put("clientVersion", "2.6.1")
                    }
                )

                put(
                    "threatInfo",
                    JSONObject().apply {
                        put(
                            "threatTypes",
                            JSONArray().apply {
                                put("MALWARE")
                                put("SOCIAL_ENGINEERING")
                                put("UNWANTED_SOFTWARE")
                                put("POTENTIALLY_HARMFUL_APPLICATION")
                            }
                        )

                        put(
                            "platformTypes",
                            JSONArray().apply {
                                put("ANY_PLATFORM")
                            }
                        )

                        put(
                            "threatEntryTypes",
                            JSONArray().apply {
                                put("URL")
                            }
                        )

                        put(
                            "threatEntries",
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("url", url)
                                    }
                                )
                            }
                        )
                    }
                )
            }

            connection.outputStream.use { output ->
                output.write(
                    body.toString().toByteArray(Charsets.UTF_8)
                )
            }

            val status = connection.responseCode

            val response = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(response).optString("error")
                }.getOrNull().orEmpty()

                return ThreatCheckResult(
                    checkedUrl = url,
                    isMalicious = false,
                    isSuspicious = false,
                    isError = true,
                    message = if (apiMessage.isNotBlank()) {
                        "Safe Browsing API error: $apiMessage"
                    } else {
                        "Safe Browsing API returned HTTP $status"
                    },
                    source = "Google Safe Browsing"
                )
            }

            val json =
                if (response.isBlank()) JSONObject()
                else JSONObject(response)

            val matches = json.optJSONArray("matches")

            // HTTP 200 + no matches is a genuine clean Safe Browsing result.
            if (matches == null || matches.length() == 0) {
                return ThreatCheckResult(
                    checkedUrl = url,
                    isMalicious = false,
                    isSuspicious = false,
                    isError = false,
                    message = "No known threat detected",
                    source = "Google Safe Browsing"
                )
            }

            val firstMatch = matches.optJSONObject(0)

            val threatType =
                firstMatch?.optString("threatType").orEmpty()

            return ThreatCheckResult(
                checkedUrl = url,
                isMalicious = true,
                isSuspicious = true,
                isError = false,
                threatType = threatType,
                message = "Google Safe Browsing identified this URL as dangerous",
                source = "Google Safe Browsing"
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun expandUrl(
        originalUrl: String
    ): ExpandedUrlResult = withContext(Dispatchers.IO) {

        val clean = originalUrl.trim()

        if (clean.isBlank()) {
            return@withContext ExpandedUrlResult(
                originalUrl = clean,
                expandedUrl = clean,
                success = false,
                error = "URL is empty"
            )
        }

        try {
            val expanded = resolveRedirects(clean)

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
                error = e.message ?: "Unable to expand URL"
            )
        }
    }

    private fun resolveRedirects(initialUrl: String): String {
        var current = initialUrl
        var redirects = 0

        while (redirects < 8) {
            val connection =
                (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Android; LinkShield)"
                    )
                }

            try {
                val code = connection.responseCode
                val location =
                    connection.getHeaderField("Location")

                if (code in 300..399 && !location.isNullOrBlank()) {
                    current = URL(
                        URL(current),
                        location
                    ).toString()

                    redirects++
                } else {
                    break
                }
            } finally {
                connection.disconnect()
            }
        }

        return current
    }

    suspend fun checkAndExpand(
        url: String
    ): Pair<ThreatCheckResult, ExpandedUrlResult> =
        withContext(Dispatchers.IO) {

            val expanded = expandUrl(url)

            val target =
                if (
                    expanded.success &&
                    expanded.expandedUrl.isNotBlank()
                ) {
                    expanded.expandedUrl
                } else {
                    url
                }

            val threat = checkUrl(target)

            threat to expanded
        }

    fun isShortenedUrl(url: String): Boolean {
        val host = runCatching {
            URL(url)
                .host
                .lowercase()
                .removePrefix("www.")
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
}
