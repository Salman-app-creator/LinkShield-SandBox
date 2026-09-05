package com.linkshield.sandbox

// REPO PATH: app/src/main/java/com/linkshield/sandbox/LinkShieldApp.kt

import android.app.Application
import android.util.Log
import com.linkshield.sandbox.adblock.AdBlockEngine
import com.linkshield.sandbox.ui.grabber.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LinkShieldApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // AdBlock engine
        appScope.launch(Dispatchers.IO) {
            runCatching {
                AdBlockEngine.getInstance().initialize(this@LinkShieldApp)
            }.onFailure {
                Log.w("LinkShieldApp", "AdBlock initialization failed: ${it.message}")
            }
        }

        // yt-dlp — background init, UI nahi rukti
        // Agar fail ho to first YouTube fetch pe retry hoga automatically
        appScope.launch(Dispatchers.IO) {
            runCatching {
                YtDlpEngine.initialize(this@LinkShieldApp)
            }.onFailure {
                Log.w("LinkShieldApp", "yt-dlp pre-init failed: ${it.message}")
            }
        }
    }
}
