package com.linkshield.sandbox.vpn

/**
 * VpnConnectionState.kt
 *
 * Sealed class representing every possible state of the VPN lifecycle.
 * Observed by VpnViewModel via StateFlow and rendered in VpnScreen.
 *
 * State machine:
 *
 *   DISCONNECTED ──► CONNECTING ──► CONNECTED
 *        ▲                │             │
 *        │                └──► ERROR    │
 *        └──────── DISCONNECTING ◄──────┘
 */
sealed class VpnConnectionState {

    /** No VPN tunnel active. Default/initial state. */
    data object Disconnected : VpnConnectionState()

    /**
     * Handshake / TUN setup in progress.
     * UI should show a loading/spinner indicator.
     */
    data object Connecting : VpnConnectionState()

    /**
     * Tunnel is fully up and routing traffic through the Shadowsocks proxy.
     *
     * @param serverIp IP address of the connected Shadowsocks server.
     * @param startTimeMs System.currentTimeMillis() at connection start —
     *                    used to display session duration in the UI.
     */
    data class Connected(
        val serverIp: String   = ShadowsocksConfig.HOST,
        val startTimeMs: Long  = System.currentTimeMillis()
    ) : VpnConnectionState()

    /**
     * Graceful teardown in progress.
     * UI button should be disabled during this state.
     */
    data object Disconnecting : VpnConnectionState()

    /**
     * An unrecoverable error occurred.
     * UI should display [message] and offer a retry/reconnect option.
     *
     * @param message Human-readable error description.
     */
    data class Error(val message: String) : VpnConnectionState()
}

// ── Convenience extensions ────────────────────────────────────────────────────

val VpnConnectionState.isActive: Boolean
    get() = this is VpnConnectionState.Connected

val VpnConnectionState.isBusy: Boolean
    get() = this is VpnConnectionState.Connecting ||
            this is VpnConnectionState.Disconnecting

val VpnConnectionState.label: String
    get() = when (this) {
        is VpnConnectionState.Disconnected  -> "CONNECT"
        is VpnConnectionState.Connecting    -> "CONNECTING…"
        is VpnConnectionState.Connected     -> "DISCONNECT"
        is VpnConnectionState.Disconnecting -> "DISCONNECTING…"
        is VpnConnectionState.Error         -> "RETRY"
    }
