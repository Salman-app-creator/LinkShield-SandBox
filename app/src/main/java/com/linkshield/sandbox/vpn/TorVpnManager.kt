package com.linkshield.sandbox.vpn

// REPO PATH: app/src/main/java/com/linkshield/sandbox/vpn/TorVpnManager.kt
// VPN feature removed. This stub kept for compile compatibility only.

import android.content.Context

class TorVpnManager(private val context: Context) {

    companion object {
        @Volatile
        private var _isConnected = false
        fun setConnected(value: Boolean) { _isConnected = value }
    }

    fun connect(): Result<Unit> = Result.success(Unit)
    fun disconnect(): Result<Unit> { _isConnected = false; return Result.success(Unit) }
    fun isConnected(): Boolean = false
}
