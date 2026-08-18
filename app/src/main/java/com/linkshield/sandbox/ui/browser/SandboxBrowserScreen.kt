package com.linkshield.sandbox.ui.browser

import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxBrowserScreen(
    onOpenGrabber: () -> Unit,
    onExit: () -> Unit,
    initialUrl: String = ""
) {
    val context =
        LocalContext.current

    val viewModel:
        SandboxBrowserViewModel =
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
                            if (
                                !viewModel.goBack()
                            ) {
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
                        onClick = {
                            viewModel.goBack()
                        },
                        enabled =
                            state.canGoBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Previous page"
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.goForward()
                        },
                        enabled =
                            state.canGoForward
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            "Next page"
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.reload()
                        }
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
                    OutlinedTextField(
                value = state.url,
                onValueChange =
                    viewModel::updateUrl,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp
                        ),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        null
                    )
                }
            )

            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = {
                        state.progress / 100f
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

            SandboxWebViewContainer(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                initialUrl =
                    initialUrl,
                onUrlChanged = {
                    viewModel.updateUrl(it)
                }
            )

            Button(
                onClick =
                    onOpenGrabber,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp
                        )
                        .height(52.dp)
            ) {
                Text(
                    "Open Grabber"
                )
            }
        }
    }
}
