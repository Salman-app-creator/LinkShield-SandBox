package com.linkshield.sandbox.ui.grabber

import androidx.activity.compose.BackHandler

@androidx.compose.runtime.Composable
fun GrabberBackHandler(
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }
}
