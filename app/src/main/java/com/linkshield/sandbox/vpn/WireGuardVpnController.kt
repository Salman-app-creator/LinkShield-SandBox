package com.linkshield.sandbox.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WireGuardVpnController(
    context: Context
) {

    companion object {
        const val VPN_PERMISSION_REQUEST = 9102
    }

    private val appContext =
        context.applicationContext

    private val backend =
        GoBackend(appContext)

    private val tunnel =
        LinkShieldTunnel()

    suspend fun connect(
        configText: String
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(configText.isNotBlank()) {
                    "WireGuard configuration is empty"
                }

                val config =
                    Config.parse(
                        configText.byteInputStream()
                    )

                val state =
                    backend.setState(
                        tunnel,
                        Tunnel.State.UP,
                        config
                    )

                state == Tunnel.State.UP
            }
        }
        suspend fun disconnect(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val state =
                    backend.setState(
                        tunnel,
                        Tunnel.State.DOWN,
                        null
                    )

                state == Tunnel.State.DOWN
            }
        }

    fun isConnected(): Boolean {
        return runCatching {
            backend.getState(
                tunnel
            ) == Tunnel.State.UP
        }.getOrDefault(false)
    }

    fun prepareVpn(
        activity: Activity
    ): Boolean {

        val intent =
            VpnService.prepare(activity)

        if (intent != null) {
            activity.startActivityForResult(
                intent,
                VPN_PERMISSION_REQUEST
            )

            return false
        }

        return true
    }

    fun getBackendVersion(): String {
        return runCatching {
            backend.getVersion()
        }.getOrDefault("unknown")
    }
    private class LinkShieldTunnel :
        Tunnel {

        override fun getName(): String {
            return "LinkShield"
        }

        override fun onStateChange(
            newState: Tunnel.State
        ) {
            // WireGuard backend owns tunnel state.
        }
    }
}
