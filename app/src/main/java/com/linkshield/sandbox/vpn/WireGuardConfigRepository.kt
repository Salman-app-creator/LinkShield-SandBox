package com.linkshield.sandbox.vpn

import android.content.Context
import java.io.File

class WireGuardConfigRepository(
    context: Context
) {

    private val configDir =
        File(
            context.filesDir,
            "wireguard"
        ).apply {
            if (!exists()) {
                mkdirs()
            }
        }

    private val configFile =
        File(
            configDir,
            "linkshield.conf"
        )

    fun save(config: String) {
        require(config.isNotBlank()) {
            "WireGuard configuration is empty"
        }

        configFile.writeText(
            config,
            Charsets.UTF_8
        )
    }
    fun load(): String? {
        if (!configFile.exists()) {
            return null
        }

        return runCatching {
            configFile.readText(
                Charsets.UTF_8
            )
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun exists(): Boolean {
        return configFile.exists() &&
            configFile.length() > 0
    }

    fun delete() {
        runCatching {
            configFile.delete()
        }
    }
    fun configFile(): File {
        return configFile
    }
}
