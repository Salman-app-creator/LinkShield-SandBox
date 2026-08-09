package com.linkshield.sandbox.dns

import android.content.Context
import android.content.Intent
import android.provider.Settings
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.util.concurrent.TimeUnit

sealed class DnsProvider(
    val label: String,
    val dohUrl: String,
    val ips: List<String>
) {
    object Cloudflare : DnsProvider("Cloudflare 1.1.1.1", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1"))
    object AdGuard : DnsProvider("AdGuard DNS", "https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14"))
    object Quad9 : DnsProvider("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9"))
}

class DnsManager(context: Context) {
    private val prefs = context.getSharedPreferences("dns_config", Context.MODE_PRIVATE)

    var activeClient: DnsOverHttps? = null
        private set

    fun getSavedProvider(): DnsProvider {
        return when (prefs.getString("provider", "cloudflare")) {
            "adguard" -> DnsProvider.AdGuard
            "quad9" -> DnsProvider.Quad9
            else -> DnsProvider.Cloudflare
        }
    }

    fun saveProvider(provider: DnsProvider) {
        val key = when (provider) {
            is DnsProvider.Cloudflare -> "cloudflare"
            is DnsProvider.AdGuard -> "adguard"
            is DnsProvider.Quad9 -> "quad9"
        }
        prefs.edit().putString("provider", key).apply()
    }

    fun buildClient(provider: DnsProvider): DnsOverHttps {
        val bootstrap = provider.ips.map { InetAddress.getByName(it) }
        val dns = DnsOverHttps.Builder()
            .url(provider.dohUrl.toHttpUrl())
            .bootstrapDnsHosts(bootstrap)
            .build()
        activeClient = dns
        return dns
    }

    fun createOkHttpClient(): OkHttpClient {
        val provider = getSavedProvider()
        val doh = activeClient ?: buildClient(provider)
        return OkHttpClient.Builder()
            .dns(doh)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun openSystemDnsSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
}
