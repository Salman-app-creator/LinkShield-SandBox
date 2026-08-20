package com.linkshield.sandbox

// ─────────────────────────────────────────────────────────────────────────────
// LinkShieldApp.kt
//
// Application class — add VpnNotificationHelper.createChannel() call here.
// This ensures the notification channel exists before any notification is shown.
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Application
import com.linkshield.sandbox.adblock.AdBlockEngine
import com.linkshield.sandbox.vpn.VpnNotificationHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LinkShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Create VPN notification channel (must exist before first notification)
        // Safe to call multiple times — Android ignores duplicate channel creation.
        VpnNotificationHelper.createChannel(this)

        // Initialize AdBlock engine in background
        GlobalScope.launch {
            AdBlockEngine.getInstance().initialize(this@LinkShieldApp)
        }
    }
}
