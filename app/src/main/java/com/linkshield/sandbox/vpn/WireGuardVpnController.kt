package com.linkshield.sandbox.vpn

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WireGuardVpnController(
    context: Context
) {

    private val backend: Backend =
        GoBackend(context.applicationContext)

    private val tunnel =
        LinkShieldTunnel()

    suspend fun connect(
        configText: String
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(
                    configText.isNotBlank()
                ) {
                    "WireGuard configuration is empty"
                }

                val config =
                    Config.parse(
                        configText.byteInputStream()
                    )

                backend.setState(
                    tunnel,
                    Tunnel.State.UP,
                    config
                )

                check(
                    backend.getState(tunnel) ==
                        Tunnel.State.UP
                ) {
                    "WireGuard tunnel failed to start"
                }
            }
        }
        suspend fun disconnect(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                backend.setState(
                    tunnel,
                    Tunnel.State.DOWN,
                    null
                )

                check(
                    backend.getState(tunnel) ==
                        Tunnel.State.DOWN
                ) {
                    "WireGuard tunnel failed to stop"
                }
            }
        }

    fun isConnected(): Boolean {
        return runCatching {
            backend.getState(tunnel) ==
                Tunnel.State.UP
        }.getOrDefault(false)
    }

    fun version(): String {
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
            // WireGuard backend reports the state.
        }
    }
}
