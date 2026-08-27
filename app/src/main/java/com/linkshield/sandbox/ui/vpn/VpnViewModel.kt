package com.linkshield.sandbox.ui.vpn

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/vpn/VpnViewModel.kt

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linkshield.sandbox.vpn.PsiphonVpnManager
import com.linkshield.sandbox.vpn.VpnConnectionState
import com.linkshield.sandbox.vpn.isActive
import com.linkshield.sandbox.vpn.isBusy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val psiphonManager = PsiphonVpnManager(application)

    private val _vpnState = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
    val vpnState: StateFlow<VpnConnectionState> = _vpnState.asStateFlow()

    fun getVpnPermissionIntent(): Intent? = VpnService.prepare(getApplication())

    fun onToggleVpn(onPermissionRequired: (Intent) -> Unit) {
        val current = _vpnState.value
        if (current.isBusy) return

        if (current.isActive) {
            disconnect()
            return
        }

        val permissionIntent = getVpnPermissionIntent()
        if (permissionIntent != null) {
            onPermissionRequired(permissionIntent)
        } else {
            onPermissionGranted()
        }
    }

    fun onPermissionGranted() {
        _vpnState.value = VpnConnectionState.Connecting
        viewModelScope.launch {
            val result = psiphonManager.connect()
            _vpnState.value = if (result.isSuccess) {
                VpnConnectionState.Connected()
            } else {
                VpnConnectionState.Error(result.exceptionOrNull()?.message ?: "Connection failed")
            }
        }
    }

    fun disconnect() {
        _vpnState.value = VpnConnectionState.Disconnecting
        viewModelScope.launch {
            psiphonManager.disconnect()
            _vpnState.value = VpnConnectionState.Disconnected
        }
    }

    fun clearError() {
        if (_vpnState.value is VpnConnectionState.Error) {
            _vpnState.value = VpnConnectionState.Disconnected
        }
    }
}
