package com.linkshield.sandbox.license

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

class LicenseManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(LICENSE_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val LICENSE_PREFS = "license_prefs"
        private const val KEY_IS_PRO = "is_pro_activated"
        private const val KEY_INSTALL_DATE = "install_date"
        private const val KEY_DOWNLOAD_COUNT = "download_count"
        private const val KEY_USED_KEYS = "used_keys"
        private const val KEY_FIRST_LAUNCH = "first_launch_complete"

        private const val TRIAL_DAYS = 7L
        private const val FREE_DOWNLOAD_LIMIT = 20

        private val VALID_KEYS = setOf(
            "LSHD-ABCD-1234-5678",
            "LSHD-EFGH-9012-3456",
            "LSHD-IJKL-3456-7890",
            "LSHD-MNOP-5678-9012",
            "LSHD-QRST-7890-1234",
            "LSHD-UVWX-9012-3456",
            "LSHD-YZAB-1234-5678",
            "LSHD-CDEF-3456-7890",
            "LSHD-GHIJ-5678-9012",
            "LSHD-KLMN-7890-1234"
        )
    }

    init {
        if (!prefs.contains(KEY_INSTALL_DATE)) {
            prefs.edit().putLong(KEY_INSTALL_DATE, System.currentTimeMillis()).apply()
        }
    }

    fun isFirstLaunchComplete(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, false)

    fun setFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, true).apply()
    }

    fun isProUser(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)

    fun getInstallDate(): Long = prefs.getLong(KEY_INSTALL_DATE, System.currentTimeMillis())

    fun getDaysSinceInstall(): Long {
        val diff = System.currentTimeMillis() - getInstallDate()
        return TimeUnit.MILLISECONDS.toDays(diff)
    }

    fun getTrialDaysRemaining(): Int {
        val days = TRIAL_DAYS - getDaysSinceInstall()
        return days.coerceAtLeast(0).toInt()
    }

    fun isTrialActive(): Boolean = getTrialDaysRemaining() > 0

    fun getDownloadCount(): Int = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    fun incrementDownloadCount(): Boolean {
        if (isProUser()) return true
        val current = getDownloadCount()
        if (current >= FREE_DOWNLOAD_LIMIT) return false
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, current + 1).apply()
        return true
    }

    fun getRemainingDownloads(): Int {
        if (isProUser()) return Int.MAX_VALUE
        return (FREE_DOWNLOAD_LIMIT - getDownloadCount()).coerceAtLeast(0)
    }

    fun canDownload(): Boolean = isProUser() || (isTrialActive() && getDownloadCount() < FREE_DOWNLOAD_LIMIT)

    fun canUseFullShield(): Boolean = isProUser() || isTrialActive()

    fun isAccessAllowed(): Boolean {
        return isProUser() || isTrialActive() || getDownloadCount() < FREE_DOWNLOAD_LIMIT
    }

    fun getRestrictionReason(): String {
        return when {
            isProUser() -> ""
            !isTrialActive() && getDownloadCount() >= FREE_DOWNLOAD_LIMIT ->
                "Trial ended and download limit reached. Upgrade to Pro for unlimited access."
            !isTrialActive() ->
                "Your 7-days trial has ended. Upgrade to Pro to continue using all features."
            getDownloadCount() >= FREE_DOWNLOAD_LIMIT ->
                "You have used all 20 free downloads. Upgrade to Pro for unlimited downloads."
            else -> ""
        }
    }

    fun getStatusBadgeText(): String {
        return when {
            isProUser() -> "PRO UNLOCKED"
            isTrialActive() -> "TRIAL: ${getTrialDaysRemaining()}d left"
            getDownloadCount() >= FREE_DOWNLOAD_LIMIT -> "DL LIMIT REACHED"
            else -> "TRIAL: ${getTrialDaysRemaining()}d | ${getRemainingDownloads()} DLs"
        }
    }

    fun validateKey(key: String): Boolean {
        val trimmed = key.trim().uppercase()
        if (!VALID_KEYS.contains(trimmed)) return false

        val usedKeys = prefs.getStringSet(KEY_USED_KEYS, mutableSetOf()) ?: mutableSetOf()
        if (usedKeys.contains(trimmed)) return false

        prefs.edit()
            .putBoolean(KEY_IS_PRO, true)
            .putStringSet(KEY_USED_KEYS, usedKeys.toMutableSet().apply { add(trimmed) })
            .apply()

        return true
    }

    fun getUsedKeysCount(): Int {
        return (prefs.getStringSet(KEY_USED_KEYS, mutableSetOf()) ?: mutableSetOf()).size
    }

    fun resetForTesting() {
        prefs.edit().clear().apply()
    }
}
