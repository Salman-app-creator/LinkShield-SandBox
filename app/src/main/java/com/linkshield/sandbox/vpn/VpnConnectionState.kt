package com.linkshield.sandbox.vpn

// REPO PATH: app/src/main/java/com/linkshield/sandbox/vpn/VpnConnectionState.kt

sealed class VpnConnectionState {
    object Disconnected  : VpnConnectionState()
    object Connecting    : VpnConnectionState()
    data class Connected(val startTimeMs: Long = System.currentTimeMillis(), val serverIp: String = "Psiphon") : VpnConnectionState()
    object Disconnecting : VpnConnectionState()
    data class Error(val message: String) : VpnConnectionState()
}

val VpnConnectionState.isActive: Boolean
    get() = this is VpnConnectionState.Connected

val VpnConnectionState.isBusy: Boolean
    get() = this is VpnConnectionState.Connecting || this is VpnConnectionState.Disconnecting

val VpnConnectionState.label: String
    get() = when (this) {
        is VpnConnectionState.Disconnected  -> "TAP TO CONNECT"
        is VpnConnectionState.Connecting    -> "CONNECTING..."
        is VpnConnectionState.Connected     -> "CONNECTED"
        is VpnConnectionState.Disconnecting -> "DISCONNECTING..."
        is VpnConnectionState.Error         -> "ERROR"
    }
