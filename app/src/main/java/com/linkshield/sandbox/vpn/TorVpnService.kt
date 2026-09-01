// app/src/main/java/com/linkshield/sandbox/vpn/TorVpnService.kt
package com.linkshield.sandbox.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.linkshield.sandbox.MainActivity
import com.linkshield.sandbox.R
import org.torproject.jni.TorService

class TorVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.linkshield.sandbox.TOR_START"
        const val ACTION_STOP  = "com.linkshield.sandbox.TOR_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tor_vpn_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("🔄 VPN Connecting..."))
                startTor()
            }
            ACTION_STOP -> {
                stopTor()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTor()
        TorVpnManager.setConnected(false)
        super.onDestroy()
    }

    private fun startTor() {
        try {
            val torIntent = Intent(this, TorService::class.java)
            startService(torIntent)
            TorVpnManager.setConnected(true)
            updateNotification("🔒 VPN Connected")
        } catch (e: Exception) {
            updateNotification("❌ VPN Error: ${e.message}")
            TorVpnManager.setConnected(false)
        }
    }

    private fun stopTor() {
        try {
            val torIntent = Intent(this, TorService::class.java)
            stopService(torIntent)
        } catch (e: Exception) {
            android.util.Log.e("TorVPN", "Stop error: ${e.message}")
        }
        TorVpnManager.setConnected(false)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Tor VPN", NotificationManager.IMPORTANCE_LOW
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
