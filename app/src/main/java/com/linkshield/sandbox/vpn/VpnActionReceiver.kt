package com.linkshield.sandbox.vpn

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISCONNECT) return

        val pendingResult = goAsync()
        scope.launch {
            runCatching {
                val manager = WireGuardVpnManager(context.applicationContext)
                manager.disconnect()
            }
            VpnNotificationHelper.cancel(context)
            pendingResult.finish()
        }
    }
}
