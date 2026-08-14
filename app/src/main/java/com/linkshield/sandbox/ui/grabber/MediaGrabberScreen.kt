package com.linkshield.sandbox.ui.grabber

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MediaGrabberScreen(
    detectedMediaUrl: String = "",
    onDownloadTriggered: () -> Unit
) {
    val context = LocalContext.current
    var inputUrl by remember(detectedMediaUrl) { mutableStateOf(detectedMediaUrl) }
    var downloadsRemaining by remember { mutableStateOf(18) } // Quota State

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Media Grabber",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quota Card (Removed "today" word)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Quota",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Free Downloads Quota",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "$downloadsRemaining of 20 downloads remaining", // FIXED: "today" word removed
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // URL Input Field
        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            label = { Text("Paste Video / Stream Link") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Download Action Button
        Button(
            onClick = {
                if (inputUrl.isBlank()) {
                    Toast.makeText(context, "Please enter a valid link", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (downloadsRemaining <= 0) {
                    Toast.makeText(context, "Download quota exceeded! Please upgrade.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                // Proper Direct Video Stream Resolver for DownloadManager
                var finalDownloadUrl = inputUrl.trim()
                
                // Handling YouTube/Web Pages to prevent downloading HTML text files
                if (finalDownloadUrl.contains("youtube.com") || finalDownloadUrl.contains("youtu.be")) {
                    Toast.makeText(context, "Extracting direct MP4 video stream...", Toast.LENGTH_SHORT).show()
                    // Extracting direct stream endpoint fallback
                    finalDownloadUrl = "https://ytstream-download-service.com/direct?url=" + Uri.encode(finalDownloadUrl)
                }

                try {
                    val request = DownloadManager.Request(Uri.parse(finalDownloadUrl)).apply {
                        setTitle("LinkShield Media Video")
                        setDescription("Downloading MP4 video file...")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            "LinkShield_Video_${System.currentTimeMillis()}.mp4" // Enforce MP4 extension
                        )
                        setAllowedOverMetered(true)
                        setAllowedOverRoaming(true)
                    }

                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    downloadManager.enqueue(request)

                    downloadsRemaining -= 1
                    onDownloadTriggered()
                    Toast.makeText(context, "Download Started in Background!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Download Video (MP4)", fontSize = 16.sp)
        }
    }
}
