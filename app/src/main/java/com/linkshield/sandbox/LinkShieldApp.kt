package com.linkshield.sandbox

// REPO PATH: app/src/main/java/com/linkshield/sandbox/LinkShieldApp.kt
// ← REPLACE existing file
//
// Changes vs old (repo version):
//  1. GlobalScope replaced with proper application-scoped CoroutineScope
//  2. AdBlock import fixed: repo uses lowercase "adblock" package
//  3. GrabberEngine.init() added (YoutubeDL bootstrap — must be synchronous)
//  4. GrabberEngine.updateExtractor() launched async in background
//  5. FIX: GrabberEngine import path corrected to match actual file location (ui.grabber)
//  6. FIX: GrabberEngine.init() wrapped in try-catch to prevent startup crash

import android.app.Application
import android.util.Log
import com.linkshield.sandbox.adblock.AdBlockEngine          // ← lowercase "adblock" matches repo
import com.linkshield.sandbox.ui.grabber.GrabberEngine       // ← FIX: was "grabber", now "ui.grabber"
import com.linkshield.sandbox.vpn.VpnNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LinkShieldApp : Application() {

    // Application-lifetime coroutine scope.
    // SupervisorJob → one child crash does not cancel siblings.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // ── VPN notification channel ───────────────────────────────────────────
        // Must exist before the first VPN notification fires.
        // Safe to call multiple times — Android ignores duplicate creation.
        VpnNotificationHelper.createChannel(this)

        // ── AdBlock engine — background init ──────────────────────────────────
        appScope.launch {
            AdBlockEngine.getInstance().initialize(this@LinkShieldApp)
        }

        // ── YoutubeDL (Grabber) ───────────────────────────────────────────────
        // init() MUST be synchronous on main thread before any download call.
        // FIX: Wrapped in try-catch so app doesn't crash if YoutubeDL init fails
        runCatching {
            GrabberEngine.init(this)
        }.onFailure {
            Log.e("LinkShieldApp", "GrabberEngine init failed: ${it.message}")
        }

        // Async extractor update — silent failure is acceptable (offline users)
        appScope.launch(Dispatchers.IO) {
            runCatching {
                GrabberEngine.updateExtractor(this@LinkShieldApp)
            }.onFailure {
                Log.w("LinkShieldApp", "Extractor update failed: ${it.message}")
            }
        }
    }
}
