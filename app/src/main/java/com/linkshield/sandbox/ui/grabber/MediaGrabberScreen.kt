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
import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import kotlinx.coroutines.launch

@Composable
fun MediaGrabberScreen(
    detectedMediaUrl: String = "",
    licenseManager: LicenseManager? = null,
    dnsManager: DnsManager? = null,
    onDownloadTriggered: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputUrl by remember(detectedMediaUrl) { mutableStateOf(detectedMediaUrl) }
    
    var remainingDownloads by remember(licenseManager) {
        mutableIntStateOf(licenseManager?.getRemainingDownloads()?.coerceAtMost(20) ?: 20)
    }
    
    var isLoading by remember { mutableStateOf(false) }

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

                isLoading = true
                
                scope.launch {
                    try {
                        val cobalt = CobaltApiService(context, dnsManager ?: DnsManager(context))
                        val result = cobalt.fetchMediaUrl(inputUrl.trim())
                        
                        if (result.success && result.url != null) {
                            val request = DownloadManager.Request(Uri.parse(result.url)).apply {
                                setTitle(result.filename ?: "LinkShield_Download")
                                setDescription("Downloading via LinkShield")
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    result.filename ?: "LinkShield_Video.mp4"
                                )
                                setAllowedOverMetered(true)
                                setAllowedOverRoaming(true)
                            }
                            
                            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            downloadManager.enqueue(request)
                            
                            licenseManager?.incrementDownloadCount()
                            remainingDownloads = licenseManager?.getRemainingDownloads()?.coerceAtMost(20) 
                                ?: (remainingDownloads - 1)
                            
                            onDownloadTriggered()
                            Toast.makeText(context, "Download started! Check notifications.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, result.error ?: "Failed to fetch media", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = remainingDownloads > 0 && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Download Video (MP4)", fontSize = 16.sp)
            }
        }
    }
}
