package com.linkshield.sandbox.vpn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VpnTunnelManager(
    private val context: Context
) {

    private val _connected =
        MutableStateFlow(false)

    val connected: StateFlow<Boolean> =
        _connected.asStateFlow()

    fun connect(
        config: VpnTunnelConfig
    ): Boolean {
        if (config.serverAddress.isBlank()) {
            return false
        }

        if (config.serverPort !in 1..65535) {
            return false
        }

        if (config.privateKey.isBlank()) {
            return false
        }

        if (config.publicKey.isBlank()) {
            return false
        }

        if (config.clientAddress.isBlank()) {
            return false
        }

        /*
         * The Android VpnService interface is controlled
         * by SecureVpnService. This manager only owns
         * transport state and validation.
         *
         * A real encrypted transport must be attached
         * here before reporting connected.
         */
        return false
    }
    fun disconnect() {
        _connected.value = false
    }

    fun isConnected(): Boolean {
        return _connected.value
    }

    fun markTransportConnected() {
        _connected.value = true
    }

    fun markTransportDisconnected() {
        _connected.value = false
    }

    fun validate(
        config: VpnTunnelConfig
    ): Boolean {
        return config.serverAddress.isNotBlank() &&
            config.serverPort in 1..65535 &&
            config.privateKey.isNotBlank() &&
            config.publicKey.isNotBlank() &&
            config.clientAddress.isNotBlank()
    }
    fun clearState() {
        _connected.value = false
    }

    fun shutdown() {
        _connected.value = false
    }
}
