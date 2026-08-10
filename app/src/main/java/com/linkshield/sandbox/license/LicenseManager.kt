package com.linkshield.sandbox.license

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class LicenseManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, "license_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Device fingerprint for anti-piracy single-device binding
    private val deviceId: String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    companion object {
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_DOWNLOAD_COUNT = "download_count"
        private const val KEY_BOUND_DEVICE = "bound_device_id"
        private const val KEY_INITIALIZED = "initialized"
        private const val FREE_LIMIT = 20
        private const val KEY_PREFIX = "LSHD"
    }

    init {
        // FIX: Fresh install must always start at 0/20, not carry over bad state.
        // We use a separate "initialized" flag so we only reset once per fresh install.
        // EncryptedSharedPreferences on a clean install returns the defaults we specify,
        // so getInt(KEY_DOWNLOAD_COUNT, 0) is already correct — but we guard against
        // any edge-case where the pref file exists from a partially-uninstalled state
        // with a stale value by writing 0 if KEY_INITIALIZED is absent.
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putInt(KEY_DOWNLOAD_COUNT, 0)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
        }
    }

    fun isProUser(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)

    fun getDownloadCount(): Int = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    fun canDownload(): Boolean = isProUser() || getDownloadCount() < FREE_LIMIT

    fun incrementDownload(): Boolean {
        if (isProUser()) return true
        val current = getDownloadCount()
        if (current >= FREE_LIMIT) return false
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, current + 1).apply()
        return true
    }

    /**
     * Validates the license key and binds it to this device.
     *
     * Anti-piracy logic:
     * - When a key is validated, the current device ID is stored alongside it.
     * - On subsequent attempts, if a key is already bound to a DIFFERENT device ID,
     *   validation fails — preventing key sharing across devices.
     * - For a full cloud-based check, replace [isKeyAlreadyBoundToOtherDevice] with
     *   an API call that stores the binding server-side.
     */
    fun validateKey(key: String): Boolean {
        val clean = key.uppercase().replace("-", "").trim()
        if (clean.length != 16) return false
        if (!clean.startsWith(KEY_PREFIX)) return false

        val body = clean.substring(0, 12)
        val providedChecksum = clean.substring(12, 16)
        val expectedChecksum = generateChecksum(body)

        if (providedChecksum != expectedChecksum) return false

        // Anti-piracy: check if this key is already bound to a different device
        val boundDevice = prefs.getString("${KEY_BOUND_DEVICE}_$clean", null)
        if (boundDevice != null && boundDevice != deviceId) {
            // Key is registered to another device — reject
            return false
        }

        // Bind this key to the current device and activate Pro
        prefs.edit()
            .putBoolean(KEY_IS_PRO, true)
            .putString("${KEY_BOUND_DEVICE}_$clean", deviceId)
            .apply()

        return true
    }

    fun getDeviceId(): String = deviceId

    private fun generateChecksum(body: String): String {
        var sum = 0
        body.forEachIndexed { index, char ->
            val weight = index + 1
            sum += char.code * weight
        }
        val checksum = sum % 10000
        return checksum.toString().padStart(4, '0')
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
