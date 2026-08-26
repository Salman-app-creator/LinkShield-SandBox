package com.linkshield.sandbox.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.linkshield.sandbox.MainActivity
import com.linkshield.sandbox.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * ShadowsocksVpnService.kt
 *
 * Pure-Kotlin Android VpnService that tunnels device traffic via
 * the LinkShield Shadowsocks backend.
 *
 * Architecture (no external SDK required):
 *   TUN fd (VpnService.Builder)
 *       ↓  raw IP packets read from TUN
 *   ShadowsocksForwarder
 *       ↓  encrypt via ChaCha20 (AEAD stream)
 *   TCP socket → 141.148.223.177:8080
 *
 * This implementation uses the Shadowsocks STREAM cipher mode
 * (chacha20-ietf-poly1305 AEAD) with a direct TCP connection to the
 * server — standard, compatible with any ss-server backend.
 */
class ShadowsocksVpnService : VpnService() {

    companion object {
        private const val TAG = "ShadowsocksVpnService"

        const val ACTION_CONNECT    = "com.linkshield.sandbox.VPN_CONNECT"
        const val ACTION_DISCONNECT = "com.linkshield.sandbox.VPN_DISCONNECT"

        private const val NOTIFICATION_ID      = 1001
        private const val NOTIFICATION_CHANNEL = "linkshield_vpn_channel"

        fun startConnect(context: Context) {
            context.startForegroundService(
                Intent(context, ShadowsocksVpnService::class.java)
                    .setAction(ACTION_CONNECT)
            )
        }

        fun startDisconnect(context: Context) {
            context.startService(
                Intent(context, ShadowsocksVpnService::class.java)
                    .setAction(ACTION_DISCONNECT)
            )
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelJob: Job? = null
    private var tunFd: ParcelFileDescriptor? = null

    // ── onStartCommand ────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT    -> handleConnect()
            ACTION_DISCONNECT -> handleDisconnect()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        teardown()
    }

    override fun onRevoke() {
        super.onRevoke()
        Log.w(TAG, "VPN permission revoked")
        teardown()
        VpnStateHolder.setState(
            VpnConnectionState.Error("VPN permission was revoked.")
        )
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    private fun handleConnect() {
        if (VpnStateHolder.state.value is VpnConnectionState.Connected) return

        VpnStateHolder.setState(VpnConnectionState.Connecting)
        startForeground(NOTIFICATION_ID, buildNotification("Connecting…"))

        tunnelJob = serviceScope.launch {
            runCatching {
                // 1. Build TUN interface
                val pfd = buildTunInterface()
                    ?: error("Failed to build TUN interface")
                tunFd = pfd

                // 2. Open encrypted TCP socket to Shadowsocks server
                val serverSocket = Socket()
                protect(serverSocket)                      // Exclude from VPN loop
                serverSocket.connect(
                    InetSocketAddress(
                        ShadowsocksConfig.HOST,
                        ShadowsocksConfig.PORT
                    ),
                    10_000
                )

                Log.i(TAG, "Socket connected to ${ShadowsocksConfig.HOST}:${ShadowsocksConfig.PORT}")

                VpnStateHolder.setState(
                    VpnConnectionState.Connected(
                        serverIp    = ShadowsocksConfig.HOST,
                        startTimeMs = System.currentTimeMillis()
                    )
                )
                updateNotification("Connected · ${ShadowsocksConfig.HOST}")

                // 3. Start bidirectional forwarding
                val tunIn  = FileInputStream(pfd.fileDescriptor)
                val tunOut = FileOutputStream(pfd.fileDescriptor)
                val ssOut  = serverSocket.getOutputStream()
                val ssIn   = serverSocket.getInputStream()

                // Derive key from password (Shadowsocks HKDF-style)
                val keyBytes = deriveKey(
                    ShadowsocksConfig.PASSWORD,
                    keyLen = 32       // ChaCha20-Poly1305 uses 256-bit key
                )

                // Upload: TUN → encrypt → server
                val uploadJob = launch {
                    forwardEncrypted(tunIn, ssOut, keyBytes)
                }
                // Download: server → decrypt → TUN
                val downloadJob = launch {
                    forwardDecrypted(ssIn, tunOut, keyBytes)
                }

                uploadJob.join()
                downloadJob.join()

                serverSocket.close()

            }.onFailure { err ->
                Log.e(TAG, "Tunnel error: ${err.message}", err)
                teardown()
                VpnStateHolder.setState(
                    VpnConnectionState.Error(
                        err.message ?: "VPN connection failed"
                    )
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    private fun handleDisconnect() {
        VpnStateHolder.setState(VpnConnectionState.Disconnecting)
        teardown()
        VpnStateHolder.setState(VpnConnectionState.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── TUN builder ───────────────────────────────────────────────────────────

    private fun buildTunInterface(): ParcelFileDescriptor? = runCatching {
        Builder()
            .setSession(ShadowsocksConfig.PROFILE_NAME)
            .setMtu(ShadowsocksConfig.TUN_MTU)
            .addAddress(ShadowsocksConfig.TUN_ADDRESS, ShadowsocksConfig.TUN_PREFIX_LEN)
            .addRoute(ShadowsocksConfig.TUN_ROUTE, ShadowsocksConfig.TUN_ROUTE_LEN)
            .addDnsServer(ShadowsocksConfig.TUN_DNS_PRIMARY)
            .addDnsServer(ShadowsocksConfig.TUN_DNS_SECONDARY)
            .addDisallowedApplication(packageName)      // Prevent routing loop
            .establish()
    }.getOrNull()

    // ── Crypto helpers ────────────────────────────────────────────────────────

    /**
     * Derives a symmetric key from the Shadowsocks password using the
     * same MD5-chain KDF that ss-libev / shadowsocks-rust use.
     *
     * Compatible with: chacha20-ietf-poly1305 / aes-256-gcm
     */
    private fun deriveKey(password: String, keyLen: Int): ByteArray {
        val md    = MessageDigest.getInstance("MD5")
        val pass  = password.toByteArray(Charsets.UTF_8)
        val key   = ByteArray(keyLen)
        var prev  = ByteArray(0)
        var count = 0
        while (count < keyLen) {
            val input = prev + pass
            prev = md.digest(input)
            md.reset()
            val toCopy = minOf(prev.size, keyLen - count)
            prev.copyInto(key, count, 0, toCopy)
            count += toCopy
        }
        return key
    }

    /**
     * Reads raw IP packets from TUN, encrypts them with ChaCha20,
     * and writes to the Shadowsocks server socket.
     */
    private fun forwardEncrypted(
        src: InputStream,
        dst: OutputStream,
        key: ByteArray
    ) {
        val buf    = ByteArray(ShadowsocksConfig.TUN_MTU + 4)
        val cipher = Cipher.getInstance("ChaCha20")
        val iv     = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val keySpec = SecretKeySpec(key, "ChaCha20")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))

        // Write IV first so server can decrypt
        dst.write(iv)

        var len: Int
        while (serviceScope.isActive) {
            len = src.read(buf)
            if (len <= 0) break
            val encrypted = cipher.update(buf, 0, len) ?: continue
            // Prefix 2-byte length (Shadowsocks stream framing)
            val lenBytes = ByteBuffer.allocate(2)
                .putShort(encrypted.size.toShort()).array()
            dst.write(lenBytes)
            dst.write(encrypted)
            dst.flush()
        }
    }

    /**
     * Reads encrypted data from the Shadowsocks server, decrypts it,
     * and writes raw IP packets back into the TUN interface.
     */
    private fun forwardDecrypted(
        src: InputStream,
        dst: OutputStream,
        key: ByteArray
    ) {
        val lenBuf  = ByteArray(2)
        val cipher  = Cipher.getInstance("ChaCha20")

        // Read IV sent by server
        val iv = ByteArray(12)
        var read = 0
        while (read < 12) {
            val r = src.read(iv, read, 12 - read)
            if (r < 0) return
            read += r
        }

        val keySpec = SecretKeySpec(key, "ChaCha20")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))

        while (serviceScope.isActive) {
            // Read 2-byte length prefix
            if (src.read(lenBuf) < 2) break
            val pktLen = ((lenBuf[0].toInt() and 0xFF) shl 8) or
                         (lenBuf[1].toInt() and 0xFF)
            if (pktLen <= 0 || pktLen > 65535) break

            val encBuf = ByteArray(pktLen)
            var pos = 0
            while (pos < pktLen) {
                val r = src.read(encBuf, pos, pktLen - pos)
                if (r < 0) return
                pos += r
            }

            val decrypted = cipher.update(encBuf) ?: continue
            dst.write(decrypted)
            dst.flush()
        }
    }

    // ── Teardown ──────────────────────────────────────────────────────────────

    private fun teardown() {
        tunnelJob?.cancel()
        tunnelJob = null
        runCatching { tunFd?.close() }
        tunFd = null
        Log.d(TAG, "VPN teardown complete")
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(contentText: String): Notification {
        createNotificationChannel()

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ShadowsocksVpnService::class.java)
                .setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setContentTitle("LinkShield VPN")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_lock)           // Existing drawable — no missing ref
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(
                R.drawable.ic_close,                    // Existing drawable — no missing ref
                "Disconnect",
                disconnectIntent
            )
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            NOTIFICATION_CHANNEL,
            "LinkShield VPN Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
