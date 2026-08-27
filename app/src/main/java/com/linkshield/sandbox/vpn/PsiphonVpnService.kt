package com.linkshield.sandbox.vpn

// REPO PATH: app/src/main/java/com/linkshield/sandbox/vpn/PsiphonVpnService.kt
//
// NOTE: Yeh abhi ek stub hai. PC pe Psiphon SDK (.aar) add karne ke baad
// is file ko full implementation se replace kar denge.

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

class PsiphonVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.linkshield.sandbox.PSIPHON_START"
        const val ACTION_STOP  = "com.linkshield.sandbox.PSIPHON_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "psiphon_vpn_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("🔒 VPN Active"))
            }
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Psiphon VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "LinkShield VPN status" }
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
}
