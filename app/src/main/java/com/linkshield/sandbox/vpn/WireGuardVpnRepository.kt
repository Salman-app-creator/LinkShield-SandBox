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

    suspend fun loadConfig(): Result<String> = withContext(Dispatchers.IO) {
        Result.success(HARDCODED_CONFIG)
    }

    // Hardcoded Oracle Mumbai config — always available
    private val HARDCODED_CONFIG = """
[Interface]
PrivateKey = wGOYZWTR+lStqpZUGKn/txvKPdgCTEkjTAhRJgHqO3M=
Address = 10.66.66.2/32,fd42:42:42::2/128
DNS = 1.1.1.1,1.0.0.1

[Peer]
PublicKey = cKyQuobdhp7+twoNW0muNo1mEB/4+IRS+LP51GQuxC4=
PresharedKey = JBnPv8YQkEdtm0R+h888nd56dyrpYK+T3X/nTT8C7Qs=
Endpoint = 141.148.223.177:54536
AllowedIPs = 0.0.0.0/1,128.0.0.0/1,::/0
""".trimIndent()

    fun hasConfig(): Boolean = true

    suspend fun deleteConfig() =
        withContext(Dispatchers.IO) {
            configRepository.delete()
        }
}
