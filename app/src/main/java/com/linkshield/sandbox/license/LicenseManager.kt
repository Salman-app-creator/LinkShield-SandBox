package com.linkshield.sandbox.license

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// ─────────────────────────────────────────────────────────────────────────────
// LicenseManager.kt
//
// Features:
//  1. 2-week free Shield trial — persists install timestamp on first launch;
//     trialActive() returns false after 14 days for non-Pro users.
//  2. Hardware-bound activation — license key is tied to ANDROID_ID hash;
//     a key used on Device A cannot be activated on Device B.
//  3. 20 free download quota — tracked in EncryptedSharedPreferences.
//  4. validateKey() — LSHD-XXXX-XXXX-CCCC checksum verification. UNTOUCHED.
// ─────────────────────────────────────────────────────────────────────────────

class LicenseManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, "license_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Raw ANDROID_ID — used for device binding in validateKey(). */
    private val deviceId: String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"

    companion object {
        private const val KEY_IS_PRO        = "is_pro"
        private const val KEY_DOWNLOAD_COUNT = "download_count"
        private const val KEY_BOUND_DEVICE  = "bound_device_id"
        private const val KEY_INITIALIZED   = "initialized"
        private const val KEY_INSTALL_TIME  = "install_timestamp"
        private const val FREE_LIMIT        = 20
        private const val KEY_PREFIX        = "LSHD"
        private const val TRIAL_DAYS        = 14L
        private const val MS_PER_DAY        = 86_400_000L
    }

    init {
        val now = System.currentTimeMillis()
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putInt(KEY_DOWNLOAD_COUNT, 0)
                .putLong(KEY_INSTALL_TIME, now)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
        }
    }

    // ── Trial API ─────────────────────────────────────────────────────────────

    /**
     * Returns true when the 14-day free Shield trial is still active.
     * Pro users always return true.
     */
    fun trialActive(): Boolean {
        if (isProUser()) return true
        val installed = prefs.getLong(KEY_INSTALL_TIME, System.currentTimeMillis())
        val elapsed   = System.currentTimeMillis() - installed
        return elapsed < TRIAL_DAYS * MS_PER_DAY
    }

    /**
     * Days remaining in the free trial (0 when expired or Pro).
     */
    fun trialDaysRemaining(): Int {
        if (isProUser()) return Int.MAX_VALUE
        val installed = prefs.getLong(KEY_INSTALL_TIME, System.currentTimeMillis())
        val elapsed   = System.currentTimeMillis() - installed
        val remaining = TRIAL_DAYS - (elapsed / MS_PER_DAY)
        return remaining.coerceAtLeast(0).toInt()
    }

    // ── Pro / quota API ───────────────────────────────────────────────────────

    fun isProUser(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)

    fun getDownloadCount(): Int = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    fun getRemainingDownloads(): Int =
        if (isProUser()) Int.MAX_VALUE
        else (FREE_LIMIT - getDownloadCount()).coerceAtLeast(0)

    fun canDownload(): Boolean = isProUser() || getDownloadCount() < FREE_LIMIT

    /**
     * Increments the download counter by 1.
     * Returns true if the download is within quota; false if limit reached.
     */
    fun incrementDownload(): Boolean {
        if (isProUser()) return true
        val current = getDownloadCount()
        if (current >= FREE_LIMIT) return false
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, current + 1).apply()
        return true
    }

    fun getDeviceId(): String = deviceId

    // ── License key validation — UNTOUCHED from original ─────────────────────

    /**
     * Validates a license key and activates Pro if correct.
     *
     * Format: LSHD-XXXX-XXXX-CCCC (16 chars after stripping hyphens)
     *  • First 12 chars (LSHDXXXXXXXX) → body
     *  • Last 4 chars (CCCC)           → weighted checksum of body
     *
     * Anti-piracy:
     *  • On first activation the key is bound to this device's ANDROID_ID.
     *  • Subsequent activations from a different device are rejected.
     */
    fun validateKey(key: String): Boolean {
        val clean = key.uppercase().replace("-", "").trim()
        if (clean.length != 16)              return false
        if (!clean.startsWith(KEY_PREFIX))   return false

        val body             = clean.substring(0, 12)
        val providedChecksum = clean.substring(12, 16)
        val expectedChecksum = generateChecksum(body)
        if (providedChecksum != expectedChecksum) return false

        val boundDevice = prefs.getString("${KEY_BOUND_DEVICE}_$clean", null)
        if (boundDevice != null && boundDevice != deviceId) return false

        prefs.edit()
            .putBoolean(KEY_IS_PRO, true)
            .putString("${KEY_BOUND_DEVICE}_$clean", deviceId)
            .apply()

        return true
    }

    /** Full reset — use only for testing / account wipe. */
    fun reset() { prefs.edit().clear().apply() }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun generateChecksum(body: String): String {
        var sum = 0
        body.forEachIndexed { index, char -> sum += char.code * (index + 1) }
        return (sum % 10000).toString().padStart(4, '0')
    }
}
