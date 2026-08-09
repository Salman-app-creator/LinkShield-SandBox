package com.linkshield.sandbox.license

import android.content.Context
import android.content.SharedPreferences
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

    companion object {
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_DOWNLOAD_COUNT = "download_count"
        private const val FREE_LIMIT = 20
        private const val KEY_PREFIX = "LSHD"
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
        val isValid = providedChecksum == expectedChecksum
        if (isValid) {
            prefs.edit().putBoolean(KEY_IS_PRO, true).apply()
        }
        return isValid
    }

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
     * Online validation via Blogger. Internet required.
     */
    suspend fun validateKeyOnline(key: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(BLOGGER_KEYS_URL)
                .header("Accept", "text/html")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: ${response.code}"))
                }

                val html = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response"))

                // JSON extract karo <pre id="keys-data"> se
                val regex = Regex("""<pre id="keys-data">(.*?)</pre>""", RegexOption.DOT_MATCHES_ALL)
                val match = regex.find(html)
                    ?: return@withContext Result.failure(IOException("Key database not found"))

                val jsonStr = match.groupValues[1].trim()
                val json = JSONObject(jsonStr)
                val validKeys = json.getJSONArray("valid_keys")

                val cleanKey = key.uppercase().trim().replace("-", "")

                var isValid = false
                for (i in 0 until validKeys.length()) {
                    val serverKey = validKeys.getString(i).replace("-", "").trim()
                    if (serverKey == cleanKey) {
                        isValid = true
                        break
                    }
                }

                if (isValid) {
                    prefs.edit().putBoolean(KEY_IS_PRO, true).apply()
                }

                Result.success(isValid)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}
