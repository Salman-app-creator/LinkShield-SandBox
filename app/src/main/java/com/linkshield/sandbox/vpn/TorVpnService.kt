package com.linkshield.sandbox.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.linkshield.sandbox.MainActivity
import com.linkshield.sandbox.R
import org.torproject.jni.TorService
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class TorVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.linkshield.sandbox.TOR_START"
        const val ACTION_STOP  = "com.linkshield.sandbox.TOR_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID      = "tor_vpn_channel"
        private const val TAG             = "TorVpnService"

        private const val VPN_ADDRESS     = "10.0.0.2"
        private const val VPN_DNS         = "1.1.1.1"
        private const val TOR_SOCKS_PORT  = 9050
        private const val BOOTSTRAP_TIMEOUT_MS = 60000L
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2socksProcess: Process? = null
    private var torServiceIntent: Intent? = null
    @Volatile private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (isRunning) {
                    Log.w(TAG, "VPN already active — ignoring duplicate start")
                    return START_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification("Initializing Tor..."))
                Thread { startVpn() }.start()
            }
            ACTION_STOP -> {
                Thread { stopVpn() }.start()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        try {
            isRunning = true
            TorVpnManager.setConnected(false)

            // 1. Start Tor daemon
            updateNotification("Starting Tor daemon...")
            torServiceIntent = Intent(this, TorService::class.java)
            startService(torServiceIntent)

            // 2. Wait until Tor SOCKS port is open
            updateNotification("Bootstrapping Tor network...")
            if (!waitForTorBootstrap()) {
                throw IOException("Tor bootstrap timed out. Check internet or try bridges.")
            }

            // 3. Build VPN TUN interface
            updateNotification("Creating secure tunnel...")
            vpnInterface = buildVpnInterface()
                ?: throw IOException("Android rejected VPN permission or interface build failed.")

            // 4. Route TUN -> Tor SOCKS via tun2socks
            updateNotification("Activating packet routing...")
            startTun2Socks(vpnInterface!!.fd)

            // 5. Mark global state connected
            TorVpnManager.setConnected(true)
            updateNotification("🔒 VPN Connected — All traffic routed through Tor")

        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed", e)
            TorVpnManager.setConnected(false)
            updateNotification("❌ VPN Error: ${e.message}")
            stopVpnInternal()
        }
    }

    private fun waitForTorBootstrap(): Boolean {
        val deadline = System.currentTimeMillis() + BOOTSTRAP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isLocalPortOpen("127.0.0.1", TOR_SOCKS_PORT)) {
                Log.i(TAG, "Tor SOCKS proxy ready on port $TOR_SOCKS_PORT")
                Thread.sleep(1500) // small buffer for full bootstrap
                return true
            }
            Thread.sleep(800)
        }
        return false
    }

    private fun isLocalPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { s -> s.connect(InetSocketAddress(host, port), 1200); true }
        } catch (_: IOException) { false }
    }

    private fun buildVpnInterface(): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setSession("LinkShield Tor")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)      // Capture ALL IPv4
                .addRoute("::", 0)           // Capture ALL IPv6
                .addDnsServer(VPN_DNS)
                .setMtu(1500)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.allowFamily(android.system.OsConstants.AF_INET)
                builder.allowFamily(android.system.OsConstants.AF_INET6)
            }
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "VPN Builder failed", e)
            null
        }
    }

    private fun startTun2Socks(tunFd: Int) {
        val nativeDir = applicationInfo.nativeLibraryDir
        val tun2socks = File(nativeDir, "libtun2socks.so")

        if (!tun2socks.exists()) {
            val msg = "libtun2socks.so not found in ${nativeDir}. VPN interface created but packets cannot be routed. Please add tun2socks binary to jniLibs."
            Log.e(TAG, msg)
            updateNotification("⚠️ $msg")
            // Don't throw — let Tor keep running as local proxy at least
            return
        }

        val cmd = arrayOf(
            tun2socks.absolutePath,
            "--tunfd", tunFd.toString(),
            "--tunmtu", "1500",
            "--inet4-address", "$VPN_ADDRESS/32",
            "--inet6-address", "fd00::1/128",
            "--socks-server-addr", "127.0.0.1:$TOR_SOCKS_PORT",
            "--udprelay",
            "--netif-ipaddr", VPN_ADDRESS,
            "--netif-netmask", "255.255.255.255"
        )

        val pb = ProcessBuilder(*cmd)
        pb.redirectErrorStream(true)
        tun2socksProcess = pb.start()

        // Monitor tun2socks death
        Thread {
            try {
                val code = tun2socksProcess?.waitFor()
                Log.w(TAG, "tun2socks exited: $code")
                if (isRunning) {
                    updateNotification("⚠️ Tunnel dropped — reconnecting...")
                    restartVpn()
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.start()
    }

    private fun restartVpn() {
        stopVpnInternal()
        startVpn()
    }

    private fun stopVpn() {
        stopVpnInternal()
        stopForeground(true)
        stopSelf()
    }

    private fun stopVpnInternal() {
        isRunning = false
        TorVpnManager.setConnected(false)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tun2socksProcess?.destroyForcibly()
            } else {
                tun2socksProcess?.destroy()
            }
        } catch (e: Exception) { Log.w(TAG, "tun2socks stop error", e) }
        tun2socksProcess = null

        try { vpnInterface?.close() } catch (e: Exception) { Log.w(TAG, "TUN close error", e) }
        vpnInterface = null

        try {
            torServiceIntent?.let { stopService(it); torServiceIntent = null }
        } catch (e: Exception) { Log.w(TAG, "Tor stop error", e) }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tor VPN Tunnel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "LinkShield secure tunnel status"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LinkShield Secure Tunnel")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
