package com.linkshield.sandbox.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WireGuardVpnRepository(
    context: Context
) {

    private val configRepository =
        WireGuardConfigRepository(context)

    suspend fun saveConfig(
        configText: String
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            WireGuardConfigValidator
                .validate(configText)
                .map {
                    configRepository.save(
                        configText
                    )
                }
        }

    suspend fun loadConfig(): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                configRepository.load()
                    ?: throw IllegalStateException(
                        "No WireGuard configuration found"
                    )
            }
        }

    fun hasConfig(): Boolean {
        return configRepository.exists()
    }

    suspend fun deleteConfig() =
        withContext(Dispatchers.IO) {
            configRepository.delete()
        }
}
