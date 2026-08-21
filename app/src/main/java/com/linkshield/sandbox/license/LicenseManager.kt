package com.linkshield.sandbox.license

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LicenseManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(LICENSE_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val TAG               = "LicenseManager"
        private const val LICENSE_PREFS     = "license_prefs"
        private const val KEY_IS_PRO        = "is_pro_activated"
        private const val KEY_INSTALL_DATE  = "install_date"
        private const val KEY_DOWNLOAD_COUNT= "download_count"
        private const val KEY_USED_KEYS     = "used_keys"
        private const val KEY_FIRST_LAUNCH  = "first_launch_complete"
        private const val KEY_CACHED_HASHES = "cached_valid_hashes"

        private const val TRIAL_DAYS         = 7L
        private const val FREE_DOWNLOAD_LIMIT= 20

        private const val GITHUB_JSON_URL =
            "https://raw.githubusercontent.com/Salman-app-creator/LinkShield-SandBox/refs/heads/main/Licenses.json"
    }

    init {
        if (!prefs.contains(KEY_INSTALL_DATE)) {
            prefs.edit().putLong(KEY_INSTALL_DATE, System.currentTimeMillis()).apply()
        }
    }

    // ── Basic state ──────────────────────────────────────────────────────────

    fun isFirstLaunchComplete(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, false)
    fun setFirstLaunchComplete()         { prefs.edit().putBoolean(KEY_FIRST_LAUNCH, true).apply() }

    fun isProUser(): Boolean = prefs.getBoolean(KEY_IS_PRO, false)

    fun getInstallDate(): Long = prefs.getLong(KEY_INSTALL_DATE, System.currentTimeMillis())

    fun getDaysSinceInstall(): Long {
        val diff = System.currentTimeMillis() - getInstallDate()
        return TimeUnit.MILLISECONDS.toDays(diff)
    }

    fun getTrialDaysRemaining(): Int =
        (TRIAL_DAYS - getDaysSinceInstall()).coerceAtLeast(0).toInt()

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

    fun canDownload(): Boolean =
        isProUser() || (isTrialActive() && getDownloadCount() < FREE_DOWNLOAD_LIMIT)

    fun canUseFullShield(): Boolean = isProUser() || isTrialActive()

    fun isAccessAllowed(): Boolean =
        isProUser() || isTrialActive() || getDownloadCount() < FREE_DOWNLOAD_LIMIT

    fun getRestrictionReason(): String = when {
        isProUser() -> ""
        !isTrialActive() && getDownloadCount() >= FREE_DOWNLOAD_LIMIT ->
            "Trial ended and download limit reached. Upgrade to Pro for unlimited access."
        !isTrialActive() ->
            "Your 7-day trial has ended. Upgrade to Pro to continue using all features."
        getDownloadCount() >= FREE_DOWNLOAD_LIMIT ->
            "You have used all 20 free downloads. Upgrade to Pro for unlimited downloads."
        else -> ""
    }

    fun getStatusBadgeText(): String = when {
        isProUser()     -> "PRO UNLOCKED"
        isTrialActive() -> "TRIAL: ${getTrialDaysRemaining()}d left"
        getDownloadCount() >= FREE_DOWNLOAD_LIMIT -> "DL LIMIT REACHED"
        else            -> "TRIAL: ${getTrialDaysRemaining()}d | ${getRemainingDownloads()} DLs"
    }

    fun getUsedKeysCount(): Int =
        (prefs.getStringSet(KEY_USED_KEYS, mutableSetOf()) ?: mutableSetOf()).size

    // ── SHA-256 hashing ──────────────────────────────────────────────────────

    // FIX: Explicit UTF-8 encoding so hash is identical on all devices
    private fun hashKey(key: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── Key validation ───────────────────────────────────────────────────────

    suspend fun validateKey(key: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = key.trim().uppercase()
        if (trimmed.isEmpty()) return@withContext false
        if (isProUser()) return@withContext true

        val inputHash = hashKey(trimmed)
        Log.d(TAG, "Validating key: $trimmed  hash: $inputHash")

        // Step 1: Try remote GitHub validation
        val remoteResult = runCatching { fetchAndValidate(inputHash) }
        if (remoteResult.isSuccess && remoteResult.getOrDefault(false)) {
            activatePro(trimmed)
            return@withContext true
        }

        // Step 2: If network failed, try local cache
        val cachedHashes = prefs.getStringSet(KEY_CACHED_HASHES, emptySet()) ?: emptySet()
        if (cachedHashes.isNotEmpty()) {
            Log.w(TAG, "GitHub unreachable — using locally cached hashes (${cachedHashes.size})")
            if (cachedHashes.contains(inputHash.lowercase())) {
                activatePro(trimmed)
                return@withContext true
            }
        }

        Log.e(TAG, "Key invalid or GitHub fetch failed.")
        return@withContext false
    }

    private suspend fun fetchAndValidate(inputHash: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = URL("$GITHUB_JSON_URL?t=${System.currentTimeMillis()}")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout    = 12_000
                setRequestProperty("User-Agent", "Mozilla/5.0 LinkShieldAndroid")
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
            }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "GitHub HTTP $code")
                connection.disconnect()
                return@withContext false
            }

            val jsonString = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .trim()
            connection.disconnect()

            if (jsonString.isEmpty()) {
                Log.e(TAG, "Empty response from GitHub")
                return@withContext false
            }

            Log.d(TAG, "GitHub JSON length: ${jsonString.length}")

            // Parse — handle both plain array and {valid_keys:[...]} object
            val remoteHashes = mutableSetOf<String>()
            when {
                jsonString.startsWith("[") -> {
                    val arr = JSONArray(jsonString)
                    for (i in 0 until arr.length()) remoteHashes.add(arr.getString(i).trim().lowercase())
                }
                jsonString.startsWith("{") -> {
                    val obj = JSONObject(jsonString)
                    val arr = obj.optJSONArray("valid_keys") ?: JSONArray()
                    for (i in 0 until arr.length()) remoteHashes.add(arr.getString(i).trim().lowercase())
                }
                else -> {
                    Log.e(TAG, "Unknown JSON format")
                    return@withContext false
                }
            }

            Log.d(TAG, "Loaded ${remoteHashes.size} hashes from GitHub")

            // Cache the hashes locally for offline fallback
            if (remoteHashes.isNotEmpty()) {
                prefs.edit().putStringSet(KEY_CACHED_HASHES, remoteHashes).apply()
            }

            remoteHashes.contains(inputHash.lowercase())
        }

    private fun activatePro(key: String) {
        val usedKeys = prefs.getStringSet(KEY_USED_KEYS, mutableSetOf()) ?: mutableSetOf()
        prefs.edit()
            .putBoolean(KEY_IS_PRO, true)
            .putStringSet(KEY_USED_KEYS, usedKeys.toMutableSet().apply { add(key) })
            .apply()
        Log.d(TAG, "Pro activated for key: $key")
    }

    fun resetForTesting() { prefs.edit().clear().apply() }
}
