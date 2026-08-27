package com.linkshield.sandbox.vpn

// REPO PATH: app/src/main/java/com/linkshield/sandbox/vpn/PsiphonVpnService.kt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import ca.psiphon.PsiphonTunnel
import com.linkshield.sandbox.MainActivity
import com.linkshield.sandbox.R
import org.json.JSONObject

class PsiphonVpnService : VpnService(), PsiphonTunnel.HostService {

    companion object {
        const val ACTION_START = "com.linkshield.sandbox.PSIPHON_START"
        const val ACTION_STOP  = "com.linkshield.sandbox.PSIPHON_STOP"
        private const val TAG  = "PsiphonVpnService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "psiphon_vpn_channel"
    }

    private lateinit var psiphonTunnel: PsiphonTunnel

    override fun onCreate() {
        super.onCreate()
        psiphonTunnel = PsiphonTunnel.newPsiphonTunnel(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startPsiphon()
            ACTION_STOP  -> stopPsiphon()
        }
        return START_STICKY
    }

    private fun startPsiphon() {
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
            psiphonTunnel.startRouting()
            psiphonTunnel.startTunneling(buildPsiphonConfig())
            Log.d(TAG, "Psiphon tunnel started")
        } catch (e: Exception) {
            Log.e(TAG, "Psiphon start failed: ${e.message}")
            stopSelf()
        }
    }

    private fun stopPsiphon() {
        try {
            psiphonTunnel.stop()
            Log.d(TAG, "Psiphon tunnel stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Psiphon stop failed: ${e.message}")
        }
        stopForeground(true)
        stopSelf()
    }

    /**
     * Minimal Psiphon config — no account needed.
     * PropagationChannelId aur SponsorId yeh default free values hain.
     */
    private fun buildPsiphonConfig(): String {
        return JSONObject().apply {
            put("PropagationChannelId", "FFFFFFFFFFFFFFFF")
            put("SponsorId", "FFFFFFFFFFFFFFFF")
            put("DisableLocalSocksProxy", true)
            put("DisableLocalHTTPProxy", true)
            put("TunnelWholeDevice", true)
        }.toString()
    }

    // ── PsiphonTunnel.HostService callbacks ──────────────────────────────

    override fun getContext(): Context = this

    override fun getPsiphonConfig(): String = buildPsiphonConfig()

    override fun onDiagnosticMessage(message: String?) {
        Log.d(TAG, "Psiphon: $message")
    }

    override fun onConnecting() {
        updateNotification("Connecting to Psiphon...")
        Log.d(TAG, "Psiphon connecting")
    }

    override fun onConnected() {
        updateNotification("🔒 Psiphon Connected")
        Log.d(TAG, "Psiphon connected")
    }

    override fun onHomepage(url: String?) {}
    override fun onVersionInfo(s: String?) {}
    override fun onClientUpgradeDownloaded(filename: String?) {}
    override fun onClientIsLatestVersion() {}
    override fun onListeningHttpProxyPort(port: Int) {}
    override fun onListeningSocksProxyPort(port: Int) {}
    override fun onUpstreamProxyError(message: String?) {}
    override fun onAvailableEgressRegions(regions: MutableList<String>?) {}
    override fun onSocksProxyPortInUse(port: Int) {}
    override fun onHttpProxyPortInUse(port: Int) {}
    override fun onStartedWaitingForNetworkConnectivity() {}
    override fun onStoppedWaitingForNetworkConnectivity() {}
    override fun onActiveAuthorizationIDs(authorizations: MutableList<String>?) {}
    override fun onExiting() {}
    override fun onServerAlert(reason: String?, subject: String?, actionURLs: MutableList<String>?) {}
    override fun onApplicationParameters(parameters: Any?) {}
    override fun onBytesTransferred(sent: Long, received: Long) {}
    override fun onActiveSpeedTestBytes(bytes: Long, durationMS: Long) {}
    override fun onInproxyProxyActivity(connectingClients: Int, connectedClients: Int, bytesUp: Long, bytesDown: Long) {}

    // ── VpnService builder ───────────────────────────────────────────────

    override fun newVpnServiceBuilder(): VpnService.Builder = Builder()

    // ── Notification helpers ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Psiphon VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "LinkShield Psiphon VPN status" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LinkShield VPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
