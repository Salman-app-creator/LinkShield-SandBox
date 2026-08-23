package com.linkshield.sandbox.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val updateAvailable: Boolean
)

class UpdateChecker(private val context: Context) {

    companion object {
        private const val VERSION_URL =
            "https://raw.githubusercontent.com/Salman-app-creator/LinkShield-SandBox/main/version.json"
    }

    suspend fun checkForUpdate(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$VERSION_URL?t=${System.currentTimeMillis()}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod  = "GET"
                connectTimeout = 8_000
                readTimeout    = 8_000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${conn.responseCode}")
            }
            val json    = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            val latest  = json.getString("version")
            val dlUrl   = json.getString("url")
            val current = getCurrentVersion()
            UpdateInfo(
                latestVersion   = latest,
                currentVersion  = current,
                downloadUrl     = dlUrl,
                updateAvailable = isNewer(latest, current)
            )
        }
    }

    private fun getCurrentVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        return try {
            val l = latest.split(".").map { it.toInt() }
            val c = current.split(".").map { it.toInt() }
            for (i in 0 until maxOf(l.size, c.size)) {
                val lv = l.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (lv > cv) return true
                if (lv < cv) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
