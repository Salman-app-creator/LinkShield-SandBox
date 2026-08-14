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
import com.linkshield.sandbox.license.LicenseManager

@Composable
fun MediaGrabberScreen(
    detectedMediaUrl: String = "",
    licenseManager: LicenseManager? = null,
    onDownloadTriggered: () -> Unit = {}
) {
    val context = LocalContext.current
    var inputUrl by remember(detectedMediaUrl) { mutableStateOf(detectedMediaUrl) }
    
    // REAL download count from LicenseManager
    val remainingDownloads = remember(licenseManager) {
        licenseManager?.getRemainingDownloads()?.coerceAtMost(20) ?: 20
    }

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

        // Quota Card with REAL count
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
                        text = "$remainingDownloads of 20 downloads remaining",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            label = { Text("Paste Video / Stream Link") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (inputUrl.isBlank()) {
                    Toast.makeText(context, "Please enter a valid link", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (remainingDownloads <= 0) {
                    Toast.makeText(context, "Download quota exceeded! Please upgrade to Pro.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                val finalDownloadUrl = inputUrl.trim()
                
                if (finalDownloadUrl.contains("youtube.com") || 
                    finalDownloadUrl.contains("youtu.be") ||
                    finalDownloadUrl.contains("facebook.com") ||
                    finalDownloadUrl.contains("instagram.com")) {
                    Toast.makeText(context, "Paste the direct MP4/MP3 link only. This site is not supported.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                try {
                    val fileName = "LinkShield_${System.currentTimeMillis()}.mp4"
                    val mimeType = when {
                        finalDownloadUrl.endsWith(".mp3", true) -> "audio/mpeg"
                        finalDownloadUrl.endsWith(".webm", true) -> "video/webm"
                        finalDownloadUrl.endsWith(".m3u8", true) -> "application/x-mpegURL"
                        else -> "video/mp4"
                    }

                    val request = DownloadManager.Request(Uri.parse(finalDownloadUrl)).apply {
                        setTitle("LinkShield Download")
                        setDescription("Downloading media file...")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            fileName
                        )
                        setAllowedOverMetered(true)
                        setAllowedOverRoaming(true)
                        setMimeType(mimeType)
                    }

                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    downloadManager.enqueue(request)

                    licenseManager?.incrementDownloadCount()
                    onDownloadTriggered()
                    Toast.makeText(context, "Download started! Check notification panel.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = remainingDownloads > 0
        ) {
            Text("Download Video (MP4)", fontSize = 16.sp)
        }
    }
}
