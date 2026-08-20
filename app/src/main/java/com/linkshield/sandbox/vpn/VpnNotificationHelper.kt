package com.linkshield.sandbox.vpn

// ─────────────────────────────────────────────────────────────────────────────
// VpnNotificationHelper.kt
//
// Manages the persistent foreground-service notification shown while the
// WireGuard tunnel is active.
//
// Required because:
//   Android 8+   → any background service must call startForeground() within
//                   5 seconds or the system kills it.
//   Android 14+  → VPN foreground services need foregroundServiceType =
//                   "connectedDevice" AND FOREGROUND_SERVICE_CONNECTED_DEVICE
//                   permission (see AndroidManifest.xml patch below).
//   Android 15+  → Stricter foreground service lifecycle enforcement.
//
// Usage:
//   VpnNotificationHelper.createChannel(context)   ← call once in Application.onCreate()
//   VpnNotificationHelper.show(context, status)    ← call after tunnel UP
//   VpnNotificationHelper.cancel(context)          ← call after tunnel DOWN
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.linkshield.sandbox.MainActivity
import com.linkshield.sandbox.R

object VpnNotificationHelper {

    const val CHANNEL_ID      = "linkshield_vpn_tunnel"
    const val NOTIFICATION_ID = 1001

    private const val CHANNEL_NAME = "VPN Tunnel"
    private const val CHANNEL_DESC = "Shown while LinkShield VPN tunnel is active"

    // ── Channel (must be created before first notification) ──────────────────

    /**
     * Create the notification channel.
     * Call this in [LinkShieldApp.onCreate] — safe to call multiple times.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW      // silent — no sound/vibration
        ).apply {
            description           = CHANNEL_DESC
            setShowBadge(false)
            lockscreenVisibility  = Notification.VISIBILITY_PUBLIC
        }

        notificationManager(context).createNotificationChannel(channel)
    }

    // ── Build notification ────────────────────────────────────────────────────

    /**
     * Build (but do not show) the VPN status notification.
     * GoBackend calls this internally to attach the notification to the
     * VpnService foreground state.
     */
    fun build(
        context: Context,
        status: WireGuardVpnStatus = WireGuardVpnStatus.CONNECTED
    ): Notification {
        val (title, text, iconRes) = when (status) {
            WireGuardVpnStatus.CONNECTED      ->
                Triple("🛡 LinkShield VPN Active",
                       "Encrypted tunnel is protecting your connection",
                       android.R.drawable.ic_lock_idle_lock)

            WireGuardVpnStatus.CONNECTING,
            WireGuardVpnStatus.DISCONNECTING  ->
                Triple("LinkShield VPN",
                       "Updating tunnel…",
                       android.R.drawable.ic_menu_rotate)

            else ->
                Triple("LinkShield VPN",
                       "Tunnel inactive",
                       android.R.drawable.ic_lock_idle_lock)
        }

        // Tap notification → open app
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Disconnect" action button
        val disconnectIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(VpnActionReceiver.ACTION_DISCONNECT).apply {
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setOngoing(true)                   // non-dismissible while VPN is up
            .setOnlyAlertOnce(true)             // don't re-alert on update
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectIntent
            )
            .build()
    }

    // ── Show / cancel ─────────────────────────────────────────────────────────

    fun show(context: Context, status: WireGuardVpnStatus) {
        notificationManager(context)
            .notify(NOTIFICATION_ID, build(context, status))
    }

    fun cancel(context: Context) {
        notificationManager(context).cancel(NOTIFICATION_ID)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun notificationManager(context: Context): NotificationManager =
        context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
