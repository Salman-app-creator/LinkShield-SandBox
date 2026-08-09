package com.linkshield.sandbox.ui.grabber

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.license.LicenseManager
import kotlinx.coroutines.launch

@Composable
fun MediaGrabberScreen(
    licenseManager: LicenseManager,
    onProRequired: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CobaltApiService.MediaResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadCount by remember { mutableIntStateOf(licenseManager.getDownloadCount()) }
    val isPro = licenseManager.isProUser()

    val cobaltApi = remember { CobaltApiService(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Media Grabber",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Paste a link from YouTube, TikTok, Instagram, Twitter/X, or any direct file URL",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isPro) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (downloadCount >= 20) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Free Downloads Used",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "$downloadCount / 20",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (downloadCount >= 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "PRO ACTIVE - Unlimited Downloads",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                error = null
                result = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste URL here") },
            placeholder = { Text("https://youtube.com/watch?v=...") },
            leadingIcon = {
                Icon(Icons.Default.Link, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = {
                    val clipText = clipboardManager.getText()?.text ?: ""
                    urlInput = clipText
                    error = null
                    result = null
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = { focusManager.clearFocus() }
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                if (urlInput.isBlank()) {
                    error = "Please enter a URL"
                    return@Button
                }

                if (!licenseManager.canDownload()) {
                    onProRequired()
                    return@Button
                }

                focusManager.clearFocus()
                isLoading = true
                error = null
                result = null

                scope.launch {
                    val mediaResult = cobaltApi.fetchMediaUrl(urlInput.trim())
                    isLoading = false

                    if (mediaResult.success) {
                        result = mediaResult
                        if (!isPro) {
                            licenseManager.incrementDownload()
                            downloadCount = licenseManager.getDownloadCount()
                        }
                    } else {
                        error = mediaResult.error ?: "Failed to fetch media"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fetch & Download", style = MaterialTheme.typography.labelLarge)
            }
        }

        AnimatedVisibility(visible = error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        AnimatedVisibility(visible = result != null && result?.success == true) {
            val res = result ?: return@AnimatedVisibility

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Download Ready",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        "Filename: ${res.filename ?: "media_file"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    res.url?.let { url ->
                        Text(
                            "URL: ${url.take(60)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                val filename = res.filename ?: "download_${System.currentTimeMillis()}.mp4"
                                val mimeType = when {
                                    filename.endsWith(".mp3") -> "audio/mpeg"
                                    filename.endsWith(".m4a") -> "audio/mp4"
                                    filename.endsWith(".pdf") -> "application/pdf"
                                    filename.endsWith(".zip") -> "application/zip"
                                    filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> "image/jpeg"
                                    filename.endsWith(".png") -> "image/png"
                                    else -> "video/mp4"
                                }

                                val downloadId = cobaltApi.startDownload(url, filename, mimeType)
                                Toast.makeText(
                                    context,
                                    "Download started: $filename",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Download")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
