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

    private val appScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    override fun onCreate() {
        super.onCreate()

        // VPN notification channel
        runCatching {
            VpnNotificationHelper.createChannel(this)
        }.onFailure {
            Log.e(
                "LinkShieldApp",
                "VPN notification channel failed",
                it
            )
        }

        // ---------------------------------------------------------
        // AdBlock
        // IMPORTANT:
        // Never allow AdBlock initialization to crash the app.
        // ---------------------------------------------------------

        appScope.launch {

            runCatching {

                AdBlockEngine
                    .getInstance()
                    .initialize(
                        this@LinkShieldApp
                    )

            }.onFailure {

                Log.e(
                    "LinkShieldApp",
                    "AdBlock initialization failed",
                    it
                )
            }
        }

        // ---------------------------------------------------------
        // Grabber / yt-dlp
        // ---------------------------------------------------------

        runCatching {

            GrabberEngine.init(this)

        }.onFailure {

            Log.e(
                "LinkShieldApp",
                "GrabberEngine initialization failed",
                it
            )
        }

        // ---------------------------------------------------------
        // yt-dlp extractor update
        // ---------------------------------------------------------

        appScope.launch(Dispatchers.IO) {

            runCatching {

                GrabberEngine.updateExtractor(
                    this@LinkShieldApp
                )

            }.onFailure {

                Log.w(
                    "LinkShieldApp",
                    "Extractor update failed",
                    it
                )
            }
        }
    }
}
