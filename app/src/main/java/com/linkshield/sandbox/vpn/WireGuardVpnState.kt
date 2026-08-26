package com.linkshield.sandbox.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WireGuardVpnState.kt
 *
 * Holds and exposes the reactive VPN tunnel state consumed by
 * WireGuardVpnManager and observed via VpnShieldController in the UI.
 *
 * Thread-safe: MutableStateFlow assignments are atomic.
 */
class WireGuardVpnState {

    private val _status =
        MutableStateFlow(WireGuardVpnStatus.DISCONNECTED)

    val status: StateFlow<WireGuardVpnStatus> =
        _status.asStateFlow()

    private val _error =
        MutableStateFlow<String?>(null)

    val error: StateFlow<String?> =
        _error.asStateFlow()

    // ── State transitions ─────────────────────────────────────────────────────

    fun setConnecting() {
        _status.value = WireGuardVpnStatus.CONNECTING
        _error.value  = null
    }

    fun setConnected() {
        _status.value = WireGuardVpnStatus.CONNECTED
        _error.value  = null
    }

    fun setDisconnecting() {
        _status.value = WireGuardVpnStatus.DISCONNECTING
    }

    fun setDisconnected() {
        _status.value = WireGuardVpnStatus.DISCONNECTED
        _error.value  = null
    }

    fun setError(message: String) {
        _status.value = WireGuardVpnStatus.ERROR
        _error.value  = message
    }

    fun clearError() {
        if (_status.value == WireGuardVpnStatus.ERROR) {
            _status.value = WireGuardVpnStatus.DISCONNECTED
        }
        _error.value = null
    }
}
