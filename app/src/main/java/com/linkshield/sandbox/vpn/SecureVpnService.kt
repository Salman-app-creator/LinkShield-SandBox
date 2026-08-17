package com.linkshield.sandbox.vpn

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SecureVpnService : VpnService() {

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private lateinit var wireGuardBackend: GoBackend

    private val tunnel =
        LinkShieldTunnel()

    override fun onCreate() {
        super.onCreate()

        wireGuardBackend =
            GoBackend(applicationContext)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_CONNECT -> {
                val configText =
                    intent.getStringExtra(
                        EXTRA_CONFIG
                    )

                if (!configText.isNullOrBlank()) {
                    startTunnel(configText)
                }
            }

            ACTION_DISCONNECT -> {
                stopTunnel()
            }
        }

        return START_STICKY
    }

    private fun startTunnel(
        configText: String
    ) {
        serviceScope.launch {
            runCatching {
                val config =
                    Config.parse(
                        configText.byteInputStream()
                    )

                wireGuardBackend.setState(
                    tunnel,
                    Tunnel.State.UP,
                    config
                )

                val state =
                    wireGuardBackend.getState(
                        tunnel
                    )

                if (state != Tunnel.State.UP) {
                    stopSelf()
                }
            }.onFailure {
                stopSelf()
            }
        }
    }
    private fun stopTunnel() {
        serviceScope.launch {
            runCatching {
                if (
                    wireGuardBackend.getState(
                        tunnel
                    ) == Tunnel.State.UP
                ) {
                    wireGuardBackend.setState(
                        tunnel,
                        Tunnel.State.DOWN,
                        null
                    )
                }
            }

            stopSelf()
        }
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return super.onBind(intent)
    }

    override fun onDestroy() {
        runCatching {
            if (
                ::wireGuardBackend.isInitialized &&
                wireGuardBackend.getState(
                    tunnel
                ) == Tunnel.State.UP
            ) {
                wireGuardBackend.setState(
                    tunnel,
                    Tunnel.State.DOWN,
                    null
                )
            }
        }

        serviceScope.cancel()

        super.onDestroy()
    }

    private class LinkShieldTunnel :
        Tunnel {

        override fun getName(): String {
            return "LinkShield"
        }

        override fun onStateChange(
            newState: Tunnel.State
        ) {
            // WireGuard backend owns the actual
            // tunnel state.
        }
        }
        companion object {

        const val ACTION_CONNECT =
            "com.linkshield.sandbox.vpn.CONNECT"

        const val ACTION_DISCONNECT =
            "com.linkshield.sandbox.vpn.DISCONNECT"

        const val EXTRA_CONFIG =
            "wireguard_config"
    }
}
