package com.linkshield.sandbox.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VpnStateHolder.kt
 *
 * Process-wide singleton that bridges the VPN Service and the ViewModel.
 *
 * WHY THIS APPROACH:
 * Android's VpnService runs in the same process as the UI but in a different
 * lifecycle scope. Using a shared StateFlow in a singleton is the cleanest
 * way to let the Service push state to the ViewModel without binding the
 * service (which complicates the lifecycle) or using BroadcastReceivers
 * (which are heavyweight and need registration/deregistration).
 *
 * The ViewModel observes [state] — the Service writes to it via [setState].
 * Both sides are in the same process so no IPC overhead.
 */
object VpnStateHolder {

    private val _state = MutableStateFlow<VpnConnectionState>(
        VpnConnectionState.Disconnected
    )

    /** Observed by VpnViewModel — never write to this directly from UI. */
    val state: StateFlow<VpnConnectionState> = _state.asStateFlow()

    /**
     * Called exclusively by ShadowsocksVpnService to push state transitions.
     * Thread-safe — MutableStateFlow.value is internally synchronized.
     */
    fun setState(newState: VpnConnectionState) {
        _state.value = newState
    }
}
