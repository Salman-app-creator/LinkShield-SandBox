package com.linkshield.sandbox.vpn

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader

/**
 * WireGuardVpnRepository.kt
 *
 * Handles persistence and retrieval of the WireGuard tunnel configuration.
 * Config is stored in EncryptedSharedPreferences so private keys are
 * never stored in plaintext on disk.
 *
 * Called by WireGuardVpnManager — do not access directly from UI.
 */
class WireGuardVpnRepository(private val context: Context) {

    companion object {
        private const val PREFS_FILE  = "wg_config_prefs"
        private const val KEY_CONFIG  = "wireguard_config"
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

    /** Returns true if a saved config exists. */
    fun hasConfig(): Boolean =
        prefs.contains(KEY_CONFIG)

    /**
     * Load and parse the saved WireGuard config.
     * Returns Result.failure if no config is saved or parsing fails.
     */
    suspend fun loadConfig(): Result<Config> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = prefs.getString(KEY_CONFIG, null)
                ?: error("No WireGuard configuration found. Please import a .conf file.")

            Config.parse(BufferedReader(StringReader(raw)))
        }
    }

    /**
     * Encrypt and persist the raw WireGuard config text.
     * Pass the content of a standard .conf file.
     */
    suspend fun saveConfig(configText: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Validate the config before saving
                Config.parse(BufferedReader(StringReader(configText)))

                prefs.edit()
                    .putString(KEY_CONFIG, configText)
                    .apply()
            }
        }

    /** Remove the stored config (e.g. on logout / reset). */
    suspend fun deleteConfig(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_CONFIG).apply()
    }
}
