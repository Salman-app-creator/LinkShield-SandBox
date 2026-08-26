package com.linkshield.sandbox.ui.vpn

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linkshield.sandbox.vpn.ShadowsocksVpnService
import com.linkshield.sandbox.vpn.VpnConnectionState
import com.linkshield.sandbox.vpn.VpnStateHolder
import com.linkshield.sandbox.vpn.isActive
import com.linkshield.sandbox.vpn.isBusy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * VpnViewModel.kt
 *
 * Bridges the VPN Service and the Compose UI layer.
 *
 * Responsibilities:
 *  - Exposes [vpnState] StateFlow for the UI to observe.
 *  - Handles the VPN permission check (VpnService.prepare()) before connecting.
 *  - Delegates connect / disconnect commands to ShadowsocksVpnService.
 *
 * AndroidViewModel is used (instead of plain ViewModel) to access Application
 * context without leaking Activity context.
 */
class VpnViewModel(application: Application) : AndroidViewModel(application) {

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Current VPN connection state.
     * Collected and rendered by VpnScreen.
     *
     * SharingStarted.Eagerly ensures the flow stays hot as long as the
     * ViewModel is alive — no missed emissions between screen navigations.
     */
    val vpnState: StateFlow<VpnConnectionState> = VpnStateHolder.state
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.Eagerly,
            initialValue   = VpnConnectionState.Disconnected
        )

    // ── Permission Intent ─────────────────────────────────────────────────────

    /**
     * Returns the VPN permission intent if the user has not yet granted it.
     *
     * The Activity/Composable should launch this intent via
     * rememberLauncherForActivityResult. If null is returned, permission
     * is already granted and [onPermissionGranted] can be called directly.
     */
    fun getVpnPermissionIntent(): Intent? =
        VpnService.prepare(getApplication())

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Called by the UI's 1-click button.
     *
     * If VPN is active → disconnect.
     * If VPN is idle/error → check permission first, then connect.
     * If VPN is transitioning (busy) → ignore (button should be disabled).
     */
    fun onToggleVpn(
        onPermissionRequired: (Intent) -> Unit
    ) {
        val current = vpnState.value

        if (current.isBusy) return

        if (current.isActive) {
            disconnect()
            return
        }

        // Need permission check before connecting
        val permissionIntent = getVpnPermissionIntent()
        if (permissionIntent != null) {
            // Not yet granted — launch the system dialog
            onPermissionRequired(permissionIntent)
        } else {
            // Already granted — connect immediately
            onPermissionGranted()
        }
    }

    /**
     * Called after the user grants VPN permission in the system dialog,
     * OR directly when permission was already granted.
     */
    fun onPermissionGranted() {
        ShadowsocksVpnService.startConnect(getApplication())
    }

    /** Gracefully disconnect the active tunnel. */
    fun disconnect() {
        ShadowsocksVpnService.startDisconnect(getApplication())
    }

    /** Clear an error state so the user can retry. */
    fun clearError() {
        if (vpnState.value is VpnConnectionState.Error) {
            VpnStateHolder.setState(VpnConnectionState.Disconnected)
        }
    }
}
