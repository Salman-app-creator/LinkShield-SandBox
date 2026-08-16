package com.linkshield.sandbox.ui.grabber

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun GrabberScreen(
    viewModel: GrabberViewModel,
    onBack: () -> Unit,
    onDownload: (MediaQualityOption) -> Unit
) {
    val state by
        viewModel.uiState.collectAsState()

    val latestMedia by
        MediaSnifferState.latestMedia.collectAsState()

    LaunchedEffect(latestMedia) {
        latestMedia?.let {
            viewModel.setLatestMedia(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Grabber")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
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
                            viewModel
                                .refreshNetworkStatus()
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            "Refresh"
                        )
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {
                Spacer(
                    Modifier.height(4.dp)
                )

                FreeDownloadsBanner()
            }

            item {
                OutlinedTextField(
                    value = state.address,
                    onValueChange =
                        viewModel::setAddress,
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text("Media URL")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Link,
                            null
                        )
                    }
                )
            }

            item {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Button(
                        onClick = {
                            viewModel.inspectUrl(
                                state.address
                            )
                        },
                        modifier =
                            Modifier.weight(1f),
                        enabled =
                            state.address.isNotBlank() &&
                                !state.isCheckingThreat
                    ) {
                        Text(
                            if (
                                state.isCheckingThreat
                            ) {
                                "Checking..."
                            } else {
                                "Check URL"
                            }
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.extractMedia(
                                state.address
                            )
                        },
                        modifier =
                            Modifier.weight(1f),
                        enabled =
                            state.address.isNotBlank() &&
                                !state.isExtracting
                    ) {
                        Text(
                            if (
                                state.isExtracting
                            ) {
                                "Fetching..."
                            } else {
                                "Fetch Media"
                            }
                        )
                    }
                }
            }

            if (
                state.isCheckingThreat ||
                state.isExpanding ||
                state.isExtracting
            ) {
                item {
                    LinearProgressIndicator(
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }
            }

            state.threat?.let { threat ->
                item {
                    ThreatCard(
                        threat = threat,
                        onDismiss =
                            viewModel::clearThreat
                    )
                }
            }

            if (
                state.thumbnail.isNotBlank() ||
                state.title.isNotBlank()
            ) {
                item {
                    MediaPreviewCard(
                        thumbnail =
                            state.thumbnail,
                        title =
                            state.title,
                        duration =
                            state.duration
                    )
                }
            }

            if (
                state.qualities.isNotEmpty()
            ) {
                item {
                    Text(
                        "Available formats",
                        style =
                            MaterialTheme.typography
                                .titleMedium
                    )
                }

                items(
                    items = state.qualities,
                    key = {
                        it.id
                    }
                ) { option ->

                    QualityOptionRow(
                        option = option,
                        selected =
                            state.selectedQualityId ==
                                option.id,
                        onClick = {
                            viewModel.selectQuality(
                                option.id
                            )
                        }
                    )
                }
            }

            item {
                val selected =
                    viewModel.latestOption()

                Button(
                    onClick = {
                        selected?.let(
                            onDownload
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    enabled =
                        selected != null
                ) {
                    Icon(
                        Icons.Default.Download,
                        null
                    )

                    Spacer(
                        Modifier.padding(
                            horizontal = 4.dp
                        )
                    )

                    Text("Download")
                }
            }

            item {
                NetworkBadge(
                    status =
                        state.network
                )
            }

            state.error?.let { error ->
                item {
                    Text(
                        error,
                        color =
                            MaterialTheme.colorScheme
                                .error
                    )
                }
            }
        }
    }
}
@Composable
private fun FreeDownloadsBanner() {
    Card(
        Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Text(
                "20 Free Downloads Remaining",
                style =
                    MaterialTheme.typography
                        .titleMedium
            )

            Text(
                "Upgrade to Pro for more downloads.",
                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }
    }
}

@Composable
private fun MediaPreviewCard(
    thumbnail: String,
    title: String,
    duration: String
) {
    Card(
        Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(12.dp)
        ) {

            if (thumbnail.isNotBlank()) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription =
                        "Media thumbnail",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                title.ifBlank {
                    "Media"
                },
                style =
                    MaterialTheme.typography
                        .titleMedium,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis
            )

            if (duration.isNotBlank()) {
                Text(
                    duration,
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }
    }
}

@Composable
private fun QualityOptionRow(
    option: MediaQualityOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            RadioButton(
                selected = selected,
                onClick = onClick
            )

            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    option.displayLabel
                )

                val details =
                    listOfNotNull(
                        option.extension
                            .takeIf {
                                it.isNotBlank()
                            },
                        option.height?.let {
                            "${it}p"
                        }
                    ).distinct()
                        .joinToString(" • ")

                if (details.isNotBlank()) {
                    Text(
                        details,
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreatCard(
    threat:
        com.linkshield.sandbox.api
            .ThreatCheckResult,
    onDismiss: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(12.dp)
        ) {

            Text(
                if (threat.isMalicious) {
                    "⚠ Dangerous URL"
                } else if (
                    threat.isSuspicious
                ) {
                    "⚠ Suspicious URL"
                } else {
                    "✓ URL Checked"
                },
                style =
                    MaterialTheme.typography
                        .titleMedium
            )

            Spacer(
                Modifier.height(4.dp)
            )

            Text(
                threat.message.ifBlank {
                    "No known threat detected."
                }
            )

            if (threat.isMalicious) {
                Spacer(
                    Modifier.height(8.dp)
                )

                Button(
                    onClick = onDismiss
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun NetworkBadge(
    status:
        com.linkshield.sandbox.api
            .NetworkStatus
) {
    Card(
        Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    if (
                        status.publicIp.isBlank()
                    ) {
                        "Network unavailable"
                    } else {
                        "IP: ${status.publicIp}"
                    }
                )

                if (
                    status.locationText
                        .isNotBlank()
                ) {
                    Text(
                        status.locationText,
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }
            }

            Text(
                if (status.encryptedDns) {
                    "DNS Shield ✓"
                } else {
                    "DNS Shield —"
                },
                style =
                    MaterialTheme.typography
                        .labelSmall
            )
        }
    }
}
