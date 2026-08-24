package com.linkshield.sandbox

import android.app.Application
import android.util.Log
import com.linkshield.sandbox.adblock.AdBlockEngine
import com.linkshield.sandbox.ui.grabber.GrabberEngine
import com.linkshield.sandbox.vpn.VpnNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LinkShieldApp : Application() {

    /*
     * Application lifetime coroutine scope.
     *
     * SupervisorJob ensures that a failure in one background
     * initialization task does not cancel the other tasks.
     */
    private val appScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    override fun onCreate() {
        super.onCreate()

        /*
         * VPN notification channel.
         *
         * This is lightweight and safe to create during startup.
         */
        VpnNotificationHelper.createChannel(this)

        /*
         * AdBlock initialization runs away from the main UI thread.
         *
         * This prevents first-run Compose rendering from being
         * unnecessarily blocked.
         */
        appScope.launch(Dispatchers.IO) {

            runCatching {

                AdBlockEngine
                    .getInstance()
                    .initialize(this@LinkShieldApp)

            }.onFailure {

                Log.w(
                    "LinkShieldApp",
                    "AdBlock initialization failed: ${it.message}"
                )
            }
        }

        /*
         * YoutubeDL / Grabber initialization is intentionally
         * asynchronous.
         *
         * It must NOT delay the Disclaimer screen.
         */
        appScope.launch(Dispatchers.IO) {

            runCatching {

                GrabberEngine.init(
                    this@LinkShieldApp
                )

                /*
                 * Extractor update happens only after the
                 * engine has successfully initialized.
                 */
                GrabberEngine.updateExtractor(
                    this@LinkShieldApp
                )

            }.onFailure {

                /*
                 * Grabber failure must not crash the application
                 * or prevent onboarding from appearing.
                 */
                Log.w(
                    "LinkShieldApp",
                    "Grabber bootstrap failed: ${it.message}"
                )
            }
        }
    }
}
