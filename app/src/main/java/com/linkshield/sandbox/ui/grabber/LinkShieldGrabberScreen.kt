package com.linkshield.sandbox.ui.grabber

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/grabber/LinkShieldGrabberScreen.kt

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkShieldGrabberScreen(
    onBackToBrowser: () -> Unit,
    onUpgradeClick: () -> Unit,
    initialUrl: String? = null,
    isProUser: Boolean = false,
    trialDaysLeft: Int = 7
) {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val keyboardCtrl  = LocalSoftwareKeyboardController.current
    val dm            = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }

    var inputUrl         by remember { mutableStateOf(initialUrl ?: "") }
    var fetched          by remember { mutableStateOf(false) }
    var isLoading        by remember { mutableStateOf(false) }
    var errorMsg         by remember { mutableStateOf<String?>(null) }
    var mediaUrl         by remember { mutableStateOf("") }
    var mediaFilename    by remember { mutableStateOf("") }
    var mediaMime        by remember { mutableStateOf("video/mp4") }
    var thumbnailUrl     by remember { mutableStateOf("") }
    var mediaTitle       by remember { mutableStateOf("") }

    var audioOnly          by rememberSaveable { mutableStateOf(false) }
    var selectedResolution by rememberSaveable { mutableStateOf("1080p") }

    // ── Download Progress State ──
    var activeDownloadId   by remember { mutableStateOf<Long?>(null) }
    var downloadProgress   by remember { mutableStateOf(0) }
    var downloadedBytes    by remember { mutableStateOf(0L) }
    var totalBytes         by remember { mutableStateOf(0L) }
    var isDownloading      by remember { mutableStateOf(false) }
    var downloadSpeed      by remember { mutableStateOf(0L) }
    var lastBytesSnapshot  by remember { mutableStateOf(0L) }
    var lastTimeSnapshot   by remember { mutableStateOf(0L) }

    val resolutions   = listOf("360p", "480p", "720p", "1080p")
    val dnsManager    = remember { DnsManager(context.applicationContext) }
    val cobaltService = remember { CobaltApiService(context.applicationContext) }

    val effectivelyPro     = isProUser || dnsManager.isProUser()
    val remainingDownloads = if (effectivelyPro) Int.MAX_VALUE else dnsManager.getRemainingDownloads()

    fun resetResult() {
        fetched = false
        mediaUrl = ""
        mediaFilename = ""
        mediaMime = "video/mp4"
        mediaTitle = ""
        errorMsg = null
    }

    fun performFetch() {
        val clean = inputUrl.trim()

        isLoading = true
        errorMsg = null
        keyboardCtrl?.hide()

        scope.launch {
            try {
                thumbnailUrl = extractYoutubeThumbnail(clean)

                val result = cobaltService.fetchMediaUrl(
                    rawUrl = clean,
                    audioOnly = audioOnly,
                    resolution = selectedResolution
                )

                if (result.success && result.url != null) {
                    mediaUrl = result.url
                    mediaFilename = result.filename
                        ?: "LinkShield_download.${if (audioOnly) "mp3" else "mp4"}"
                    mediaTitle = mediaFilename.substringBeforeLast(".")
                    mediaMime = result.mimeType ?: "video/mp4"
                    fetched = true
                } else {
                    resetResult()
                    thumbnailUrl = extractYoutubeThumbnail(clean)
                    errorMsg = result.error ?: "Failed to fetch media"
                }
            } catch (e: Exception) {
                resetResult()
                errorMsg = "Fetch failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isLoading = false
            }
        }
    }

    fun doFetch() {
        val clean = inputUrl.trim()

        if (clean.isBlank()) {
            errorMsg = "Enter a URL first"
            return
        }

        if (!effectivelyPro && remainingDownloads <= 0) {
            errorMsg = "Download limit reached. Upgrade to continue."
            return
        }

        performFetch()
    }

    fun downloadCurrent() {
        if (!fetched || mediaUrl.isBlank()) {
            errorMsg = "Fetch the media first"
            return
        }

        try {
            val safeFilename = mediaFilename
                .replace(Regex("[/\\\\:*?\"<>|]"), "_")
                .trim()
                .ifBlank {
                    "LinkShield_${System.currentTimeMillis()}.${if (audioOnly) "mp3" else "mp4"}"
                }

            val downloadId = dm.enqueue(
                DownloadManager.Request(Uri.parse(mediaUrl))
                    .setTitle(mediaTitle.ifBlank { "LinkShield Media" })
                    .setDescription("Downloading via LinkShield Sandbox")
                    .setMimeType(mediaMime)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "LinkShield/$safeFilename"
                    )
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
            )

            activeDownloadId = downloadId
            isDownloading = true
            downloadProgress = 0
            downloadedBytes = 0L
            totalBytes = 0L
            downloadSpeed = 0L
            lastBytesSnapshot = 0L
            lastTimeSnapshot = System.currentTimeMillis()

            if (!effectivelyPro) {
                dnsManager.consumeDownload()
            }

            Toast.makeText(
                context,
                "Download started ✓",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            errorMsg = "Download failed: ${e.localizedMessage}"
            isDownloading = false
        }
    }

    // ── Download progress polling with speed calculation ──
    LaunchedEffect(activeDownloadId, isDownloading) {
        val id = activeDownloadId

        if (id == null || !isDownloading) return@LaunchedEffect

        lastBytesSnapshot = 0L
        lastTimeSnapshot = System.currentTimeMillis()

        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(id)
            val cursor = dm.query(query)

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    )
                    val bytesDownloaded = cursor.getLong(
                        cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val bytesTotal = cursor.getLong(
                        cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastTimeSnapshot
                    if (timeDiff > 0) {
                        val bytesDiff = bytesDownloaded - lastBytesSnapshot
                        downloadSpeed = if (bytesDiff > 0) (bytesDiff * 1000) / timeDiff else 0L
                    }
                    lastBytesSnapshot = bytesDownloaded
                    lastTimeSnapshot = now

                    downloadedBytes = bytesDownloaded
                    totalBytes = bytesTotal

                    if (bytesTotal > 0) {
                        downloadProgress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloadProgress = 100
                            downloadSpeed = 0L
                            isDownloading = false
                            Toast.makeText(
                                context,
                                "Download complete ✓",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        DownloadManager.STATUS_FAILED -> {
                            isDownloading = false
                            downloadSpeed = 0L
                            errorMsg = "Download failed. Dobara try karein."
                        }
                    }
                }
                cursor.close()
            }

            delay(1000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackToBrowser,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }

            Spacer(Modifier.width(6.dp))

            Text(
                "Grabber",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    when {
                        effectivelyPro ->
                            Text(
                                "👑 PRO Unlimited",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                        trialDaysLeft > 0 -> {
                            Text(
                                "[ $remainingDownloads Free Downloads Remaining ]",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Trial: $trialDaysLeft days left • Upgrade for unlimited",
                                fontSize = 12.sp
                            )
                        }

                        else -> {
                            Text(
                                "Trial Ended",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Upgrade to Pro for unlimited downloads",
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (!effectivelyPro) {
                    TextButton(onClick = onUpgradeClick) {
                        Icon(
                            Icons.Default.Upgrade,
                            null,
                            Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("Upgrade")
                    }
                }
            }
        }

        OutlinedTextField(
            value = inputUrl,
            onValueChange = {
                inputUrl = it
                if (fetched || errorMsg != null) resetResult()
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Paste video link here...") },
            leadingIcon = {
                Icon(Icons.Default.PlayCircle, null)
            },
            trailingIcon = {
                if (inputUrl.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            inputUrl = ""
                            resetResult()
                            thumbnailUrl = ""
                        }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "Clear",
                            Modifier.size(18.dp)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (!isLoading && !isDownloading) {
                        if (fetched) downloadCurrent() else doFetch()
                    }
                }
            ),
            shape = RoundedCornerShape(12.dp),
            isError = errorMsg != null
        )

        errorMsg?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }

        // ── Download Progress Card with Speed and Cancel ──
        if (isDownloading || downloadProgress > 0) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (isDownloading) "Downloading..." else "Download Complete ✓",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$downloadProgress%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isDownloading) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    activeDownloadId?.let { id -> dm.remove(id) }
                                    isDownloading = false
                                    downloadSpeed = 0L
                                    downloadProgress = 0
                                    downloadedBytes = 0L
                                    totalBytes = 0L
                                    activeDownloadId = null
                                    Toast.makeText(
                                        context,
                                        "Download cancelled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Cancel",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        if (isDownloading && downloadSpeed > 0) {
                            Text(
                                "⚡ ${formatSpeed(downloadSpeed)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isDownloading && downloadSpeed > 0 && totalBytes > downloadedBytes) {
                        val remainingBytes = totalBytes - downloadedBytes
                        val secondsLeft = remainingBytes / downloadSpeed
                        Text(
                            "⏱ ${formatTime(secondsLeft)} remaining",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Card(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                when {
                                    isLoading -> "Fetching..."
                                    fetched -> "✅ Ready"
                                    else -> "🎬 Tap Fetch"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )

                            if (mediaTitle.isNotBlank()) {
                                Text(
                                    mediaTitle,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            null,
                            Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            when {
                                isLoading -> "Fetching..."
                                fetched -> "Ready to download"
                                else -> "Paste URL and tap Fetch"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Text(
            "Options:",
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = audioOnly,
                onCheckedChange = {
                    audioOnly = it
                    resetResult()
                },
                enabled = !isLoading && !isDownloading
            )

            Text(
                "Audio Only (MP3)",
                fontSize = 13.sp
            )
        }

        if (!audioOnly) {
            Text(
                "Select Resolution:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                resolutions.forEach { res ->
                    FilterChip(
                        selected = selectedResolution == res,
                        onClick = {
                            selectedResolution = res
                            resetResult()
                        },
                        enabled = !isLoading && !isDownloading,
                        label = {
                            Text(
                                res,
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        if (fetched) {
            Text(
                "Quality: ${if (audioOnly) "MP3 Audio" else "$selectedResolution • MP4"}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                if (fetched) downloadCurrent() else doFetch()
            },
            enabled = inputUrl.isNotBlank() && !isLoading && !isDownloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Download,
                null
            )

            Spacer(Modifier.width(8.dp))

            Text(
                when {
                    isLoading -> "Fetching..."
                    isDownloading -> "Downloading..."
                    fetched -> "Download"
                    else -> "Fetch Media"
                },
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.2f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    val kb = bytesPerSecond / 1024.0
    val mb = kb / 1024.0

    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB/s", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.2f KB/s", kb)
        else -> "$bytesPerSecond B/s"
    }
}

private fun formatTime(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

private fun extractYoutubeThumbnail(url: String): String {
    return try {
        val uri = Uri.parse(url.trim())
        val host = uri.host?.lowercase(Locale.US).orEmpty()

        val vid = when {
            host == "youtu.be" -> uri.lastPathSegment

            host.endsWith("youtube.com") ->
                uri.getQueryParameter("v")
                    ?: uri.path
                        ?.substringAfter("/shorts/")
                        ?.substringBefore("/")

            else -> null
        }

        if (!vid.isNullOrBlank()) {
            "https://img.youtube.com/vi/$vid/hqdefault.jpg"
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    }
}
