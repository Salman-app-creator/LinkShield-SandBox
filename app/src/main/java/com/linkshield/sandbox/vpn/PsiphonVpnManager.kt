package com.linkshield.sandbox.vpn

// REPO PATH: app/src/main/java/com/linkshield/sandbox/vpn/PsiphonVpnManager.kt

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PsiphonVpnManager
 *
 * WireGuardVpnManager ki jagah yeh class use hogi.
 * Same interface rakha hai taake UnblockShieldScreen mein
 * sirf class name change karna pade.
 *
 * Psiphon automatically best available server choose karta hai —
 * koi config, koi account needed nahi.
 */
class PsiphonVpnManager(private val context: Context) {

    private var connected = false

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val intent = Intent(context, PsiphonVpnService::class.java).apply {
                action = PsiphonVpnService.ACTION_START
            }
            context.startService(intent)
            connected = true
            Result.success(Unit)
        } catch (e: Exception) {
            connected = false
            Result.failure(e)
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val intent = Intent(context, PsiphonVpnService::class.java).apply {
                action = PsiphonVpnService.ACTION_STOP
            }
            context.startService(intent)
            connected = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isConnected(): Boolean = connected
}
