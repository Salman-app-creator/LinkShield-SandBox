package com.linkshield.sandbox

// REPO PATH: app/src/main/java/com/linkshield/sandbox/LinkShieldApp.kt
// ← REPLACE existing file
//
// Changes vs old (repo version):
//  1. GlobalScope replaced with proper application-scoped CoroutineScope
//  2. AdBlock import fixed: repo uses lowercase "adblock" package
//  3. GrabberEngine.init() added (YoutubeDL bootstrap — must be synchronous)
//  4. GrabberEngine.updateExtractor() launched async in background

import android.app.Application
import com.linkshield.sandbox.adblock.AdBlockEngine          // ← lowercase "adblock" matches repo
import com.linkshield.sandbox.grabber.GrabberEngine
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
        GrabberEngine.init(this)

        // Async extractor update — silent failure is acceptable (offline users)
        appScope.launch(Dispatchers.IO) {
            GrabberEngine.updateExtractor(this@LinkShieldApp)
        }
    }
}
