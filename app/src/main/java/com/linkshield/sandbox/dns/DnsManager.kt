package com.linkshield.sandbox.dns

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory

private const val TAG = "DnsManager"
private const val PREFS_NAME = "shield_prefs"
private const val KEY_DOH_ENABLED = "doh_enabled"
private const val KEY_PROVIDER = "doh_provider"
private const val KEY_DOWNLOAD_COUNT = "download_count"
private const val KEY_IS_PRO = "is_pro"
private const val KEY_INITIALIZED = "initialized"
private const val FREE_DOWNLOAD_LIMIT = 20

private val GOOGLE_BYPASS_HOSTS = setOf(
    "google.com", "www.google.com", "apis.google.com",
    "googleapis.com", "accounts.google.com", "ssl.google-analytics.com",
    "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
    "youtubei.googleapis.com", "googlevideo.com", "manifest.googlevideo.com",
    "yt3.ggpht.com", "i.ytimg.com", "ytimg.com",
    "gstatic.com", "fonts.gstatic.com", "fonts.googleapis.com"
)

private fun isGoogleHost(host: String): Boolean {
    val h = host.lowercase()
    if (GOOGLE_BYPASS_HOSTS.contains(h)) return true
    if (h.endsWith(".googleapis.com") || h.endsWith(".googlevideo.com") ||
        h.endsWith(".google.com") || h.endsWith(".gstatic.com")) return true
    if (h.matches(Regex("(www\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?"))) return true
    return false
}

enum class DohProvider(val displayName: String, val url: String, val ips: List<String>) {
    CLOUDFLARE("Cloudflare (1.1.1.1)", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    CLOUDFLARE_WARP("Cloudflare WARP", "https://1.1.1.1/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("Google (8.8.8.8)", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("Quad9 (9.9.9.9)", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    ADGUARD("AdGuard", "https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14", "94.140.15.15"))
}

// SNI Fragmentation: wrap PLAIN socket BEFORE SSL layers on top
private class SniFragmentingSocketFactory(private val base: SSLSocketFactory) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = base.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = base.supportedCipherSuites
    override fun createSocket(): Socket = base.createSocket()

    override fun createSocket(host: String, port: Int): Socket {
        if (isGoogleHost(host)) return base.createSocket(host, port)
        val plain = Socket()
        plain.connect(java.net.InetSocketAddress(host, port), 15000)
        return base.createSocket(FragmentingPlainSocket(plain), host, port, true)
    }

    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket {
        if (isGoogleHost(host)) return base.createSocket(host, port, local, localPort)
        val plain = Socket()
        plain.bind(java.net.InetSocketAddress(local, localPort))
        plain.connect(java.net.InetSocketAddress(host, port), 15000)
        return base.createSocket(FragmentingPlainSocket(plain), host, port, true)
    }

    override fun createSocket(addr: InetAddress, port: Int): Socket {
        val host = addr.hostName ?: addr.hostAddress ?: ""
        val plain = Socket()
        plain.connect(java.net.InetSocketAddress(addr, port), 15000)
        return if (isGoogleHost(host))
            base.createSocket(plain, host, port, true)
        else
            base.createSocket(FragmentingPlainSocket(plain), host, port, true)
    }

    override fun createSocket(addr: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket {
        val host = addr.hostName ?: addr.hostAddress ?: ""
        val plain = Socket()
        plain.bind(java.net.InetSocketAddress(local, localPort))
        plain.connect(java.net.InetSocketAddress(addr, port), 15000)
        return if (isGoogleHost(host))
            base.createSocket(plain, host, port, true)
        else
            base.createSocket(FragmentingPlainSocket(plain), host, port, true)
    }

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        return if (isGoogleHost(host)) base.createSocket(s, host, port, autoClose)
        else base.createSocket(FragmentingPlainSocket(s), host, port, autoClose)
    }
}

private class FragmentingPlainSocket(private val plain: Socket) : Socket() {
    private var firstWrite = true

    companion object {
        const val SPLIT_AT = 55
    }

    private val fragmentingOut: OutputStream by lazy {
        val base = plain.getOutputStream()
        object : OutputStream() {
            override fun write(b: Int) = base.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (firstWrite && len > SPLIT_AT) {
                    firstWrite = false
                    base.write(b, off, SPLIT_AT)
                    base.flush()
                    base.write(b, off + SPLIT_AT, len - SPLIT_AT)
                } else {
                    base.write(b, off, len)
                }
            }
            override fun flush() = base.flush()
            override fun close() = base.close()
        }
    }

