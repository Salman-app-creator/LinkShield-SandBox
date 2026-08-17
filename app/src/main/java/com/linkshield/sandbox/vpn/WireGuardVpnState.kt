package com.linkshield.sandbox.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WireGuardVpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

class WireGuardVpnState {

    private val _status =
        MutableStateFlow(
            WireGuardVpnStatus.DISCONNECTED
        )

    val status: StateFlow<WireGuardVpnStatus> =
        _status.asStateFlow()

    private val _error =
        MutableStateFlow<String?>(null)

    val error: StateFlow<String?> =
        _error.asStateFlow()

    fun setConnecting() {
        _error.value = null
        _status.value =
            WireGuardVpnStatus.CONNECTING
    }

    fun setConnected() {
        _error.value = null
        _status.value =
            WireGuardVpnStatus.CONNECTED
    }

    fun setDisconnecting() {
        _status.value =
            WireGuardVpnStatus.DISCONNECTING
    }

    fun setDisconnected() {
        _error.value = null
        _status.value =
            WireGuardVpnStatus.DISCONNECTED
    }

    fun setError(message: String) {
        _error.value = message
        _status.value =
            WireGuardVpnStatus.ERROR
    }

    fun clearError() {
        _error.value = null

        if (
            _status.value ==
            WireGuardVpnStatus.ERROR
        ) {
            _status.value =
                WireGuardVpnStatus.DISCONNECTED
        }
    }
}
