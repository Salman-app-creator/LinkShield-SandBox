package com.linkshield.sandbox.ui.grabber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun GrabberEntry(
    viewModel: GrabberViewModel,
    onBack: () -> Unit,
    onDownload: (MediaQualityOption) -> Unit
) {
    val state by
        viewModel.uiState
            .collectAsStateWithLifecycle()

    GrabberScreen(
        viewModel = viewModel,
        onBack = onBack,
        onDownload = onDownload
    )
}
