// FIX #21: This file is a direct rename of "Grabber navigation.kt" (which had a space
// in its filename). A space in a Kotlin source filename is a latent build fragility:
// shell scripts, CI pipelines, and some AGP incremental-build cache configurations
// mis-tokenise space-containing paths. Content is 100% identical to the original.
// ACTION REQUIRED: Delete the old "Grabber navigation.kt" from your source tree after
// copying this file in.
package com.linkshield.sandbox.ui.grabber

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController

object GrabberRoutes {
    const val GRABBER = "grabber"
}

fun NavHostController.openGrabber() {
    navigate(GrabberRoutes.GRABBER) {
        launchSingleTop = true
    }
}

fun NavHostController.closeGrabber() {
    if (!popBackStack()) {
        navigateUp()
    }
}

@Composable
fun rememberGrabberDownloadManager(
    context: Context
): GrabberDownloadManager {
    return remember(context) {
        GrabberDownloadManager(
            context.applicationContext
        )
    }
}
