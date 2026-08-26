package com.linkshield.sandbox.vpn

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader

class WireGuardVpnRepository(private val context: Context) {

    // ── Single companion object — merged ──────────────────────────────────────
    companion object {
        private const val PREFS_FILE = "wg_config_prefs"
        private const val KEY_CONFIG = "wireguard_config"

        private const val BUILT_IN_CONFIG = """
[Interface]
PrivateKey = OH1Y55pIvy330HQrKaEz4q53to+RtrF8jLVK85kLY1k=
Address = 10.66.66.3/32,fd42:42:42::3/128
DNS = 1.1.1.1,1.0.0.1

[Peer]
PublicKey = cKyQuobdhp7+twoNW0muNo1mEB/4+IRS+LP51GQuxC4=
PresharedKey = KF/W4IBCsLpN33tq6BEBLnkBQjbl+aznxffKQqafQ8g=
Endpoint = 141.148.223.177:54536
AllowedIPs = 0.0.0.0/0,::/0
PersistentKeepalive = 25
"""
    }

    // ── EncryptedSharedPreferences ────────────────────────────────────────────

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun hasConfig(): Boolean = true

    suspend fun loadConfig(): Result<Config> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = prefs.getString(KEY_CONFIG, null) ?: BUILT_IN_CONFIG
            Config.parse(BufferedReader(StringReader(raw)))
        }
    }

    suspend fun saveConfig(configText: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Config.parse(BufferedReader(StringReader(configText)))
                prefs.edit().putString(KEY_CONFIG, configText).apply()
            }
        }

    suspend fun deleteConfig(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_CONFIG).apply()
    }
}
