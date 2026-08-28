package com.linkshield.sandbox.ui.grabber

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
import com.linkshield.sandbox.dns.DnsManager
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val latestCaptured by MediaSnifferState.latestMedia.collectAsState()

    var inputUrl by remember { mutableStateOf(initialUrl ?: "") }
    var sourceUrl by remember { mutableStateOf("") }
    var fetched by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var mediaUrl by remember { mutableStateOf("") }
    var mediaFilename by remember { mutableStateOf("") }
    var mediaMime by remember { mutableStateOf("video/mp4") }
    var thumbnailUrl by remember { mutableStateOf("") }
    var mediaTitle by remember { mutableStateOf("") }

    var audioOnly by rememberSaveable { mutableStateOf(false) }
    var selectedResolution by rememberSaveable { mutableStateOf("1080p") }

    val resolutions = listOf("360p", "480p", "720p", "1080p", "4K")
    val dnsManager = remember { DnsManager(context.applicationContext) }
    val repository = remember { MediaExtractorRepository() }

    val effectivelyPro = isProUser || dnsManager.isProUser()
    val remainingDownloads = if (effectivelyPro) Int.MAX_VALUE else dnsManager.getRemainingDownloads()

    val automaticSource = remember(initialUrl, latestCaptured?.pageUrl) {
        val capturedPage = latestCaptured?.pageUrl.orEmpty()
        when {
            isSupportedPageUrl(initialUrl.orEmpty()) -> initialUrl.orEmpty()
            isSupportedPageUrl(capturedPage) -> capturedPage
            else -> initialUrl.orEmpty()
        }
    }

    fun resetResult() {
        fetched = false
        mediaUrl = ""
        mediaFilename = ""
        mediaMime = "video/mp4"
        thumbnailUrl = ""
        mediaTitle = ""
        errorMsg = null
    }

    fun fetchSource(url: String) {
        val clean = url.trim()
        if (clean.isBlank()) {
            errorMsg = "Enter a URL first"
            return
        }
        if (!effectivelyPro && remainingDownloads <= 0) {
            errorMsg = "Download limit reached. Upgrade to Pro."
            return
        }

        inputUrl = clean
        sourceUrl = clean
        isLoading = true
        isDownloading = false
        errorMsg = null
        fetched = false
        keyboardController?.hide()

        scope.launch {
            try {
                val results = repository.extract(mediaUrl = clean, title = "")
                isLoading = false
                if (results.isNotEmpty()) {
                    val media = results.first()
                    sourceUrl = clean
                    mediaUrl = media.url
                    mediaTitle = media.title
                    mediaMime = media.mimeType
                    val ext = if (audioOnly || media.isAudio) "mp3" else "mp4"
                    mediaFilename = buildFilename(media.title, ext)
                    thumbnailUrl = extractYoutubeThumbnail(clean)
                    fetched = true
                } else {
                    resetResult()
                    sourceUrl = clean
                    errorMsg = "Failed to parse media"
                }
            } catch (e: Exception) {
                isLoading = false
                resetResult()
                sourceUrl = clean
                errorMsg = e.localizedMessage ?: "Failed to extract media"
            }
        }
    }

    fun downloadCurrent() {
        if (!fetched || mediaUrl.isBlank()) {
            errorMsg = "Fetch the media first"
            return
        }
        if (!effectivelyPro && remainingDownloads <= 0) {
            errorMsg = "Download limit reached. Upgrade to Pro."
            return
        }

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(mediaUrl))
                .setTitle(mediaTitle.ifBlank { "LinkShield Media" })
                .setDescription("Downloading file...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LinkShield/$mediaFilename")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            downloadManager.enqueue(request)
            if (!effectivelyPro) dnsManager.consumeDownload()

            Toast.makeText(context, "Download started! Check notifications ✓", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            errorMsg = "Download failed: ${e.localizedMessage}"
            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(automaticSource) {
        if (isSupportedPageUrl(automaticSource) && automaticSource != sourceUrl && !isLoading) {
            fetchSource(automaticSource)
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank() && initialUrl != "about:blank" && initialUrl != inputUrl) {
            inputUrl = initialUrl
            resetResult()
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
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToBrowser, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to browser")
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (effectivelyPro) {
                        Text("👑 PRO Unlimited", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    } else if (trialDaysLeft > 0) {
                        Text("[ $remainingDownloads Free Downloads Remaining ]", fontWeight = FontWeight.Bold)
                        Text("Trial: $trialDaysLeft days left • Upgrade for unlimited", fontSize = 12.sp)
                    } else {
                        Text("Trial Ended", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Upgrade to Pro for unlimited downloads", fontSize = 12.sp)
                    }
                }
                if (!effectivelyPro) {
                    TextButton(onClick = onUpgradeClick) {
                        Icon(Icons.Default.Upgrade, null, Modifier.size(17.dp))
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
            leadingIcon = { Icon(Icons.Default.PlayCircle, null) },
            trailingIcon = {
                if (inputUrl.isNotEmpty()) {
                    IconButton(onClick = {
                        inputUrl = ""
                        sourceUrl = ""
                        resetResult()
                    }) {
                        Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                if (!isLoading && !isDownloading) fetchSource(inputUrl)
            }),
            shape = RoundedCornerShape(12.dp),
            isError = errorMsg != null
        )
        errorMsg?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Card(
            Modifier.fillMaxWidth().height(180.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(thumbnailUrl).crossfade(true).build(),
                        contentDescription = "Media preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                when {
                                    isDownloading -> "Downloading ${downloadProgress.toInt()}%"
                                    isLoading -> "Parsing media..."
                                    fetched -> "✅ Ready to download"
                                    else -> "Media preview"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            if (mediaTitle.isNotBlank()) {
                                Text(
                                    mediaTitle,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PlayCircle,
                            null,
                            Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                isDownloading -> "Downloading ${downloadProgress.toInt()}%"
                                isLoading -> "Parsing media..."
                                else -> "Paste URL and tap Fetch"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Text("Options:", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = audioOnly,
                onCheckedChange = {
                    audioOnly = it
                    resetResult()
                },
                enabled = !isLoading && !isDownloading
            )
            Text("Audio Only (MP3)", fontSize = 13.sp)
        }

        if (!audioOnly) {
            Text("Select Resolution:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                resolutions.forEach { res ->
                    FilterChip(
                        selected = selectedResolution == res,
                        onClick = {
                            selectedResolution = res
                            resetResult()
                        },
                        enabled = !isLoading && !isDownloading,
                        label = { Text(res, fontSize = 12.sp) },
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
                if (isDownloading) return@Button
                if (fetched) downloadCurrent() else fetchSource(inputUrl)
            },
            enabled = inputUrl.isNotBlank() && !isLoading && !isDownloading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    isLoading -> "Parsing..."
                    isDownloading -> "Downloading ${downloadProgress.toInt()}%"
                    fetched -> "Download"
                    else -> "Fetch Media"
                },
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

private fun isSupportedPageUrl(url: String): Boolean {
    if (url.isBlank() || url.equals("about:blank", true)) return false
    val lower = url.lowercase(Locale.US)
    return lower.contains("youtube.com/watch?") ||
        lower.contains("youtube.com/shorts/") ||
        lower.contains("youtu.be/") ||
        lower.contains("instagram.com/reel/") ||
        lower.contains("instagram.com/p/") ||
        lower.contains("tiktok.com/") ||
        lower.contains("facebook.com/") ||
        lower.contains("fb.watch/") ||
        lower.contains("twitter.com/") ||
        lower.contains("x.com/")
}

private fun buildFilename(title: String, extension: String): String {
    val safe = title
        .ifBlank { "LinkShield_download" }
        .replace("[^a-zA-Z0-9_\\- ]".toRegex(), "_")
        .trim()
        .take(100)
        .ifBlank { "LinkShield_download" }
    return "$safe.$extension"
}

private fun extractYoutubeThumbnail(url: String): String {
    val clean = url.trim()
    return try {
        val uri = android.net.Uri.parse(clean)
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val videoId = when {
            host == "youtu.be" -> uri.path?.trim('/')?.substringBefore('/')
            host.endsWith("youtube.com") && uri.path.equals("/watch", true) -> uri.getQueryParameter("v")
            host.endsWith("youtube.com") && uri.path?.startsWith("/shorts/") == true ->
                uri.path?.substringAfter("/shorts/")?.substringBefore('/')
            else -> null
        }
        if (!videoId.isNullOrBlank()) {
            "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    }
}
