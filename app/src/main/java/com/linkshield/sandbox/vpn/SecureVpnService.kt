package com.linkshield.sandbox.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SecureVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_CONNECT -> startVpn()
            ACTION_DISCONNECT -> stopVpn()
        }

        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        vpnInterface =
            Builder()
                .setSession("LinkShield Secure Network")
                .setMtu(DEFAULT_MTU)
                .addAddress(
                    VPN_ADDRESS,
                    VPN_PREFIX
                )
                .addRoute(
                    VPN_ROUTE,
                    VPN_ROUTE_PREFIX
                )
                .addDnsServer(
                    DNS_SERVER
                )
                .establish()

        if (vpnInterface == null) {
            stopSelf()
            return
        }

        // Remote tunnel transport will be attached here.
        // The VPN interface itself must not be advertised
        // as an active Internet tunnel until the remote
        // transport is connected.
    }

    private fun stopVpn() {
        runCatching {
            vpnInterface?.close()
        }

        vpnInterface = null

        stopSelf()
    }

    override fun onDestroy() {
        runCatching {
            vpnInterface?.close()
        }

        vpnInterface = null

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    companion object {
        const val ACTION_CONNECT =
            "com.linkshield.sandbox.vpn.CONNECT"

        const val ACTION_DISCONNECT =
            "com.linkshield.sandbox.vpn.DISCONNECT"

        private const val DEFAULT_MTU = 1500

        private const val VPN_ADDRESS =
            "10.8.0.2"

        private const val VPN_PREFIX = 24

        private const val VPN_ROUTE =
            "0.0.0.0"

        private const val VPN_ROUTE_PREFIX = 0

        private const val DNS_SERVER =
            "1.1.1.1"
    }
}
