package com.linkshield.sandbox.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WireGuardConfigProvider(
    context: Context
) {

    private val repository =
        WireGuardVpnRepository(context)

    suspend fun getConfig(): Result<String> =
        withContext(Dispatchers.IO) {
            repository.loadConfig()
        }

    suspend fun setConfig(
        configText: String
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            repository.saveConfig(
                configText
            )
        }

    fun hasConfig(): Boolean {
        return repository.hasConfig()
    }
    suspend fun clearConfig() {
        withContext(Dispatchers.IO) {
            repository.deleteConfig()
        }
    }

    suspend fun getValidatedConfig():
        Result<String> {

        return getConfig().fold(
            onSuccess = { config ->
                WireGuardConfigValidator
                    .validate(config)
                    .map {
                        config
                    }
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
        }
        suspend fun isReady(): Boolean =
        withContext(Dispatchers.IO) {
            val config = repository.loadConfig()
                .getOrNull()

            config != null &&
                WireGuardConfigValidator
                    .isValid(config)
        }
}
