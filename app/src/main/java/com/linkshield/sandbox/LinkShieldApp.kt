package com.linkshield.sandbox

import android.app.Application
import android.util.Log
import com.linkshield.sandbox.adblock.AdBlockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LinkShieldApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        appScope.launch(Dispatchers.IO) {
            runCatching {
                AdBlockEngine.getInstance().initialize(this@LinkShieldApp)
            }.onFailure {
                Log.w("LinkShieldApp", "AdBlock initialization failed: ${it.message}")
            }
        }
    }
}
