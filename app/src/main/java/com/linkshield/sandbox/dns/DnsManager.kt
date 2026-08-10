package com.linkshield.sandbox.dns

import android.content.Context
import android.content.SharedPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class DnsManager(context: Context) {

    enum class DnsProvider(val displayName: String, val url: String) {
        CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query"),
        ADGUARD("AdGuard", "https://dns.adguard-dns.com/dns-query"),
        QUAD9("Quad9", "https://dns.quad9.net/dns-query"),
        GOOGLE("Google", "https://dns.google/dns-query")
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var dohClient: OkHttpClient? = null
    private var currentProvider: DnsProvider = DnsProvider.CLOUDFLARE

    companion object {
        private const val KEY_SHIELD_ENABLED = "shield_enabled"
        private const val KEY_DNS_PROVIDER = "dns_provider"
    }

    init {
        // Restore persisted shield state on init
        if (isShieldPersistedOn()) {
            val savedProvider = getSavedProvider()
            try {
                buildDohClient(savedProvider)
                currentProvider = savedProvider
            } catch (_: Exception) {
                // If restore fails, start fresh
                prefs.edit().putBoolean(KEY_SHIELD_ENABLED, false).apply()
            }
        }
    }

    fun getClient(): OkHttpClient = dohClient ?: bootstrapClient

    fun enableDoh(provider: DnsProvider = DnsProvider.CLOUDFLARE): OkHttpClient {
        currentProvider = provider
        buildDohClient(provider)
        prefs.edit()
            .putBoolean(KEY_SHIELD_ENABLED, true)
            .putString(KEY_DNS_PROVIDER, provider.name)
            .apply()
        return dohClient!!
    }

    fun disableDoh(): OkHttpClient {
        dohClient = null
        prefs.edit()
            .putBoolean(KEY_SHIELD_ENABLED, false)
            .apply()
        return bootstrapClient
    }

    fun isDohEnabled(): Boolean = dohClient != null

    fun getCurrentProvider(): DnsProvider = currentProvider

    fun isShieldPersistedOn(): Boolean = prefs.getBoolean(KEY_SHIELD_ENABLED, false)

    private fun getSavedProvider(): DnsProvider {
        val name = prefs.getString(KEY_DNS_PROVIDER, DnsProvider.CLOUDFLARE.name)
        return try {
            DnsProvider.valueOf(name ?: DnsProvider.CLOUDFLARE.name)
        } catch (_: Exception) {
            DnsProvider.CLOUDFLARE
        }
    }

    private fun buildDohClient(provider: DnsProvider) {
        val httpUrl = provider.url.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid DoH URL: ${provider.url}")

        val dns = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(httpUrl)
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1")
            )
            .build()

        dohClient = bootstrapClient.newBuilder()
            .dns(dns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
