package com.linkshield.sandbox.ui.grabber

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrabberScreen(
    viewModel: GrabberViewModel,
    onBack: () -> Unit,
    onDownload: (MediaQualityOption) -> Unit
) {
    val state by
        viewModel.uiState.collectAsState()

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Media Grabber")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(16.dp)
                    ) {
                        Text(
                            text =
                                "[ 20 Free Downloads Remaining ]",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        Spacer(
                            Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                "Upgrade to Pro for more downloads."
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.url,
                    onValueChange =
                        viewModel::setUrl,
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text("Media URL")
                    }
                )
            }

            if (state.isLoading) {
                item {
                    LinearProgressIndicator(
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }
            }

            if (
                state.title.isNotBlank() ||
                state.duration.isNotBlank()
            ) {
                item {
                    Card(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(16.dp)
                        ) {
                            Text(
                                text =
                                    state.title
                                        .ifBlank {
                                            "Media Preview"
                                        },
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            if (
                                state.duration
                                    .isNotBlank()
                            ) {
                                Text(
                                    text =
                                        "Duration: " +
                                            state.duration
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Available Formats",
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )
            }

            items(
                items = state.qualities,
                key = {
                    it.url
                }
            ) { option ->

                QualityOptionRow(
                    option = option,
                    selected =
                        state.selectedQuality
                            ?.url ==
                            option.url,
                    onSelect = {
                        viewModel
                            .selectQuality(
                                option
                            )
                    }
                )
            }

            item {
                Button(
                    onClick = {
                        state.selectedQuality
                            ?.let(onDownload)
                    },
                    enabled =
                        state.selectedQuality !=
                            null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                ) {
                    Text("Download")
                }
            }

            state.error?.let { message ->
                item {
                    Text(
                        text = message,
                        color =
                            MaterialTheme
                                .colorScheme
                                .error
                    )
                }
            }
        }
    }
}
@Composable
private fun QualityOptionRow(
    option: MediaQualityOption,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text =
                        option.label
                            .ifBlank {
                                option.quality
                            }
                )

                if (
                    option.mimeType
                        .isNotBlank()
                ) {
                    Text(
                        text =
                            option.mimeType,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }
            }
        }
    }
}
