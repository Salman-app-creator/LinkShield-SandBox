package com.linkshield.sandbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GrabberScreen() {
    var urlText by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("1080p MP4") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Media Grabber", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // URL Input
        OutlinedTextField(
            value = urlText, onValueChange = { urlText = it },
            label = { Text("Paste Video Link") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Visual Media Preview Box
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quality Selectors
        Text("Select Quality", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1080p MP4", "720p MP4", "Audio MP3").forEach { format ->
                FilterChip(
                    selected = selectedFormat == format,
                    onClick = { selectedFormat = format },
                    label = { Text(format, fontSize = 11.sp) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Download ($selectedFormat)")
        }
    }
}
