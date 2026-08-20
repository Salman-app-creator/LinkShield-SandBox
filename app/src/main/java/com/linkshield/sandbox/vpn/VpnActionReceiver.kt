package com.linkshield.sandbox.vpn

// ─────────────────────────────────────────────────────────────────────────────
// VpnActionReceiver.kt
//
// BroadcastReceiver that handles the "Disconnect" action button in the
// VPN foreground notification.
//
// Declared in AndroidManifest.xml (see patch below).
// ─────────────────────────────────────────────────────────────────────────────

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VpnActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISCONNECT = "com.linkshield.sandbox.VPN_DISCONNECT"
    }

    // Use a standalone scope — receiver lifecycle is very short
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISCONNECT) return

        // Disconnect in background so the receiver returns quickly
        scope.launch {
            runCatching {
                val manager = WireGuardVpnManager(context.applicationContext)
                manager.disconnect()
            }
            // Cancel notification regardless of result
            VpnNotificationHelper.cancel(context)
        }
    }
}
