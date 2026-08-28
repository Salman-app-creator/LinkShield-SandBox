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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.linkshield.sandbox.MainActivity
import com.linkshield.sandbox.R
import ca.psiphon.PsiphonTunnel
import ca.psiphon.PsiphonTunnel.HostService
import org.json.JSONObject

class PsiphonVpnService : VpnService(), HostService {

    private var psiphonTunnel: PsiphonTunnel? = null
    private var isTunnelRunning = false

    companion object {
        const val ACTION_START = "com.linkshield.sandbox.vpn.START"
        const val ACTION_STOP = "com.linkshield.sandbox.vpn.STOP"
        private const val CHANNEL_ID = "PsiphonVpnChannel"
        private const val NOTIFICATION_ID = 1001

        private const val CONFIG_JSON = """{
            "PropagationChannelId":"FFFFFFFFFFFFFFFF",
            "SponsorId":"FFFFFFFFFFFFFFFF",
            "DisableLocalSocksProxy":true,
            "DisableLocalHTTPProxy":true,
            "TunnelWholeDevice":true
        }"""
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        psiphonTunnel = PsiphonTunnel.newPsiphonTunnel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isTunnelRunning) return
        
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to Psiphon VPN..."))
        isTunnelRunning = true

        try {
            psiphonTunnel?.startTunneling("")
        } catch (e: Exception) {
            Log.e("PsiphonVpnService", "Error starting tunnel: ${e.message}")
            updateNotification("Failed to connect")
            stopVpn()
        }
    }

    private fun stopVpn() {
        if (!isTunnelRunning) return
        try {
            psiphonTunnel?.stop()
        } catch (e: Exception) {
            Log.e("PsiphonVpnService", "Error stopping tunnel: ${e.message}")
        } finally {
            isTunnelRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LinkShield VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LinkShield Sandbox VPN")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    // --- PsiphonTunnel.HostService Implementation ---
    override fun getAppName(): String = "LinkShield Sandbox"
    override fun getContext(): Context = applicationContext
    override fun getPsiphonConfig(): String = CONFIG_JSON

    override fun onConnected() {
        Log.i("PsiphonVpnService", "Psiphon VPN Connected successfully.")
        updateNotification("Connected & Protected")
    }

    override fun onConnecting() {
        updateNotification("Connecting to Psiphon...")
    }

    override fun onStartedWaitingForNetwork() {
        updateNotification("Waiting for network...")
    }

    override fun onStopped() {
        updateNotification("Disconnected")
    }
}
