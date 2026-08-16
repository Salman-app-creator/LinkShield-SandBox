package com.linkshield.sandbox.ui.grabber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GrabberOptions(
    options: List<MediaQualityOption>,
    selected: MediaQualityOption?,
    onSelected: (MediaQualityOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement =
                Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected =
                            selected?.url ==
                                option.url,
                        onClick = {
                            onSelected(option)
                        }
                    )

                    Text(
                        text =
                            option.displayLabel
                    )
                }
            }
        }
    }
}
