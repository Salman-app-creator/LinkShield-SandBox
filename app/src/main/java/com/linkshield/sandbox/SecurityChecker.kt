package com.linkshield.sandbox

import java.net.URI

data class ScanResult(
    val score: Int,
    val warnings: List<String>,
    val isDangerous: Boolean
)

object SecurityChecker {

    private val suspiciousKeywords = setOf(
        "login",
        "verify",
        "verification",
        "secure",
        "account",
        "update",
        "banking",
        "wallet",
        "password",
        "signin",
        "confirm",
        "claim",
        "reward",
        "bonus",
        "free"
    )

    private val trustedDomains = setOf(
        "google.com",
        "youtube.com",
        "facebook.com",
        "instagram.com",
        "tiktok.com",
        "x.com",
        "twitter.com",
        "microsoft.com",
        "apple.com",
        "amazon.com",
        "github.com"
    )

    fun analyzeUrl(urlString: String): ScanResult {
        val warnings = mutableListOf<String>()
        var score = 100

        val cleanUrl = urlString.trim()

        if (cleanUrl.isBlank()) {
            return ScanResult(
                score = 0,
                warnings = listOf("No URL available for scanning"),
                isDangerous = false
            )
        }

        try {
            val uri = URI(
                if (
                    cleanUrl.startsWith("http://") ||
                    cleanUrl.startsWith("https://")
                ) {
                    cleanUrl
                } else {
                    "https://$cleanUrl"
                }
            )

            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase().orEmpty()

            if (host.isBlank()) {
                return ScanResult(
                    score = 0,
                    warnings = listOf("Invalid or malformed domain"),
                    isDangerous = true
                )
            }

            val trusted = isTrustedDomain(host)

            if (scheme == "http") {
                score -= 25
                warnings += "Connection is not encrypted"
            }

            if (isIpAddress(host)) {
                score -= 35
                warnings += "Website uses a raw IP address"
            }

            if (host.length > 50) {
                score -= 15
                warnings += "Domain name is unusually long"
            }

            if (host.count { it == '.' } > 4) {
                score -= 10
                warnings += "Domain contains many subdomains"
            }

            if ('@' in cleanUrl) {
                score -= 25
                warnings += "URL contains an @ symbol"
            }

            if (hasSuspiciousPunycode(host)) {
                score -= 35
                warnings += "Domain contains suspicious IDN encoding"
            }

            if (containsSuspiciousKeyword(host) && !trusted) {
                score -= 20
                warnings += "Domain contains a security-related keyword"
            }

            if (hasSuspiciousPort(uri)) {
                score -= 10
                warnings += "URL uses a non-standard port"
            }

            if (hasExcessiveUrlLength(cleanUrl)) {
                score -= 10
                warnings += "URL is unusually long"
            }

            val finalScore = score.coerceIn(0, 100)

            return ScanResult(
                score = finalScore,
                warnings = warnings.distinct(),
                isDangerous = finalScore < 50
            )
        } catch (_: Exception) {
            return ScanResult(
                score = 0,
                warnings = listOf("Malformed or invalid URL"),
                isDangerous = true
            )
        }
    }

    private fun isTrustedDomain(host: String): Boolean {
        return trustedDomains.any {
            host == it || host.endsWith(".$it")
        }
    }

    private fun isIpAddress(host: String): Boolean {
        val ipv4 = Regex(
            "^\\d{1,3}(\\.\\d{1,3}){3}$"
        )

        return ipv4.matches(host) || host.contains(":")
    }

    private fun hasSuspiciousPunycode(host: String): Boolean {
        return host.split(".").any {
            it.startsWith("xn--")
        }
    }

    private fun containsSuspiciousKeyword(host: String): Boolean {
        return suspiciousKeywords.any {
            host.contains(it, ignoreCase = true)
        }
    }

    private fun hasSuspiciousPort(uri: URI): Boolean {
        val port = uri.port

        return port != -1 &&
            port != 80 &&
            port != 443
    }

    private fun hasExcessiveUrlLength(url: String): Boolean {
        return url.length > 500
    }
}
