package com.linkshield.sandbox.disclaimer

import android.content.Context
import android.content.SharedPreferences

class DisclaimerManager(
    context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    companion object {

        private const val PREFS_NAME =
            "linkshield_setup"

        private const val KEY_ACCEPTED =
            "disclaimer_accepted"

        private const val KEY_BROWSER_SET =
            "browser_set"
    }

    fun hasAccepted(): Boolean =
        prefs.getBoolean(
            KEY_ACCEPTED,
            false
        )

    fun accept() {

        prefs.edit()
            .putBoolean(
                KEY_ACCEPTED,
                true
            )
            .apply()
    }

    fun hasBrowserSet(): Boolean =
        prefs.getBoolean(
            KEY_BROWSER_SET,
            false
        )

    fun markBrowserSet() {

        prefs.edit()
            .putBoolean(
                KEY_BROWSER_SET,
                true
            )
            .apply()
    }

    fun reset() {

        prefs.edit()
            .clear()
            .apply()
    }
}
