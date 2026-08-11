package com.linkshield.sandbox.dns

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

// ─────────────────────────────────────────────────────────────────────────────
// DnsManager.kt
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "DnsManager"

// SharedPreferences keys
private const val PREFS_NAME            = "shield_prefs"
private const val KEY_SHIELD_ENABLED    = "shield_enabled"
private const val KEY_DNS_PROVIDER      = "dns_provider"
private const val KEY_IS_PRO            = "is_pro"
private const val KEY_DOWNLOAD_COUNT    = "download_count"
private const val KEY_INITIALIZED       = "initialized"
private const val FREE_DOWNLOAD_LIMIT   = 20

private const val WARP_DOH_URL = "https://1.1.1.1/dns-query"

class DnsManager(private val context: Context) {

    // ── Provider catalogue ────────────────────────────────────────────────────
    enum class DnsProvider(
        val displayName:  String,
        val dohUrl:       String,
        val bootstrapIps: List<String>
    ) {
        CLOUDFLARE(
            displayName  = "Cloudflare",
            dohUrl       = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1")
        ),
        CLOUDFLARE_WARP(
            displayName  = "Cloudflare WARP (Fallback)",
            dohUrl       = WARP_DOH_URL,
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1")
        ),
        ADGUARD(
            displayName  = "AdGuard",
            dohUrl       = "https://dns.adguard-dns.com/dns-query",
            bootstrapIps = listOf("94.140.14.14", "94.140.15.15")
        ),
        QUAD9(
            displayName  = "Quad9",
            dohUrl       = "https://dns.quad9.net/dns-query",
            bootstrapIps = listOf("9.9.9.9", "149.112.112.112")
        ),
        GOOGLE(
            displayName  = "Google",
            dohUrl       = "https://dns.google/dns-query",
            bootstrapIps = listOf("8.8.8.8", "8.8.4.4")
        )
    }

    // ── Internal state ────────────────────────────────────────────────────────
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val bootstrapClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10,  TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val standardSocketFactory: SocketFactory = SocketFactory.getDefault()
    private val fragmentingSocketFactory: TlsFragmentingSocketFactory = TlsFragmentingSocketFactory()

    @Volatile
    private var activeDohClient: OkHttpClient? = null

    @Volatile
    private var activeProvider: DnsProvider = DnsProvider.CLOUDFLARE

    private val stateLock = Any()

