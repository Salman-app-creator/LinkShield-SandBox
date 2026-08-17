package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.CapturedMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

data class MediaOption(
    val sourceUrl: String,
    val downloadUrl: String,
    val quality: String,
    val type: String,
    val extension: String,
    val filename: String? = null,
    val referer: String? = null
)

@Composable
fun MediaGrabberScreen(
    dnsManager: DnsManager,
    licenseManager: LicenseManager,
    activeUrl: String = "",
    capturedMedia: List<CapturedMediaItem> = emptyList(),
    onClearCaptured: () -> Unit = {},
    onUpgradeRequired: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf(activeUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<List<MediaOption>>(emptyList()) }
    var showQualityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeUrl) {
        if (activeUrl.isNotBlank() && urlInput.isBlank()) {
            urlInput = activeUrl
        }
    }

    val api = remember(dnsManager) {
        CobaltApiService(context.applicationContext, dnsManager)
    }

    fun startDownload(option: MediaOption) {
        if (!licenseManager.canDownload()) {
            Toast.makeText(
                context,
                licenseManager.getRestrictionReason(),
                Toast.LENGTH_LONG
            ).show()
            onUpgradeRequired()
            return
        }

        // Count exactly one download only when the user actually taps Download.
        if (!licenseManager.incrementDownloadCount()) {
            Toast.makeText(
                context,
                licenseManager.getRestrictionReason(),
                Toast.LENGTH_LONG
            ).show()
            onUpgradeRequired()
            return
        }

        runCatching {
            api.startDownload(
                url = option.downloadUrl,
                filename = option.filename
                    ?: "LinkShield_${option.quality.replace(" ", "_")}_${System.currentTimeMillis()}.${option.extension}",
                mimeType = if (option.extension == "mp3") "audio/mpeg" else "video/mp4",
                referer = option.referer
            )
        }.onSuccess {
            Toast.makeText(
                context,
                "Download started: ${option.quality}",
                Toast.LENGTH_SHORT
            ).show()
        }.onFailure {
            Toast.makeText(
                context,
                "Download failed: ${it.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun extract(sourceUrl: String) {
        val target = sourceUrl.trim()
        if (target.isBlank()) {
            errorMessage = "URL enter karna zaroori hai."
            return
        }

        focusManager.clearFocus()
        isLoading = true
        errorMessage = null
        options = emptyList()

        scope.launch {
            val resultOptions = withContext(Dispatchers.IO) {
                val list = mutableListOf<MediaOption>()

                // If the WebView already captured a direct media URL, use it.
                val captured = capturedMedia
                    .firstOrNull { it.url == target }
                    ?: capturedMedia.firstOrNull { it.pageUrl == target }

                if (captured != null && isDownloadableDirect(captured.url)) {
                    list += MediaOption(
                        sourceUrl = captured.pageUrl,
                        downloadUrl = captured.url,
                        quality = "Captured media",
                        type = "VIDEO",
                        extension = extensionFor(captured.url),
                        referer = captured.pageUrl
                    )
                } else {
                    // Current page/direct social URL -> Cobalt.
                    val requested = listOf("1080", "720", "480").map { quality ->
                        api.extract(
                            CobaltApiService.MediaRequest(
                                sourceUrl = target,
                                quality = quality,
                                audioOnly = false
                            )
                        )
                    }

                    requested.forEachIndexed { index, result ->
                        if (result.success && !result.url.isNullOrBlank()) {
                            list += MediaOption(
                                sourceUrl = target,
                                downloadUrl = result.url,
                                quality = "${listOf("1080p", "720p", "480p")[index]} Video",
                                type = "VIDEO",
                                extension = extensionFor(result.url),
                                filename = result.filename,
                                referer = target
                            )
                        }
                    }

                    // Audio is a separate server-side conversion request.
                    val audio = api.extract(
                        CobaltApiService.MediaRequest(
                            sourceUrl = target,
                            quality = "1080",
                            audioOnly = true
                        )
                    )
                    if (audio.success && !audio.url.isNullOrBlank()) {
                        list += MediaOption(
                            sourceUrl = target,
                            downloadUrl = audio.url,
                            quality = "Audio Only",
                            type = "AUDIO",
                            extension = "mp3",
                            filename = audio.filename ?: "LinkShield_Audio.mp3",
                            referer = target
                        )
                    }

                    // If all quality requests were rejected but a direct link was supplied,
                    // expose it as one best-available option.
                    if (list.isEmpty() && isDownloadableDirect(target)) {
                        list += MediaOption(
                            sourceUrl = target,
                            downloadUrl = target,
                            quality = "Best available",
                            type = if (isAudio(target)) "AUDIO" else "VIDEO",
                            extension = extensionFor(target),
                            referer = target
                        )
                    }
                }

                list.distinctBy { it.downloadUrl to it.quality }
            }

            isLoading = false
            options = resultOptions
            showQualityDialog = resultOptions.isNotEmpty()

            if (resultOptions.isEmpty()) {
                errorMessage =
                    "Media link nahi mila. Source page login/protection require kar sakta hai, " +
                    "ya Cobalt instance ne is service ko support nahi kiya."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // "Green Hole Grabber" header intentionally removed.

                Text(
                    text = "Paste a social-media URL or use the current page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste Video / Media Link") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                    trailingIcon = {
                        if (urlInput.isNotEmpty()) {
                            IconButton(onClick = { urlInput = "" }) {
                                Icon(Icons.Default.Refresh, "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { extract(urlInput) }
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { extract(urlInput) },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Get Media")
                        }
                    }

                    if (capturedMedia.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                urlInput = capturedMedia.first().pageUrl
                                extract(urlInput)
                            }
                        ) {
                            Text("${capturedMedia.size} Captured")
                        }
                    }
                }

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (options.isNotEmpty()) {
            Text(
                "Available downloads",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(options) { option ->
                    MediaOptionCard(
                        option = option,
                        onDownload = { startDownload(option) }
                    )
                }
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Choose quality") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        OutlinedButton(
                            onClick = {
                                showQualityDialog = false
                                startDownload(option)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (option.type == "AUDIO")
                                    Icons.Default.MusicNote
                                else
                                    Icons.Default.Movie,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${option.quality}  •  .${option.extension}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MediaOptionCard(
    option: MediaOption,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (option.type == "AUDIO") Icons.Default.MusicNote else Icons.Default.Movie,
                contentDescription = null
            )

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    option.quality,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    "${option.type} • .${option.extension}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onDownload) {
                Text("Download", fontSize = 12.sp)
            }
        }
    }
}

private fun isDownloadableDirect(url: String): Boolean {
    val lower = url.lowercase()
    return listOf(
        ".mp4", ".m4v", ".webm", ".mkv", ".mov",
        ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav"
    ).any(lower::contains)
}

private fun isAudio(url: String): Boolean {
    val lower = url.lowercase()
    return listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav").any(lower::contains)
}

private fun extensionFor(url: String): String {
    val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
    return when {
        path.endsWith(".mp3") -> "mp3"
        path.endsWith(".m4a") -> "m4a"
        path.endsWith(".webm") -> "webm"
        path.endsWith(".mkv") -> "mkv"
        path.endsWith(".mov") -> "mov"
        path.endsWith(".m4v") -> "m4v"
        else -> "mp4"
    }
}
