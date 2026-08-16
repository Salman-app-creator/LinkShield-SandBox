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
