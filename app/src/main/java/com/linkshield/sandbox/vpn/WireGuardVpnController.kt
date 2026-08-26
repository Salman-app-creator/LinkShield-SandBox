package com.linkshield.sandbox.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WireGuardVpnController.kt
 *
 * Low-level wrapper around the WireGuard GoBackend.
 *
 * NOTE: backend.setState() returns Tunnel.State (not Unit).
 * runCatching infers the return type from the last expression, so we
 * must explicitly end each block with `Unit` to get Result<Unit>.
 */
class WireGuardVpnController(private val context: Context) {

    companion object {
        private const val TUNNEL_NAME = "LinkShieldVPN"
    }

    private val backend: GoBackend by lazy {
        GoBackend(context.applicationContext)
    }

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) { }
    }

    suspend fun connect(config: Config): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("UNUSED_VARIABLE")
                val ignored: Tunnel.State = backend.setState(tunnel, Tunnel.State.UP, config)
                Unit                        // Explicit Unit — fixes Result<Tunnel.State> mismatch
            }
        }

    suspend fun disconnect(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("UNUSED_VARIABLE")
                val ignored: Tunnel.State = backend.setState(tunnel, Tunnel.State.DOWN, null)
                Unit                        // Explicit Unit
            }
        }

    fun isConnected(): Boolean =
        runCatching {
            backend.getState(tunnel) == Tunnel.State.UP
        }.getOrDefault(false)
}
