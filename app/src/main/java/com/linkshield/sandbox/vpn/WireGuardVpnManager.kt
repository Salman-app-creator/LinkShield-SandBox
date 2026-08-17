package com.linkshield.sandbox.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WireGuardVpnManager(
    context: Context
) {

    private val repository =
        WireGuardVpnRepository(context)

    private val controller =
        WireGuardVpnController(context)

    val state =
        WireGuardVpnState()

    suspend fun connect(): Result<Unit> =
        withContext(Dispatchers.IO) {

            state.setConnecting()

            repository.loadConfig()
                .fold(
                    onSuccess = { config ->
                        controller.connect(config)
                            .onSuccess {
                                state.setConnected()
                            }
                            .onFailure { error ->
                                state.setError(
                                    error.message
                                        ?: "Unable to connect"
                                )
                            }
                    },
                    onFailure = { error ->
                        state.setError(
                            error.message
                                ?: "WireGuard configuration missing"
                        )
                    }
                )
        }
        suspend fun disconnect(): Result<Unit> =
        withContext(Dispatchers.IO) {

            state.setDisconnecting()

            controller.disconnect()
                .onSuccess {
                    state.setDisconnected()
                }
                .onFailure { error ->
                    state.setError(
                        error.message
                            ?: "Unable to disconnect"
                    )
                }
        }

    fun isConnected(): Boolean {
        return controller.isConnected()
    }

    fun hasConfiguration(): Boolean {
        return repository.hasConfig()
    }

    suspend fun saveConfiguration(
        configText: String
    ): Result<Unit> {

        return repository.saveConfig(
            configText
        )
    }
 suspend fun removeConfiguration() {
        repository.deleteConfig()

        if (controller.isConnected()) {
            disconnect()
        }

        state.setDisconnected()
    }

    fun clearError() {
        state.clearError()
    }
}   
