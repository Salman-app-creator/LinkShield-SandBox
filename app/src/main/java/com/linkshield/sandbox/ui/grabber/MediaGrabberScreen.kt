package com.linkshield.sandbox.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGrabberScreen(initialUrl: String?) {
    val context = LocalContext.current
    var inputUrl by remember { mutableStateOf(initialUrl ?: "") }
    var selectedQuality by remember { mutableStateOf("1080p (HD)") }
    var expandedQualityMenu by remember { mutableStateOf(false) }

    // FIXED: Free Limit is now 20 Downloads
    val totalFreeQuota = 20
    var remainingFreeDownloads by remember { mutableIntStateOf(20) }

    val qualities = listOf("1080p (HD)", "720p (SD)", "480p (Low)", "MP3 (Audio Only)")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Media Grabber",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- Free Downloads Counter Card (20 Downloads) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Quota",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Free Downloads Quota",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { remainingFreeDownloads.toFloat() / totalFreeQuota },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$remainingFreeDownloads of $totalFreeQuota downloads remaining today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manual Link Input
        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            label = { Text("Paste Video / Stream Link") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Quality Menu
        ExposedDropdownMenuBox(
            expanded = expandedQualityMenu,
            onExpandedChange = { expandedQualityMenu = !expandedQualityMenu }
        ) {
            OutlinedTextField(
                value = selectedQuality,
                onValueChange = {},
                readOnly = true,
                label = { Text("Quality / Format") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedQualityMenu) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expandedQualityMenu,
                onDismissRequest = { expandedQualityMenu = false }
            ) {
                qualities.forEach { quality ->
                    DropdownMenuItem(
                        text = { Text(quality) },
                        onClick = {
                            selectedQuality = quality
                            expandedQualityMenu = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // REAL DOWNLOAD TRIGGER BUTTON
        Button(
            onClick = {
                val targetUrl = inputUrl.trim()
                if (targetUrl.isNotBlank() && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
                    if (remainingFreeDownloads > 0) {
                        try {
                            // REAL SYSTEM DOWNLOAD MANAGER TRIGGER
                            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            val uri = Uri.parse(targetUrl)
                            val request = DownloadManager.Request(uri).apply {
                                setTitle("Downloading LinkShield Media")
                                setDescription("Downloading $selectedQuality file...")
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    "LinkShield_${System.currentTimeMillis()}.${if (selectedQuality.contains("MP3")) "mp3" else "mp4"}"
                                )
                                setAllowedOverMetered(true)
                                setAllowedOverRoaming(true)
                            }

                            downloadManager.enqueue(request)
                            remainingFreeDownloads--
                            Toast.makeText(context, "Download started! Check notifications.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Download error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Free daily limit reached! Upgrade to Pro for unlimited downloads.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Please enter a valid HTTP/HTTPS link!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = "Download")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download Stream")
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Captured Streams Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Auto-Captured Media Stream",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (!initialUrl.isNullOrEmpty()) {
                    Text(
                        text = initialUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No stream captured yet. Play a video in the Shield browser to auto-detect links.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
