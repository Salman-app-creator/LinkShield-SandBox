package com.linkshield.sandbox.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecureVpnManager(
    private val context: Context
) {

    companion object {
        const val ACTION_CONNECT =
            "com.linkshield.sandbox.vpn.CONNECT"

        const val ACTION_DISCONNECT =
            "com.linkshield.sandbox.vpn.DISCONNECT"

        const val ACTION_STATUS =
            "com.linkshield.sandbox.vpn.STATUS"

        const val EXTRA_CONNECTED =
            "vpn_connected"

        const val REQUEST_VPN_PERMISSION = 9101

        private const val PREFS =
            "linkshield_vpn"

        private const val KEY_ENABLED =
            "vpn_enabled"
    }

    private val preferences =
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

    private val _isConnected =
        MutableStateFlow(
            preferences.getBoolean(
                KEY_ENABLED,
                false
            )
        )

    val isConnected: StateFlow<Boolean> =
        _isConnected.asStateFlow()

    fun prepare(activity: Activity): Boolean {
        val permissionIntent =
            VpnService.prepare(activity)

        return if (permissionIntent != null) {
            activity.startActivityForResult(
                permissionIntent,
                REQUEST_VPN_PERMISSION
            )
            false
        } else {
            true
        }
    }
    fun connect() {
        val intent = Intent(
            context,
            SecureVpnService::class.java
        ).apply {
            action = ACTION_CONNECT
        }

        try {
            context.startService(intent)
        } catch (e: Exception) {
            setConnected(false)
        }
    }

    fun disconnect() {
        val intent = Intent(
            context,
            SecureVpnService::class.java
        ).apply {
            action = ACTION_DISCONNECT
        }

        try {
            context.startService(intent)
        } catch (e: Exception) {
            setConnected(false)
        }
    }

    fun toggle(activity: Activity) {
        if (_isConnected.value) {
            disconnect()
            return
        }

        val permissionIntent =
            VpnService.prepare(activity)

        if (permissionIntent != null) {
            activity.startActivityForResult(
                permissionIntent,
                REQUEST_VPN_PERMISSION
            )
        } else {
            connect()
        }
    }

    fun setConnected(value: Boolean) {
        _isConnected.value = value

        preferences.edit()
            .putBoolean(
                KEY_ENABLED,
                value
            )
            .apply()
    }

    fun refreshState() {
        _isConnected.value =
            preferences.getBoolean(
                KEY_ENABLED,
                false
            )
    }
   fun isConnected(): Boolean {
        return _isConnected.value
    }

    fun clearState() {
        _isConnected.value = false

        preferences.edit()
            .putBoolean(
                KEY_ENABLED,
                false
            )
            .apply()
    }

    fun serviceIntent(
        action: String
    ): Intent {
        return Intent(
            context,
            SecureVpnService::class.java
        ).apply {
            this.action = action
        }
    }
} 
