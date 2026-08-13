package com.linkshield.sandbox.disclaimer

import android.content.Context
import android.content.SharedPreferences

// ─────────────────────────────────────────────────────────────────────────────
// DisclaimerManager.kt  — package com.linkshield.sandbox.disclaimer
//
// Persists whether the user has accepted the first-launch disclaimer.
// MainActivity creates one instance and passes it to LinkShieldApp.
// ─────────────────────────────────────────────────────────────────────────────

class DisclaimerManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("disclaimer_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCEPTED = "disclaimer_accepted"
    }

    /** True when the user has already accepted the disclaimer on a previous launch. */
    fun hasAccepted(): Boolean = prefs.getBoolean(KEY_ACCEPTED, false)

    /** Persists acceptance. Call this when the user taps Accept. */
    fun accept() {
        prefs.edit().putBoolean(KEY_ACCEPTED, true).apply()
    }

    /** Resets the disclaimer — useful for testing. */
    fun reset() {
        prefs.edit().remove(KEY_ACCEPTED).apply()
    }
}
