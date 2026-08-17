package com.linkshield.sandbox.vpn

import com.wireguard.config.BadConfigException
import com.wireguard.config.Config

object WireGuardConfigValidator {

    fun validate(
        configText: String
    ): Result<Unit> {

        if (configText.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "WireGuard configuration is empty"
                )
            )
        }

        return runCatching {
            Config.parse(
                configText.byteInputStream()
            )

            Unit
        }.recoverCatching { error ->

            if (error is BadConfigException) {
                throw IllegalArgumentException(
                    "Invalid WireGuard configuration",
                    error
                )
            }

            throw error
        }
    }

    fun isValid(
        configText: String
    ): Boolean {
        return validate(configText).isSuccess
    }
}
