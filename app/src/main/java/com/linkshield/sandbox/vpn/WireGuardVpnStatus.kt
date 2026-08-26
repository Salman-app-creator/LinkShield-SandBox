package com.linkshield.sandbox.vpn

/**
 * WireGuardVpnStatus.kt
 *
 * Represents the lifecycle state of the WireGuard VPN tunnel.
 * Used by VpnShieldController, VpnNotificationHelper, and
 * WireGuardVpnState to communicate tunnel status across layers.
 */
enum class WireGuardVpnStatus {

    /** Tunnel is fully up and routing traffic. */
    CONNECTED,

    /** Handshake / key exchange in progress. */
    CONNECTING,

    /** Tunnel teardown in progress. */
    DISCONNECTING,

    /** Tunnel is fully down — no VPN active. */
    DISCONNECTED,

    /** An unrecoverable error occurred; user action required. */
    ERROR
}
