// app/src/main/java/com/linkshield/sandbox/vpn/PsiphonVpnManager.kt
package com.linkshield.sandbox.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

class PsiphonVpnManager(private val context: Context) {

    @Volatile
    private var isVpnConnected = false

    fun connect(): Result<Unit> {
        return try {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                // Return error if VPN permission is not granted by user yet
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
                isVpnConnected = true
                Result.success(Unit)
            }
        } catch (e: Exception) {
            isVpnConnected = false
            Result.failure(e)
        }
    }

    fun disconnect(): Result<Unit> {
        return try {
            val intent = Intent(context, PsiphonVpnService::class.java).apply {
                action = PsiphonVpnService.ACTION_STOP
            }
            context.startService(intent)
            isVpnConnected = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isConnected(): Boolean = isVpnConnected
}
