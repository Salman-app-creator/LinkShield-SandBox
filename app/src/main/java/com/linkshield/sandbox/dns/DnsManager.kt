package com.linkshield.sandbox.dns

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

// ─────────────────────────────────────────────────────────────────────────────
// DnsManager.kt
//
// Features:
//  1. DoH (DNS-over-HTTPS) via OkHttp — Cloudflare, WARP, Google, Quad9, AdGuard
//  2. SNI fragmentation via SniFragmentingSocketFactory — bypasses ISP DPI blocks
//  3. Google/YouTube bypass — skips fragmentation for Google domains to prevent
//     reCAPTCHA / 429 triggers
//  4. Download quota — 20 free downloads tracked in SharedPreferences
//  5. isShieldPersistedOn() alias — kept for call-site compatibility
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG                 = "DnsManager"
private const val PREFS_NAME          = "shield_prefs"
private const val KEY_DOH_ENABLED     = "doh_enabled"
private const val KEY_PROVIDER        = "doh_provider"
private const val KEY_DOWNLOAD_COUNT  = "download_count"
private const val KEY_IS_PRO          = "is_pro"
private const val KEY_INITIALIZED     = "initialized"
private const val FREE_DOWNLOAD_LIMIT = 20

// ── Google / YouTube — must bypass SNI fragmentation ─────────────────────────
// Fragmenting TLS ClientHello to Google servers triggers reCAPTCHA / HTTP 429.
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
        h.endsWith(".google.com")     || h.endsWith(".gstatic.com")) return true
    // google.co.uk, google.de, etc.
    if (h.matches(Regex("(www\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?"))) return true
    return false
}

