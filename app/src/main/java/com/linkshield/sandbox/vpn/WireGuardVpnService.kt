package com.linkshield.sandbox.vpn

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.wireguard.android.backend.GoBackend

/**
 * Custom VpnService that wraps GoBackend.VpnService and immediately
 * promotes itself to a foreground service so Android 8+ does not kill it.
 */
class WireGuardVpnService : GoBackend.VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately so Android 8+ doesn't kill us
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VpnNotificationHelper.createChannel(this)
            startForeground(
                VpnNotificationHelper.NOTIFICATION_ID,
                VpnNotificationHelper.build(this, WireGuardVpnStatus.CONNECTED)
            )
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }
}
