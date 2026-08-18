package com.linkshield.sandbox.ui.grabber

import androidx.compose.runtime.Composable
// FIX #16: Removed two now-unused imports:
//   – "androidx.compose.runtime.getValue"          (was only needed for delegate "by")
//   – "androidx.lifecycle.compose.collectAsStateWithLifecycle" (call site deleted below)

@Composable
fun GrabberEntry(
    viewModel: GrabberViewModel,
    onBack: () -> Unit,
    onDownload: (MediaQualityOption) -> Unit
) {
    // FIX #16: Original code collected "val state by viewModel.uiState
    // .collectAsStateWithLifecycle()" but the resulting `state` variable was
    // never referenced anywhere in the function body – the raw viewModel was
    // passed directly to GrabberScreen. The unused collection triggered a
    // Kotlin "Variable 'state' is never used" warning, which becomes a
    // compile error when the project enables allWarningsAsErrors / -Werror.
    // Removed the dead collection entirely; behaviour is unchanged.
    GrabberScreen(
        viewModel = viewModel,
        onBack = onBack,
        onDownload = onDownload
    )
}