// ─────────────────────────────────────────────────────────────────────────────
// DoH Provider catalogue
// ─────────────────────────────────────────────────────────────────────────────
enum class DohProvider(
    val displayName: String,
    val url:         String,
    val ips:         List<String>
) {
    CLOUDFLARE(
        displayName = "Cloudflare (1.1.1.1)",
        url         = "https://cloudflare-dns.com/dns-query",
        ips         = listOf("1.1.1.1", "1.0.0.1")
    ),
    CLOUDFLARE_WARP(
        displayName = "Cloudflare WARP",
        url         = "https://1.1.1.1/dns-query",
        ips         = listOf("1.1.1.1", "1.0.0.1")
    ),
    GOOGLE(
        displayName = "Google (8.8.8.8)",
        url         = "https://dns.google/dns-query",
        ips         = listOf("8.8.8.8", "8.8.4.4")
    ),
    QUAD9(
        displayName = "Quad9 (9.9.9.9)",
        url         = "https://dns.quad9.net/dns-query",
        ips         = listOf("9.9.9.9", "149.112.112.112")
    ),
    ADGUARD(
        displayName = "AdGuard",
        url         = "https://dns.adguard-dns.com/dns-query",
        ips         = listOf("94.140.14.14", "94.140.15.15")
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SNI Fragmentation
//
// How it works:
//   ISPs that block sites by SNI read the "server_name" TLS extension from
//   a single-packet ClientHello. SniFragmentingSocketFactory wraps the SSL
//   socket's OutputStream so the first write is split: 1 byte first, then the
//   rest. Single-packet DPI engines cannot reassemble the SNI across two TCP
//   segments → connection allowed.
//
// Google bypass applied in wrap(): Google domains receive the plain socket.
// ─────────────────────────────────────────────────────────────────────────────
private class SniFragmentingSocketFactory(
    private val base: SSLSocketFactory
) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String>   = base.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = base.supportedCipherSuites
    override fun createSocket(): Socket                    = base.createSocket()

    override fun createSocket(host: String, port: Int): Socket =
        wrap(base.createSocket(host, port) as SSLSocket, host)

    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket =
        wrap(base.createSocket(host, port, local, localPort) as SSLSocket, host)

    override fun createSocket(addr: InetAddress, port: Int): Socket =
        base.createSocket(addr, port)

    override fun createSocket(addr: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket =
        base.createSocket(addr, port, local, localPort)

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        return if (isGoogleHost(host)) base.createSocket(s, host, port, autoClose)
        else wrap(base.createSocket(s, host, port, autoClose) as SSLSocket, host)
    }

    private fun wrap(ssl: SSLSocket, host: String): SSLSocket =
        if (isGoogleHost(host)) ssl else FragmentingSSLSocket(ssl)
}

private class FragmentingSSLSocket(private val d: SSLSocket) : SSLSocket() {
    private var firstDone = false
    private val out: OutputStream by lazy { d.outputStream }

    override fun getOutputStream(): OutputStream = object : OutputStream() {
        override fun write(b: Int) { out.write(b) }
        override fun write(buf: ByteArray, off: Int, len: Int) {
            if (!firstDone && len > 1) {
                firstDone = true
                out.write(buf, off, 1); out.flush()
                out.write(buf, off + 1, len - 1)
            } else {
                out.write(buf, off, len)
            }
        }
        override fun flush() = out.flush()
        override fun close() = out.close()
    }

    override fun getInputStream(): InputStream                    = d.inputStream
    override fun startHandshake()                                 { d.startHandshake() }
    override fun getSession()                                     = d.session
    override fun addHandshakeCompletedListener(l: javax.net.ssl.HandshakeCompletedListener) { d.addHandshakeCompletedListener(l) }
    override fun removeHandshakeCompletedListener(l: javax.net.ssl.HandshakeCompletedListener) { d.removeHandshakeCompletedListener(l) }
    override fun setUseClientMode(m: Boolean)                     { d.useClientMode = m }
    override fun getUseClientMode(): Boolean                      = d.useClientMode
    override fun setNeedClientAuth(n: Boolean)                    { d.needClientAuth = n }
    override fun getNeedClientAuth(): Boolean                     = d.needClientAuth
    override fun setWantClientAuth(w: Boolean)                    { d.wantClientAuth = w }
    override fun getWantClientAuth(): Boolean                     = d.wantClientAuth
    override fun setEnableSessionCreation(f: Boolean)             { d.enableSessionCreation = f }
    override fun getEnableSessionCreation(): Boolean              = d.enableSessionCreation
    override fun getSupportedCipherSuites(): Array<String>        = d.supportedCipherSuites
    override fun getEnabledCipherSuites(): Array<String>          = d.enabledCipherSuites
    override fun setEnabledCipherSuites(s: Array<String>)        { d.enabledCipherSuites = s }
    override fun getSupportedProtocols(): Array<String>           = d.supportedProtocols
    override fun getEnabledProtocols(): Array<String>             = d.enabledProtocols
    override fun setEnabledProtocols(p: Array<String>)           { d.enabledProtocols = p }
    override fun close()                                          { d.close() }
    override fun isClosed(): Boolean                              = d.isClosed
    override fun isConnected(): Boolean                           = d.isConnected
    override fun getInetAddress(): InetAddress                    = d.inetAddress
    override fun getPort(): Int                                   = d.port
    override fun getLocalAddress(): InetAddress                   = d.localAddress
    override fun getLocalPort(): Int                              = d.localPort
    override fun getRemoteSocketAddress(): java.net.SocketAddress = d.remoteSocketAddress
    override fun getLocalSocketAddress(): java.net.SocketAddress  = d.localSocketAddress
    override fun connect(e: java.net.SocketAddress)               { d.connect(e) }
    override fun connect(e: java.net.SocketAddress, t: Int)       { d.connect(e, t) }
    override fun bind(b: java.net.SocketAddress)                  { d.bind(b) }
    override fun getChannel(): java.nio.channels.SocketChannel    = d.channel
    override fun setSoTimeout(t: Int)                             { d.soTimeout = t }
    override fun getSoTimeout(): Int                              = d.soTimeout
    override fun setSoLinger(on: Boolean, l: Int)                 { d.setSoLinger(on, l) }
    override fun getSoLinger(): Int                               = d.soLinger
    override fun setTcpNoDelay(on: Boolean)                       { d.tcpNoDelay = on }
    override fun getTcpNoDelay(): Boolean                         = d.tcpNoDelay
    override fun setKeepAlive(on: Boolean)                        { d.keepAlive = on }
    override fun getKeepAlive(): Boolean                          = d.keepAlive
    override fun setReuseAddress(on: Boolean)                     { d.reuseAddress = on }
    override fun getReuseAddress(): Boolean                       = d.reuseAddress
    override fun setSendBufferSize(s: Int)                        { d.sendBufferSize = s }
    override fun getSendBufferSize(): Int                         = d.sendBufferSize
    override fun setReceiveBufferSize(s: Int)                     { d.receiveBufferSize = s }
    override fun getReceiveBufferSize(): Int                      = d.receiveBufferSize
    override fun shutdownInput()                                  { d.shutdownInput() }
    override fun shutdownOutput()                                 { d.shutdownOutput() }
    override fun isInputShutdown(): Boolean                       = d.isInputShutdown
    override fun isOutputShutdown(): Boolean                      = d.isOutputShutdown
    override fun toString(): String                               = d.toString()
}

// ─────────────────────────────────────────────────────────────────────────────
// DnsManager
// ─────────────────────────────────────────────────────────────────────────────
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
        // Fresh install → counter must start at 0, not a stale value
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putInt(KEY_DOWNLOAD_COUNT, 0)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
            Log.d(TAG, "Fresh install — download counter initialised at 0")
        }
    }

    // ── OkHttpClient API ──────────────────────────────────────────────────────

    /** Thread-safe, cached OkHttpClient with DoH + SNI fragmentation when shield is ON. */
    fun getClient(): OkHttpClient = cachedClient ?: synchronized(this) {
        cachedClient ?: buildClient().also { cachedClient = it }
    }

    /**
     * Enable DoH with [provider]. Falls back to CLOUDFLARE_WARP if primary fails.
     */
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

    /** Alias kept for call-site compatibility with older UnblockShieldScreen code. */
    fun isShieldPersistedOn(): Boolean = isDohEnabled()

    fun getCurrentProvider(): DohProvider {
        val name = prefs.getString(KEY_PROVIDER, DohProvider.CLOUDFLARE.name)
        return runCatching { DohProvider.valueOf(name!!) }.getOrDefault(DohProvider.CLOUDFLARE)
    }

    // ── Download quota API ────────────────────────────────────────────────────

    fun isProUser(): Boolean   = prefs.getBoolean(KEY_IS_PRO, false)
    fun setProUser(pro: Boolean) { prefs.edit().putBoolean(KEY_IS_PRO, pro).apply() }
    fun getDownloadCount(): Int = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    fun getRemainingDownloads(): Int =
        if (isProUser()) Int.MAX_VALUE
        else (FREE_DOWNLOAD_LIMIT - getDownloadCount()).coerceAtLeast(0)

    fun canDownload(): Boolean = isProUser() || getDownloadCount() < FREE_DOWNLOAD_LIMIT

    /**
     * Atomically increments the counter.
     * Returns true when the download is allowed; false when limit is reached.
     */
    fun consumeDownload(): Boolean {
        if (isProUser()) return true
        val current = getDownloadCount()
        if (current >= FREE_DOWNLOAD_LIMIT) return false
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, current + 1).apply()
        Log.d(TAG, "Download consumed: ${current + 1}/$FREE_DOWNLOAD_LIMIT")
        return true
    }

    fun resetDownloadCount() { prefs.edit().putInt(KEY_DOWNLOAD_COUNT, 0).apply() }

    // ── Internal ──────────────────────────────────────────────────────────────

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
