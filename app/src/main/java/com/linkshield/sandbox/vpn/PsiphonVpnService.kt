// app/src/main/java/com/linkshield/sandbox/vpn/PsiphonVpnService.kt
package com.linkshield.sandbox.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import ca.psiphon.PsiphonTunnel
import com.linkshield.sandbox.MainActivity
import com.linkshield.sandbox.R
import org.json.JSONObject

class PsiphonVpnService : VpnService(), PsiphonTunnel.HostService {

    companion object {
        const val ACTION_START = "com.linkshield.sandbox.PSIPHON_START"
        const val ACTION_STOP  = "com.linkshield.sandbox.PSIPHON_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "psiphon_vpn_channel"
    }

    private var psiphonTunnel: PsiphonTunnel? = null
    private var tunnelThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        psiphonTunnel = PsiphonTunnel.newPsiphonTunnel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("🔄 VPN Connecting..."))
                startTunnel()
            }
            ACTION_STOP -> {
                stopTunnel()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun startTunnel() {
        tunnelThread = Thread {
            try {
                psiphonTunnel?.startTunneling("")
            } catch (e: Exception) {
                updateNotification("❌ VPN Error: ${e.message}")
            }
        }.also { it.start() }
    }

    private fun stopTunnel() {
        psiphonTunnel?.stop()
        tunnelThread?.interrupt()
        tunnelThread = null
    }

    // ── PsiphonTunnel.HostService callbacks ──────────────────────────────

    override fun getContext(): Context = this

    override fun getPsiphonConfig(): String {
        return JSONObject().apply {
            put("PropagationChannelId", "FFFFFFFFFFFFFFFF")
            put("SponsorId",            "FFFFFFFFFFFFFFFF")
            put("DisableLocalSocksProxy",  false)
            put("DisableLocalHTTPProxy",   false)
            put("LocalSocksProxyPort",  1080)
            put("LocalHttpProxyPort",   8080)
            put("EmitDiagnosticNotices", true)
            put("EgressRegion", "")
        }.toString()
    }

    override fun onDiagnosticMessage(message: String) {
        android.util.Log.d("PsiphonVPN", message)
    }

    override fun onAvailableEgressRegions(regions: List<String>) {}
    override fun onSocksProxyPortInUse(port: Int) {}
    override fun onHttpProxyPortInUse(port: Int) {}
    override fun onListeningSocksProxyPort(port: Int) {}
    override fun onListeningHttpProxyPort(port: Int) {}
    override fun onUpstreamProxyError(message: String) {}
    override fun onConnecting() { updateNotification("🔄 VPN Connecting...") }
    override fun onConnected() { updateNotification("🔒 VPN Connected") }
    override fun onHomepage(url: String) {}
    override fun onClientRegion(region: String) {}
    override fun onClientAddress(address: String) {}
    override fun onUntunneledAddress(address: String) {}
    override fun onBytesTransferred(sent: Long, received: Long) {}
    override fun onStartedWaitingForNetworkConnectivity() { updateNotification("⏳ Waiting for network...") }
    override fun onActiveAuthorizationIDs(authorizationIds: List<String>) {}
    override fun onTrafficRateLimits(upstreamBytesPerSecond: Long, downstreamBytesPerSecond: Long) {}
    override fun onApplicationParameters(parameters: Any) {}
    override fun onServerAlert(reason: String, subject: String, actionUrls: List<String>) {}
    override fun onExiting() {}

    // ── Notification helpers ──────────────────────────────────────────────

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Psiphon VPN", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "LinkShield VPN status" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LinkShield VPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
