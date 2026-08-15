package com.linkshield.sandbox

import android.app.Application
import com.linkshield.sandbox.adblock.AdBlockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// LinkShieldApp.kt
//
// AdBlockEngine is initialized here via a background coroutine so:
//   • The main thread never blocks — no ANR risk.
//   • By the time the user opens their first webpage (~2-3s after launch),
//     the engine is already ready.
//   • If the app launches and immediately tries to block a request before
//     the engine is ready, AdBlockEngine.shouldBlock() returns false safely
//     (the isInitialized AtomicBoolean guard inside the engine handles this).
// ─────────────────────────────────────────────────────────────────────────────

class LinkShieldApp : Application() {

    // Application-scoped coroutine scope — lives as long as the process
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Initialize AdBlockEngine in the background — non-blocking
        appScope.launch {
            AdBlockEngine.getInstance().initialize(applicationContext)
        }
    }
}
