// app/src/main/java/com/linkshield/sandbox/vpn/PsiphonVpnManager.kt
package com.linkshield.sandbox.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

class PsiphonVpnManager(private val context: Context) {

    companion object {
        @Volatile
        private var _isConnected = false

        fun setConnected(value: Boolean) { _isConnected = value }
    }

    fun connect(): Result<Unit> {
        return try {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                Result.failure(SecurityException("VPN permission required"))
            } else {
                val intent = Intent(context, PsiphonVpnService::class.java).apply {
                    action = PsiphonVpnService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnect(): Result<Unit> {
        return try {
            val intent = Intent(context, PsiphonVpnService::class.java).apply {
                action = PsiphonVpnService.ACTION_STOP
            }
            context.startService(intent)
            _isConnected = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isConnected(): Boolean = _isConnected
}
