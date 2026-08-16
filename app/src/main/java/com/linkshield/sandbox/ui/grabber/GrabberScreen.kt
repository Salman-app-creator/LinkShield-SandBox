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
    val state by viewModel.uiState.collectAsState()
    val latestMedia by viewModel.latestMedia.collectAsState()

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
                            imageVector =
                                Icons.Default.ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.refreshNetworkStatus()
                        }
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Refresh,
                            contentDescription =
                                "Refresh network status"
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
                    modifier =
                        Modifier.height(4.dp)
                )

                FreeDownloadsBanner()

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )
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
                            imageVector =
                                Icons.Default.Link,
                            contentDescription =
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
                            if (state.isCheckingThreat) {
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
                            if (state.isExtracting) {
                                "Extracting..."
                            } else {
                                "Fetch Media"
                            }
                        )
                    }
                }
            }

            if (
                state.isCheckingThreat ||
                state.isExpanding
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

            if (state.qualities.isNotEmpty()) {
                item {
                    Text(
                        text = "Available formats",
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
                        imageVector =
                            Icons.Default.Download,
                        contentDescription =
                            null
                    )

                    Spacer(
                        modifier =
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
                        text = error,
                        color =
                            MaterialTheme.colorScheme
                                .error,
                        style =
                            MaterialTheme.typography
                                .bodyMedium
                    )
                }
            }

            item {
                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )
            }
        }
    }
}
@Composable
private fun FreeDownloadsBanner() {
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
                    "20 Free Downloads Remaining",
                style =
                    MaterialTheme.typography
                        .titleMedium
            )

            Text(
                text =
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
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Column(
            modifier =
                Modifier.padding(12.dp)
        ) {
            if (thumbnail.isNotBlank()) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = "Media thumbnail",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
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
                    text = duration,
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
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Row(
            modifier =
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
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text =
                        option.displayLabel,
                    style =
                        MaterialTheme.typography
                            .bodyLarge
                )

                val details =
                    listOfNotNull(
                        option.extension
                            .takeIf {
                                it.isNotBlank()
                            },
                        option.mimeType
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
                        text = details,
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
    threat: com.linkshield.sandbox.api.ThreatCheckResult,
    onDismiss: () -> Unit
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Column(
            modifier =
                Modifier.padding(12.dp)
        ) {
            Text(
                text =
                    if (threat.isMalicious) {
                        "⚠ Dangerous URL"
                    } else if (threat.isSuspicious) {
                        "⚠ Suspicious URL"
                    } else {
                        "✓ URL checked"
                    },
                style =
                    MaterialTheme.typography
                        .titleMedium,
                color =
                    if (
                        threat.isMalicious ||
                        threat.isSuspicious
                    ) {
                        MaterialTheme.colorScheme
                            .error
                    } else {
                        MaterialTheme.colorScheme
                            .primary
                    }
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    threat.message.ifBlank {
                        "No known threat detected."
                    }
            )

            if (threat.source.isNotBlank()) {
                Text(
                    text =
                        "Source: ${threat.source}",
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }

            if (threat.isMalicious) {
                Spacer(
                    modifier =
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
        com.linkshield.sandbox.api.NetworkStatus
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text =
                        if (status.publicIp.isBlank()) {
                            "Network status unavailable"
                        } else {
                            "IP: ${status.publicIp}"
                        },
                    style =
                        MaterialTheme.typography
                            .bodyMedium
                )

                if (
                    status.locationText
                        .isNotBlank()
                ) {
                    Text(
                        text =
                            status.locationText,
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }
            }

            Text(
                text =
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
