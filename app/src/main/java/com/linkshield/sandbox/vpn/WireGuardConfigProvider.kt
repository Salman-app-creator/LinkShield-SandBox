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
            repository.loadConfig().map { configText ->
                ensureSecureDns(configText)
            }
        }

    suspend fun setConfig(
        configText: String
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val updatedConfig = ensureSecureDns(configText)
            repository.saveConfig(
                updatedConfig
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

    suspend fun getValidatedConfig(): Result<String> {
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
            val config = getConfig().getOrNull()

            config != null &&
                WireGuardConfigValidator
                    .isValid(config)
        }

    // Secure DNS Ensure Karne Ka Helper Function
    private fun ensureSecureDns(config: String): String {
        if (config.contains("DNS =", ignoreCase = true)) {
            return config
        }
        // Agar config mein DNS line nahi hai toh [Interface] ke neeche auto-add kar do
        return if (config.contains("[Interface]", ignoreCase = true)) {
            config.replace(
                "[Interface]",
                "[Interface]\nDNS = 1.1.1.1, 1.0.0.1",
                ignoreCase = true
            )
        } else {
            config
        }
    }
}
