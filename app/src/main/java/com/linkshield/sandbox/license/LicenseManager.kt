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

    fun validateKey(key: String): Boolean {
        val clean = key.uppercase().replace("-", "").trim()
        if (clean.length != 16) return false
        if (!clean.startsWith(KEY_PREFIX)) return false

        val body = clean.substring(0, 12)
        val providedChecksum = clean.substring(12, 16)
        val expectedChecksum = generateChecksum(body)

        if (providedChecksum != expectedChecksum) return false

        val boundDevice = prefs.getString("${KEY_BOUND_DEVICE}_$clean", null)
        if (boundDevice != null && boundDevice != deviceId) {
            return false
        }

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
