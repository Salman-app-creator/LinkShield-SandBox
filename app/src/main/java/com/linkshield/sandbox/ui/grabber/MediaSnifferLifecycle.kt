package com.linkshield.sandbox.ui.grabber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun MediaSnifferLifecycle(
    onResume: () -> Unit,
    onPause: () -> Unit = {}
) {
    val lifecycleOwner =
        LocalLifecycleOwner.current

    DisposableEffect(
        lifecycleOwner
    ) {
        val observer =
            LifecycleEventObserver {
                    _, event ->

                when (event) {
                    Lifecycle.Event.ON_RESUME ->
                        onResume()

                    Lifecycle.Event.ON_PAUSE ->
                        onPause()

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle
            .addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle
                .removeObserver(observer)
        }
    }
}
