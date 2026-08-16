package com.linkshield.sandbox.ui.grabber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GrabberPreviewCard(
    title: String,
    duration: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title.ifBlank {
                    "Media Preview"
                },
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            if (duration.isNotBlank()) {
                Text(
                    text = "Duration: $duration",
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )
            }
        }
    }
}
