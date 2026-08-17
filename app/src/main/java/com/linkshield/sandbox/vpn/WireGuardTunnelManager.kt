package com.linkshield.sandbox.vpn

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WireGuardTunnelManager(
    context: Context
) {

    private val backend: Backend =
        GoBackend(context.applicationContext)

    private val tunnel =
        LinkShieldTunnel()

    suspend fun connect(
        configText: String
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val config = Config.parse(
                configText.byteInputStream()
            )

            backend.setState(
                tunnel,
                Tunnel.State.UP,
                config
            )

            backend.getState(tunnel) ==
                Tunnel.State.UP
        }.getOrElse {
            false
        }
    }
    suspend fun disconnect(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                backend.setState(
                    tunnel,
                    Tunnel.State.DOWN,
                    null
                )

                backend.getState(tunnel) ==
                    Tunnel.State.DOWN
            }.getOrElse {
                false
            }
        }

    fun isConnected(): Boolean {
        return runCatching {
            backend.getState(tunnel) ==
                Tunnel.State.UP
        }.getOrDefault(false)
    }

    private class LinkShieldTunnel :
        Tunnel {

        override fun getName(): String {
            return "LinkShield"
        }

        override fun onStateChange(
            newState: Tunnel.State
        ) {
            // State is queried directly from Backend.
        }
        }
        suspend fun shutdown() {
        withContext(Dispatchers.IO) {
            runCatching {
                if (
                    backend.getState(tunnel) ==
                    Tunnel.State.UP
                ) {
                    backend.setState(
                        tunnel,
                        Tunnel.State.DOWN,
                        null
                    )
                }
            }
        }
    }
}
