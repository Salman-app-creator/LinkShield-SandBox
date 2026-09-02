package com.linkshield.sandbox

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

        // yt-dlp initialization can load an embedded Python runtime. Keep it
        // off the main thread so app startup / approved UI screens do not
        // inherit a white-screen delay.
        appScope.launch(Dispatchers.IO) {
            runCatching {
                YtDlpEngine.initialize(this@LinkShieldApp)
            }.onFailure {
                Log.w("LinkShieldApp", "yt-dlp initialization failed: ${it.message}")
            }
        }

        appScope.launch(Dispatchers.IO) {
            runCatching {
                AdBlockEngine.getInstance().initialize(this@LinkShieldApp)
            }.onFailure {
                Log.w("LinkShieldApp", "AdBlock initialization failed: ${it.message}")
            }
        }
    }
}
