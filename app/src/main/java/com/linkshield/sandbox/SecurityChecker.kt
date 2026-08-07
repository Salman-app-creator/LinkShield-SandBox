package com.linkshield.sandbox

import java.net.URI

data class ScanResult(
    val score: Int,
    val warnings: List<String>,
    val isDangerous: Boolean
)

object SecurityChecker {
    fun analyzeUrl(urlString: String): ScanResult {
        val warnings = mutableListOf<String>()
        var score = 100

        try {
            val uri = URI(urlString)
            val host = uri.host?.lowercase() ?: ""

            if (uri.scheme != "https") {
                score -= 30
                warnings.add("Unencrypted connection (HTTP)")
            }

            if (host.matches(Regex(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*"))) {
                score -= 40
                warnings.add("Uses raw IP address instead of domain")
            }

            if (host.length > 50) {
                score -= 15
                warnings.add("Suspiciously long domain name")
            }

            val suspiciousKeywords = listOf("login", "verify", "secure", "account", "update", "banking", "free", "claim")
            for (keyword in suspiciousKeywords) {
                if (host.contains(keyword) && !isLegitimateDomain(host)) {
                    score -= 25
                    warnings.add("Contains suspicious keyword: $keyword")
                    break
                }
            }

        } catch (e: Exception) {
            score = 0
            warnings.add("Malformed or invalid URL structure")
        }

        val finalScore = score.coerceIn(0, 100)
        return ScanResult(
            score = finalScore,
            warnings = warnings,
            isDangerous = finalScore < 50
        )
    }

    private fun isLegitimateDomain(host: String): Boolean {
        val trusted = listOf("google.com", "facebook.com", "microsoft.com", "apple.com", "amazon.com", "github.com")
        return trusted.any { host == it || host.endsWith(".$it") }
    }
}
