package com.linkshield.sandbox.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WireGuardVpnManager(
    context: Context
) {

    private val repository = WireGuardVpnRepository(context)
    private val controller = WireGuardVpnController(context)
    val state = WireGuardVpnState()

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        state.setConnecting()

        // 1. Config load karein
        val configResult = repository.loadConfig()

        if (configResult.isFailure) {
            val errorMsg = configResult.exceptionOrNull()?.message
                ?: "WireGuard configuration missing"
            state.setError(errorMsg)
            return@withContext Result.failure(Exception(errorMsg))
        }

        val config = configResult.getOrThrow()

        // 2. Controller ke zariye tunnel connect karein
        val connectResult = controller.connect(config)
        connectResult
            .onSuccess {
                state.setConnected()
            }
            .onFailure { throwable ->
                val errorMsg = throwable.message ?: "Unable to connect"
                state.setError(errorMsg)
            }
        return@withContext connectResult
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        state.setDisconnecting()

        val result = controller.disconnect()
        result
            .onSuccess {
                state.setDisconnected()
            }
            .onFailure { throwable ->
                val errorMsg = throwable.message ?: "Unable to disconnect"
                state.setError(errorMsg)
            }
        return@withContext result
    }

    fun isConnected(): Boolean {
        return controller.isConnected()
    }

    fun hasConfiguration(): Boolean {
        return repository.hasConfig()
    }

    suspend fun saveConfiguration(configText: String): Result<Unit> {
        return repository.saveConfig(configText)
    }

    suspend fun removeConfiguration() {
        if (controller.isConnected()) {
            disconnect()
        }
        repository.deleteConfig()
        state.setDisconnected()
    }

    fun clearError() {
        state.clearError()
    }
}
