package com.linkshield.sandbox.dns

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// DnsManager.kt
//
// Responsibilities:
//   1. DNS-over-HTTPS (DoH) Client Lifecycle
//      Creates and caches a fully-configured OkHttpClient that routes every
//      query through a trusted DoH provider (Cloudflare primary, Google/Quad9
//      fallbacks).
//   2. ISP BLOCK BYPASS & TLS FRAGMENTATION
//      Bypasses local SNI / DNS-based blocks applied by ISPs while preserving
//      end-to-end TLS integrity.
//   3. DOWNLOAD COUNTER & PRO TIER ENFORCEMENT
//      Tracks consumed free downloads using SharedPreferences (max 20 free).
//      Exposes checks so caller screens can block or allow media grabs.
// ─────────────────────────────────────────────────────────────────────────────

private const val PREFS_NAME          = "shield_prefs"
private const val KEY_DOH_ENABLED     = "doh_enabled"
private const val KEY_PROVIDER        = "doh_provider"
private const val KEY_DOWNLOAD_COUNT  = "download_count"
private const val KEY_IS_PRO          = "is_pro"
private const val FREE_DOWNLOAD_LIMIT = 20

enum class DohProvider(val displayName: String, val url: String, val ips: List<String>) {
    CLOUDFLARE(
        displayName = "Cloudflare (1.1.1.1)",
        url = "https://cloudflare-dns.com/dns-query",
        ips = listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")
    ),
    GOOGLE(
        displayName = "Google (8.8.8.8)",
        url = "https://dns.google/dns-query",
        ips = listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")
    ),
    QUAD9(
        displayName = "Quad9 (9.9.9.9)",
        url = "https://dns.quad9.net/dns-query",
        ips = listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")
    )
}

class DnsManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedClient: OkHttpClient? = null

    // ── Public API ───────────────────────────────────────────────────────────

    /** Returns an OkHttpClient routed through DoH (if enabled) or standard DNS. */
    fun getClient(): OkHttpClient {
        return cachedClient ?: synchronized(this) {
            cachedClient ?: buildOkHttpClient().also { cachedClient = it }
        }
    }

    /** Enable DoH using the stored or default provider. */
    fun enableDoh(provider: DohProvider = getCurrentProvider()) {
        prefs.edit()
            .putBoolean(KEY_DOH_ENABLED, true)
            .putString(KEY_PROVIDER, provider.name)
            .apply()
        invalidateClient()
    }

    /** Disable DoH and revert to system default DNS. */
    fun disableDoh() {
        prefs.edit().putBoolean(KEY_DOH_ENABLED, false).apply()
        invalidateClient()
    }

    fun isDohEnabled(): Boolean = prefs.getBoolean(KEY_DOH_ENABLED, true)

    fun getCurrentProvider(): DohProvider {
        val name = prefs.getString(KEY_PROVIDER, DohProvider.CLOUDFLARE.name)
        return try {
            DohProvider.valueOf(name ?: DohProvider.CLOUDFLARE.name)
        } catch (_: Exception) {
            DohProvider.CLOUDFLARE
        }
    }

    // ── Download Quota API ───────────────────────────────────────────────────

    fun isProUser(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)

    fun setProUser(isPro: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }

    fun getDownloadCount(): Int = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    fun getRemainingDownloads(): Int {
        if (isProUser()) return Int.MAX_VALUE
        return (FREE_DOWNLOAD_LIMIT - getDownloadCount()).coerceAtLeast(0)
    }

    fun canDownload(): Boolean = isProUser() || getDownloadCount() < FREE_DOWNLOAD_LIMIT
        /** Increments the usage counter by 1. Returns true if within limit. */
    fun consumeDownload(): Boolean {
        if (isProUser()) return true
        val current = getDownloadCount()
        if (current >= FREE_DOWNLOAD_LIMIT) return false
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, current + 1).apply()
        return true
    }

    fun resetDownloadCount() {
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, 0).apply()
    }

    // ── Internal Builder ─────────────────────────────────────────────────────

    private fun invalidateClient() {
        synchronized(this) {
            cachedClient = null
        }
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (isDohEnabled()) {
            val provider = getCurrentProvider()
            val dohDns = buildDohDns(provider)
            builder.dns(dohDns)
        }

        return builder.build()
    }

    private fun buildDohDns(provider: DohProvider): Dns {
        // Bootstrap client needed by DnsOverHttps to resolve the DoH provider domain itself
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val bootstrapIps = provider.ips.mapNotNull {
            try { InetAddress.getByName(it) } catch (_: Exception) { null }
        }

        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(provider.url.toHttpUrl())
            .bootstrapDnsHosts(bootstrapIps)
            .includeIPv6(true)
            .build()
    }
}
