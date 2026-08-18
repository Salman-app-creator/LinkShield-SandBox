package com.linkshield.sandbox.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
// FIX #8: Added missing ExperimentalMaterial3Api import.
// Without this import the @OptIn annotation below cannot resolve the class.
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

// FIX #8: TopAppBar (and OutlinedTextField in older M3 builds) are annotated
// @ExperimentalMaterial3Api. The original file had no @OptIn, causing a
// "This declaration is experimental and its usage must be marked with…" error.
// All other composables in this project that use TopAppBar already carry this annotation.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxBrowserScreen(
    onOpenGrabber: () -> Unit,
    onExit: () -> Unit,
    initialUrl: String = ""
) {
    val context =
        LocalContext.current

    val viewModel: SandboxBrowserViewModel =
        viewModel()

    val state by
        viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.syncFromWebView()
    }

    BackHandler {
        if (!viewModel.goBack()) {
            onExit()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.title.ifBlank {
                            "LinkShield Sandbox"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!viewModel.goBack()) {
                                onExit()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.goBack() },
                        enabled = state.canGoBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Previous page"
                        )
                    }

                    IconButton(
                        onClick = { viewModel.goForward() },
                        enabled = state.canGoForward
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            "Next page"
                        )
                    }

                    IconButton(
                        onClick = { viewModel.reload() }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            "Reload"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::updateUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, null)
                }
            )

            if (state.isLoading) {
                // FIX #12: Material3 ≥ 1.1 changed the determinate LinearProgressIndicator
                // parameter from "progress: Float" to "progress: () -> Float" (a lambda).
                // The original code passed "state.progress / 100f" (a Float literal) which
                // causes a type-mismatch compile error on the M3 version used by this project
                // (confirmed because UnblockShieldScreen.kt in the same project already uses
                // the correct lambda form). Wrapped in a lambda to match the API.
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SandboxWebViewContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                initialUrl = initialUrl,
                onUrlChanged = {
                    viewModel.updateUrl(it)
                }
            )

            Button(
                onClick = onOpenGrabber,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(52.dp)
            ) {
                Text("Open Grabber")
            }
        }
    }
}
