package com.linkshield.sandbox.ui.grabber

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GrabberEntry(
    context: Context,
    onBack: () -> Unit
) {
    val applicationContext =
        context.applicationContext

    val viewModel: GrabberViewModel =
        viewModel(
            factory =
                GrabberViewModelFactory(
                    applicationContext
                )
        )

    val downloadHandler =
        remember(applicationContext) {
            GrabberDownloadHandler(
                applicationContext
            )
        }

    GrabberScreen(
        viewModel = viewModel,
        onBack = onBack,
        onDownload = {
            downloadHandler.download(it)
        }
    )
}
