package com.linkshield.sandbox

import android.app.Application
import com.linkshield.sandbox.adblock.AdBlockEngine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LinkShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize AdBlockEngine in background so it's ready when WebView loads
        GlobalScope.launch {
            AdBlockEngine.getInstance().initialize(this@LinkShieldApp)
        }
    }
}
