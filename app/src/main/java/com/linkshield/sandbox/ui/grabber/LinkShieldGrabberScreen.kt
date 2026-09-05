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

    var inputUrl by remember { mutableStateOf(initialUrl ?: "") }
    var fetched by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
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
    val cobaltService = remember { CobaltApiService(context.applicationContext) }

    val effectivelyPro = isProUser || dnsManager.isProUser()
    val remainingDownloads = if (effectivelyPro) Int.MAX_VALUE else dnsManager.getRemainingDownloads()

    fun resetResult() {
        fetched = false; mediaUrl = ""; mediaFilename = ""
        mediaMime = "video/mp4"; mediaTitle = ""; errorMsg = null
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank() && initialUrl != "about:blank") {
            inputUrl = initialUrl
            resetResult()
            thumbnailUrl = extractYoutubeThumbnail(initialUrl)
        }
    }

    fun doFetch() {
        val clean = inputUrl.trim()
        if (clean.isBlank()) { errorMsg = "Enter a URL first"; return }
        if (!effectivelyPro && remainingDownloads <= 0) { errorMsg = "Download limit reached."; return }

        isLoading = true
        errorMsg = null
        keyboardController?.hide()

        scope.launch {
            try {
                thumbnailUrl = extractYoutubeThumbnail(clean)

                // FIX: YouTube → yt-dlp | Everything else → Cobalt
                val (success, url, filename, mime, err) = if (YtDlpEngine.isYouTubeUrl(clean)) {
                    val r = YtDlpEngine.resolve(context, clean, selectedResolution, audioOnly)
                    arrayOf(r.success, r.url, r.filename, r.mimeType, r.error)
                } else {
                    val r = cobaltService.fetchMediaUrl(clean, audioOnly, selectedResolution)
                    arrayOf(r.success, r.url, r.filename, r.mimeType, r.error)
                }

                if (success == true && url != null) {
                    mediaUrl = url as String
                    mediaFilename = (filename as? String)
                        ?: "LinkShield_download.${if (audioOnly) "mp3" else "mp4"}"
                    mediaTitle = mediaFilename.substringBeforeLast(".")
                    mediaMime = (mime as? String) ?: "video/mp4"
                    fetched = true
                    // FIX: quota NOT consumed on fetch — only on actual download
                } else {
                    resetResult()
                    thumbnailUrl = extractYoutubeThumbnail(clean)
                    errorMsg = (err as? String) ?: "Failed to fetch media"
                }
            } catch (e: Exception) {
                resetResult()
                errorMsg = "Fetch failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isLoading = false
            }
        }
    }

    fun downloadCurrent() {
        if (!fetched || mediaUrl.isBlank()) { errorMsg = "Fetch the media first"; return }
        try {
            val safeFilename = mediaFilename
                .replace(Regex("[/\\\\:*?\"<>|]"), "_")
                .trim()
                .ifBlank { "LinkShield_${System.currentTimeMillis()}.${if (audioOnly) "mp3" else "mp4"}" }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(
                DownloadManager.Request(Uri.parse(mediaUrl))
                    .setTitle(mediaTitle.ifBlank { "LinkShield Media" })
                    .setDescription("Downloading via LinkShield Sandbox")
                    .setMimeType(mediaMime)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LinkShield/$safeFilename")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
            )
            // FIX: quota consumed here on actual download, not on fetch
            if (!effectivelyPro) dnsManager.consumeDownload()
            Toast.makeText(context, "Download started ✓", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            errorMsg = "Download failed: ${e.localizedMessage}"
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToBrowser, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Spacer(Modifier.width(6.dp))
            Text("Grabber", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (effectivelyPro) Text("👑 PRO Unlimited", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    else if (trialDaysLeft > 0) {
                        Text("[ $remainingDownloads Free Downloads Remaining ]", fontWeight = FontWeight.Bold)
                        Text("Trial: $trialDaysLeft days left • Upgrade for unlimited", fontSize = 12.sp)
                    } else {
                        Text("Trial Ended", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Upgrade to Pro for unlimited downloads", fontSize = 12.sp)
                    }
                }
                if (!effectivelyPro) TextButton(onClick = onUpgradeClick) {
                    Icon(Icons.Default.Upgrade, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(3.dp)); Text("Upgrade")
                }
            }
        }

        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it; if (fetched || errorMsg != null) resetResult() },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Paste video link here...") },
            leadingIcon = { Icon(Icons.Default.PlayCircle, null) },
            trailingIcon = {
                if (inputUrl.isNotEmpty()) IconButton(onClick = { inputUrl = ""; resetResult(); thumbnailUrl = "" }) {
                    Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (!isLoading) { if (fetched) downloadCurrent() else doFetch() } }),
            shape = RoundedCornerShape(12.dp), isError = errorMsg != null
        )
        errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

        Card(Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(thumbnailUrl).crossfade(true).build(),
                        contentDescription = "Thumbnail", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.BottomStart) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                when {
                                    isLoading && YtDlpEngine.isYouTubeUrl(inputUrl.trim()) -> "Fetching via yt-dlp..."
                                    isLoading -> "Fetching from Cobalt..."
                                    fetched -> "✅ Ready"
                                    else -> "🎬 Tap Fetch"
                                },
                                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                            if (mediaTitle.isNotBlank())
                                Text(mediaTitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 2)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayCircle, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                isLoading && YtDlpEngine.isYouTubeUrl(inputUrl.trim()) -> "Fetching via yt-dlp..."
                                isLoading -> "Fetching from Cobalt..."
                                fetched -> "Ready to download"
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
            Checkbox(checked = audioOnly, onCheckedChange = { audioOnly = it; resetResult() }, enabled = !isLoading)
            Text("Audio Only (MP3)", fontSize = 13.sp)
        }

        if (!audioOnly) {
            Text("Select Resolution:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                resolutions.forEach { res ->
                    FilterChip(
                        selected = selectedResolution == res,
                        onClick = { selectedResolution = res; resetResult() },
                        enabled = !isLoading,
                        label = { Text(res, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        if (fetched) Text(
            "Quality: ${if (audioOnly) "MP3 Audio" else "$selectedResolution • MP4"}",
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = { if (fetched) downloadCurrent() else doFetch() },
            enabled = inputUrl.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp))
            Text(when { isLoading -> "Fetching..."; fetched -> "Download"; else -> "Fetch Media" }, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

private fun extractYoutubeThumbnail(url: String): String {
    return try {
        val uri = android.net.Uri.parse(url.trim())
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val videoId = when {
            host == "youtu.be" -> uri.lastPathSegment
            host.endsWith("youtube.com") -> uri.getQueryParameter("v")
                ?: uri.path?.substringAfter("/shorts/")?.substringBefore("/")
            else -> null
        }
        if (!videoId.isNullOrBlank()) "https://img.youtube.com/vi/$videoId/hqdefault.jpg" else ""
    } catch (_: Exception) { "" }
}
