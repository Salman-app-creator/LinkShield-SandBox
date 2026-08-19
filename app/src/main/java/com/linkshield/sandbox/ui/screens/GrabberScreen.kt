package com.linkshield.sandbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.linkshield.sandbox.ui.grabber.LinkShieldGrabberScreen

@Composable
fun GrabberScreen() {
    LinkShieldGrabberScreen(onBackToBrowser = {}, onUpgradeClick = {})
}
