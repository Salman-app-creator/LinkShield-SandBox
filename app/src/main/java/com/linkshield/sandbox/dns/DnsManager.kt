package com.linkshield.sandbox.dns

import android.content.Context
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

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var dohClient: OkHttpClient? = null
    private var currentProvider: DnsProvider = DnsProvider.CLOUDFLARE

    fun getClient(): OkHttpClient {
        return dohClient ?: bootstrapClient
    }

    fun enableDoh(provider: DnsProvider = DnsProvider.CLOUDFLARE): OkHttpClient {
        currentProvider = provider
        val httpUrl = okhttp3.HttpUrl.parse(provider.url)
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
            .build()

        return dohClient!!
    }

    fun disableDoh(): OkHttpClient {
        dohClient = null
        return bootstrapClient
    }

    fun isDohEnabled(): Boolean = dohClient != null

    fun getCurrentProvider(): DnsProvider = currentProvider
}