    init {
        ensureDownloadCountInitialized()

        if (isShieldPersistedOn()) {
            val saved = getSavedProvider()
            try {
                enableDoh(saved)
                Log.d(TAG, "Restored DoH shield: ${saved.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore DoH on init, disabling shield: ${e.message}")
                prefs.edit().putBoolean(KEY_SHIELD_ENABLED, false).apply()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    fun getClient(): OkHttpClient = activeDohClient ?: bootstrapClient

    fun enableDoh(provider: DnsProvider = DnsProvider.CLOUDFLARE): OkHttpClient = synchronized(stateLock) {
        // Strategy 1 — standard TLS, primary provider
        try {
            val client = buildAndTestDohClient(provider, useFragmentation = false)
            setActiveClient(client, provider)
            persistShieldState(enabled = true, provider = provider)
            Log.d(TAG, "DoH enabled (standard): ${provider.displayName}")
            return client
        } catch (primaryStd: Exception) {
            Log.w(TAG, "Standard TLS blocked for ${provider.displayName}: ${primaryStd.message}")
        }

        // Strategy 2 — fragmented TLS, primary provider
        try {
            val client = buildAndTestDohClient(provider, useFragmentation = true)
            setActiveClient(client, provider)
            persistShieldState(enabled = true, provider = provider)
            Log.d(TAG, "DoH enabled (fragmented): ${provider.displayName}")
            return client
        } catch (primaryFrag: Exception) {
            Log.w(TAG, "Fragmented TLS failed for ${provider.displayName}: ${primaryFrag.message}")
        }

        // Strategy 3 — standard TLS, WARP fallback
        try {
            val client = buildAndTestDohClient(DnsProvider.CLOUDFLARE_WARP, useFragmentation = false)
            setActiveClient(client, DnsProvider.CLOUDFLARE_WARP)
            persistShieldState(enabled = true, provider = DnsProvider.CLOUDFLARE_WARP)
            Log.d(TAG, "DoH fallback active (standard): CLOUDFLARE_WARP")
            return client
        } catch (warpStd: Exception) {
            Log.w(TAG, "WARP standard TLS failed: ${warpStd.message}")
        }

        // Strategy 4 — fragmented TLS, WARP fallback
        try {
            val client = buildAndTestDohClient(DnsProvider.CLOUDFLARE_WARP, useFragmentation = true)
            setActiveClient(client, DnsProvider.CLOUDFLARE_WARP)
            persistShieldState(enabled = true, provider = DnsProvider.CLOUDFLARE_WARP)
            Log.d(TAG, "DoH fallback active (fragmented): CLOUDFLARE_WARP")
            return client
        } catch (warpFrag: Exception) {
            Log.e(TAG, "WARP fragmented TLS also failed: ${warpFrag.message}")
            throw IllegalStateException(
                "DoH unavailable — all strategies exhausted. " +
                "Primary standard: ${warpFrag.message}"
            )
        }
    }

    fun disableDoh() = synchronized(stateLock) {
        activeDohClient?.connectionPool?.evictAll()
        activeDohClient = null
        activeProvider = DnsProvider.CLOUDFLARE
        prefs.edit()
            .putBoolean(KEY_SHIELD_ENABLED, false)
            .remove(KEY_DNS_PROVIDER)
            .apply()
        Log.d(TAG, "DoH disabled, connection pool evicted")
    }

    fun isDohEnabled(): Boolean = activeDohClient != null
    fun getCurrentProvider(): DnsProvider = activeProvider
    fun isShieldPersistedOn(): Boolean = prefs.getBoolean(KEY_SHIELD_ENABLED, false)

    fun isProUser(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)
    fun getDownloadCount(): Int = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    fun getRemainingDownloads(): Int =
        if (isProUser()) Int.MAX_VALUE
        else maxOf(0, FREE_DOWNLOAD_LIMIT - getDownloadCount())

    fun canDownload(): Boolean = isProUser() || getDownloadCount() < FREE_DOWNLOAD_LIMIT
    fun isShieldAccessible(): Boolean = canDownload()

    fun resolveHostname(hostname: String): List<InetAddress> {
        if (!isDohEnabled()) {
            return try {
                InetAddress.getAllByName(hostname).toList()
            } catch (e: UnknownHostException) {
                Log.e(TAG, "System DNS failed for $hostname: ${e.message}")
                emptyList()
            }
        }
        return try {
            val dnsResolver = buildDnsResolver(activeProvider, useFragmentation = false)
            dnsResolver.lookup(hostname)
        } catch (e: Exception) {
            Log.e(TAG, "DoH resolution failed for $hostname: ${e.message}")
            try {
                InetAddress.getAllByName(hostname).toList()
            } catch (fallback: UnknownHostException) {
                emptyList()
            }
        }
    }    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildAndTestDohClient(
        provider: DnsProvider,
        useFragmentation: Boolean
    ): OkHttpClient {
        val client = buildDohClient(provider, useFragmentation)
        val probeOk = testDohConnection(client, provider)
        if (!probeOk) {
            client.connectionPool.evictAll()
            throw IllegalStateException("DoH probe failed for ${provider.displayName} (fragment=$useFragmentation)")
        }
        return client
    }

    private fun setActiveClient(client: OkHttpClient, provider: DnsProvider) {
        val old = activeDohClient
        activeDohClient = client
        activeProvider = provider
        old?.connectionPool?.evictAll()
    }

    private fun buildDohClient(
        provider: DnsProvider,
        useFragmentation: Boolean
    ): OkHttpClient {
        val dns = buildDnsResolver(provider, useFragmentation)

        val builder = bootstrapClient.newBuilder()
            .dns(dns)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (useFragmentation) {
            builder.socketFactory(fragmentingSocketFactory)
        }

        return builder.build()
    }

    private fun buildDnsResolver(
        provider: DnsProvider,
        useFragmentation: Boolean
    ): DnsOverHttps {
        val httpUrl = provider.dohUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid DoH URL: ${provider.dohUrl}")

        val bootstrapHosts: Array<InetAddress> = provider.bootstrapIps
            .map { InetAddress.getByName(it) }
            .toTypedArray()

        val bootstrap = if (useFragmentation) {
            bootstrapClient.newBuilder()
                .socketFactory(fragmentingSocketFactory)
                .build()
        } else {
            bootstrapClient
        }

        return DnsOverHttps.Builder()
            .client(bootstrap)
            .url(httpUrl)
            .bootstrapDnsHosts(*bootstrapHosts)
            .includeIPv6(true)
            .post(true)
            .build()
    }

    private fun testDohConnection(client: OkHttpClient, provider: DnsProvider): Boolean {
        return try {
            val request = okhttp3.Request.Builder()
                .url(provider.dohUrl)
                .head()
                .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "DoH probe for ${provider.displayName}: HTTP ${response.code}")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "DoH probe failed for ${provider.displayName}: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    private fun persistShieldState(enabled: Boolean, provider: DnsProvider) {
        prefs.edit()
            .putBoolean(KEY_SHIELD_ENABLED, enabled)
            .putString(KEY_DNS_PROVIDER, provider.name)
            .apply()
    }

    private fun getSavedProvider(): DnsProvider {
        val saved = prefs.getString(KEY_DNS_PROVIDER, DnsProvider.CLOUDFLARE.name)
        return try {
            DnsProvider.valueOf(saved ?: DnsProvider.CLOUDFLARE.name)
        } catch (_: IllegalArgumentException) {
            DnsProvider.CLOUDFLARE
        }
    }

    private fun ensureDownloadCountInitialized() {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putInt(KEY_DOWNLOAD_COUNT, 0)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
            Log.d(TAG, "Download counter initialized to 0 (fresh install)")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TLS SNI FRAGMENTATION  (DPI / connection-reset evasion)
    // ─────────────────────────────────────────────────────────────────────────

    private inner class TlsFragmentingSocketFactory : SocketFactory() {

        private val delegate: SocketFactory = SocketFactory.getDefault()

        override fun createSocket(): Socket =
            FragmentingSocket(delegate.createSocket())

        override fun createSocket(host: String?, port: Int): Socket =
            FragmentingSocket(delegate.createSocket(host, port))

        override fun createSocket(
            host: String?, port: Int,
            localHost: InetAddress?, localPort: Int
        ): Socket = FragmentingSocket(delegate.createSocket(host, port, localHost, localPort))

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            FragmentingSocket(delegate.createSocket(host, port))

        override fun createSocket(
            address: InetAddress?, port: Int,
            localAddress: InetAddress?, localPort: Int
        ): Socket = FragmentingSocket(delegate.createSocket(address, port, localAddress, localPort))

        private inner class FragmentingSocket(private val delegate: Socket) : Socket() {

            private var outputWrapper: FragmentingOutputStream? = null

            override fun getOutputStream(): OutputStream {
                if (outputWrapper == null) {
                    outputWrapper = FragmentingOutputStream(delegate.outputStream)
                }
                return outputWrapper!!
            }

            override fun getInputStream(): InputStream = delegate.inputStream
            override fun connect(endpoint: SocketAddress?) = delegate.connect(endpoint)
            override fun connect(endpoint: SocketAddress?, timeout: Int) = delegate.connect(endpoint, timeout)
            override fun bind(bindpoint: SocketAddress?) = delegate.bind(bindpoint)
            override fun getInetAddress(): InetAddress = delegate.inetAddress
            override fun getLocalAddress(): InetAddress = delegate.localAddress
            override fun getPort(): Int = delegate.port
            override fun getLocalPort(): Int = delegate.localPort
            override fun getRemoteSocketAddress(): SocketAddress = delegate.remoteSocketAddress
            override fun getLocalSocketAddress(): SocketAddress = delegate.localSocketAddress
            override fun getChannel() = delegate.channel
            override fun setTcpNoDelay(on: Boolean) = delegate.setTcpNoDelay(on)
            override fun getTcpNoDelay(): Boolean = delegate.tcpNoDelay
            override fun setSoLinger(on: Boolean, linger: Int) = delegate.setSoLinger(on, linger)
            override fun getSoLinger(): Int = delegate.soLinger
            override fun sendUrgentData(data: Int) = delegate.sendUrgentData(data)
            override fun setOOBInline(on: Boolean) = delegate.setOOBInline(on)
            override fun getOOBInline(): Boolean = delegate.oobInline
            override fun setSoTimeout(timeout: Int) = delegate.setSoTimeout(timeout)
            override fun getSoTimeout(): Int = delegate.soTimeout
            override fun setSendBufferSize(size: Int) = delegate.setSendBufferSize(size)
            override fun getSendBufferSize(): Int = delegate.sendBufferSize
            override fun setReceiveBufferSize(size: Int) = delegate.setReceiveBufferSize(size)
            override fun getReceiveBufferSize(): Int = delegate.receiveBufferSize
            override fun setKeepAlive(on: Boolean) = delegate.setKeepAlive(on)
            override fun getKeepAlive(): Boolean = delegate.keepAlive
            override fun setTrafficClass(tc: Int) = delegate.setTrafficClass(tc)
            override fun getTrafficClass(): Int = delegate.trafficClass
            override fun setReuseAddress(on: Boolean) = delegate.setReuseAddress(on)
            override fun getReuseAddress(): Boolean = delegate.reuseAddress
            override fun close() = delegate.close()
            override fun isClosed(): Boolean = delegate.isClosed
            override fun isConnected(): Boolean = delegate.isConnected
            override fun isBound(): Boolean = delegate.isBound
            override fun isInputShutdown(): Boolean = delegate.isInputShutdown
            override fun isOutputShutdown(): Boolean = delegate.isOutputShutdown
            override fun shutdownInput() = delegate.shutdownInput()
            override fun shutdownOutput() = delegate.shutdownOutput()
            override fun toString(): String = delegate.toString()
        }

        private inner class FragmentingOutputStream(
            private val delegate: OutputStream
        ) : OutputStream() {

            private var firstWrite = true

            override fun write(b: Int) {
                if (firstWrite) {
                    firstWrite = false
                    delegate.write(b)
                    delegate.flush()
                    Thread.sleep(5)
                } else {
                    delegate.write(b)
                }
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (!firstWrite || len < 80) {
                    delegate.write(b, off, len)
                    return
                }

                firstWrite = false
                val splitAt = 50.coerceAtMost(len - 1)

                delegate.write(b, off, splitAt)
                delegate.flush()
                Thread.sleep(10)

                delegate.write(b, off + splitAt, len - splitAt)
            }

            override fun flush() = delegate.flush()
            override fun close() = delegate.close()
        }
    }
}

    
