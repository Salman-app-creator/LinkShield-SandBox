package com.linkshield.sandbox.ui.browser

import androidx.navigation.NavHostController
import com.linkshield.sandbox.ui.grabber.GrabberRoutes

fun NavHostController.openGrabberFromBrowser() {
    navigate(GrabberRoutes.GRABBER) {
        launchSingleTop = true
    }
}

fun NavHostController.returnToBrowserFromGrabber() {
    popBackStack()
}
