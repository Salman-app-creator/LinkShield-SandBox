package com.linkshield.sandbox.disclaimer

import android.content.Context
import android.content.SharedPreferences

// ─────────────────────────────────────────────────────────────────────────────
// DisclaimerManager.kt
//
// Tracks two one-time first-launch flags in SharedPreferences:
//   1. disclaimer_accepted  → user tapped "Accept & Continue" on Screen 1
//   2. browser_set          → user completed "Enable Shield" step on Screen 2
//      (we set this ONLY after we confirm the app IS the default browser)
//
// Flow enforced by MainActivity:
//   disclaimer accepted?  NO  → show DisclaimerScreen
//         ↓ YES
//   default browser set?  NO  → show EnableShieldScreen (CANNOT be skipped)
//         ↓ YES
//   show MainScreen (tabs)
// ─────────────────────────────────────────────────────────────────────────────

class DisclaimerManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME          = "disclaimer_prefs"
        private const val KEY_DISCLAIMER      = "disclaimer_accepted"
        private const val KEY_BROWSER_SET     = "default_browser_set"
    }

    // ── Disclaimer (Screen 1) ─────────────────────────────────────────────────

    /** True when the user has already tapped "Accept & Continue". */
    fun hasAccepted(): Boolean = prefs.getBoolean(KEY_DISCLAIMER, false)

    /** Call when the user taps "Accept & Continue". */
    fun accept() {
        prefs.edit().putBoolean(KEY_DISCLAIMER, true).apply()
    }

    // ── Default browser gate (Screen 2) ──────────────────────────────────────

    /**
     * True when we have previously confirmed the app is the default browser.
     * Note: we re-check the ACTUAL system default at runtime in MainActivity
     * because the user can revoke default browser status from system settings.
     */
    fun isBrowserSet(): Boolean = prefs.getBoolean(KEY_BROWSER_SET, false)

    /** Call after confirming the app is set as default browser. */
    fun markBrowserSet() {
        prefs.edit().putBoolean(KEY_BROWSER_SET, true).apply()
    }

    /** Reset for testing — clears both flags. */
    fun reset() {
        prefs.edit().clear().apply()
    }
}