    override fun getOutputStream(): OutputStream = fragmentingOut
    override fun getInputStream(): java.io.InputStream = plain.getInputStream()
    override fun close() = plain.close()
    override fun isClosed(): Boolean = plain.isClosed
    override fun isConnected(): Boolean = plain.isConnected
    override fun getInetAddress(): InetAddress = plain.inetAddress
    override fun getPort(): Int = plain.port
    override fun getLocalAddress(): InetAddress = plain.localAddress
    override fun getLocalPort(): Int = plain.localPort
    override fun getRemoteSocketAddress(): java.net.SocketAddress = plain.remoteSocketAddress
    override fun getLocalSocketAddress(): java.net.SocketAddress = plain.localSocketAddress
    override fun connect(endpoint: java.net.SocketAddress) = plain.connect(endpoint)
    override fun connect(endpoint: java.net.SocketAddress, timeout: Int) = plain.connect(endpoint, timeout)
    override fun bind(bindpoint: java.net.SocketAddress) = plain.bind(bindpoint)
    override fun getChannel(): java.nio.channels.SocketChannel = plain.channel
    override fun setSoTimeout(timeout: Int) = plain.setSoTimeout(timeout)
    override fun getSoTimeout(): Int = plain.soTimeout
    override fun setSoLinger(on: Boolean, linger: Int) = plain.setSoLinger(on, linger)
    override fun getSoLinger(): Int = plain.soLinger
    override fun setTcpNoDelay(on: Boolean) = plain.setTcpNoDelay(on)
    override fun getTcpNoDelay(): Boolean = plain.tcpNoDelay
    override fun setKeepAlive(on: Boolean) = plain.setKeepAlive(on)
    override fun getKeepAlive(): Boolean = plain.keepAlive
    override fun setReuseAddress(on: Boolean) = plain.setReuseAddress(on)
    override fun getReuseAddress(): Boolean = plain.reuseAddress
    override fun setSendBufferSize(size: Int) = plain.setSendBufferSize(size)
    override fun getSendBufferSize(): Int = plain.sendBufferSize
    override fun setReceiveBufferSize(size: Int) = plain.setReceiveBufferSize(size)
    override fun getReceiveBufferSize(): Int = plain.receiveBufferSize
    override fun shutdownInput() = plain.shutdownInput()
    override fun shutdownOutput() = plain.shutdownOutput()
    override fun isInputShutdown(): Boolean = plain.isInputShutdown
    override fun isOutputShutdown(): Boolean = plain.isOutputShutdown
    override fun toString(): String = plain.toString()
}

class DnsManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile private var cachedClient: OkHttpClient? = null

    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    init {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putInt(KEY_DOWNLOAD_COUNT, 0)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
            Log.d(TAG, "Fresh install — download counter initialised at 0")
        }
    }

    fun getClient(): OkHttpClient = cachedClient ?: synchronized(this) {
        cachedClient ?: buildClient().also { cachedClient = it }
    }

    fun enableDoh(provider: DohProvider = getCurrentProvider()) {
        prefs.edit()
            .putBoolean(KEY_DOH_ENABLED, true)
            .putString(KEY_PROVIDER, provider.name)
            .apply()
        invalidate()
        try {
            getClient()
            Log.d(TAG, "DoH ON: ${provider.displayName}")
        } catch (e: Exception) {
            Log.w(TAG, "Primary DoH failed, trying WARP: ${e.message}")
            prefs.edit().putString(KEY_PROVIDER, DohProvider.CLOUDFLARE_WARP.name).apply()
            invalidate()
            runCatching { getClient() }.onFailure { Log.e(TAG, "WARP fallback also failed: ${it.message}") }
        }
    }

    fun disableDoh() {
        prefs.edit().putBoolean(KEY_DOH_ENABLED, false).apply()
        invalidate()
        Log.d(TAG, "DoH OFF")
    }

    fun isDohEnabled(): Boolean = prefs.getBoolean(KEY_DOH_ENABLED, true)
    fun isShieldPersistedOn(): Boolean = isDohEnabled()

    fun getCurrentProvider(): DohProvider {
        val name = prefs.getString(KEY_PROVIDER, DohProvider.CLOUDFLARE.name)
        return runCatching { DohProvider.valueOf(name!!) }.getOrDefault(DohProvider.CLOUDFLARE)
    }

    fun isProUser(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)
    fun setProUser(pro: Boolean) { prefs.edit().putBoolean(KEY_IS_PRO, pro).apply() }
    fun getDownloadCount(): Int = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    fun getRemainingDownloads(): Int =
        if (isProUser()) Int.MAX_VALUE
        else (FREE_DOWNLOAD_LIMIT - getDownloadCount()).coerceAtLeast(0)

    fun canDownload(): Boolean = isProUser() || getDownloadCount() < FREE_DOWNLOAD_LIMIT

    fun consumeDownload(): Boolean {
        if (isProUser()) return true
        val current = getDownloadCount()
        if (current >= FREE_DOWNLOAD_LIMIT) return false
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, current + 1).apply()
        Log.d(TAG, "Download consumed: ${current + 1}/$FREE_DOWNLOAD_LIMIT")
        return true
    }

    fun resetDownloadCount() { prefs.edit().putInt(KEY_DOWNLOAD_COUNT, 0).apply() }

    private fun invalidate() { synchronized(this) { cachedClient = null } }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)

        if (isDohEnabled()) {
            builder.dns(buildDohDns(getCurrentProvider()))
            try {
                val base = javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory()
                builder.sslSocketFactory(SniFragmentingSocketFactory(base), buildTrustManager())
                Log.d(TAG, "SNI fragmentation active (Google domains bypassed)")
            } catch (e: Exception) {
                Log.w(TAG, "SNI fragmentation unavailable: ${e.message}")
            }
        }
        return builder.build()
    }

    private fun buildDohDns(provider: DohProvider): Dns {
        val ips = provider.ips.mapNotNull {
            runCatching { InetAddress.getByName(it) }.getOrNull()
        }
        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(provider.url.toHttpUrl())
            .bootstrapDnsHosts(ips)
            .includeIPv6(false)
            .post(true)
            .build()
    }

    private fun buildTrustManager(): javax.net.ssl.X509TrustManager {
        val tmf = javax.net.ssl.TrustManagerFactory.getInstance(
            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
        )
        tmf.init(null as java.security.KeyStore?)
        return tmf.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().first()
    }
}
