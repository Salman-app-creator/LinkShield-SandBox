package com.linkshield.sandbox.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// ── Constants ──

private const val CHROME_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

private val TRACKING_PARAMS = setOf(
    "igsh", "si", "utm_source", "utm_medium", "utm_campaign", "utm_content",
    "utm_term", "utm_id", "fbclid", "gclid", "ttclid", "ref", "spm",
    "tracking_id", "aff_id", "feature", "app", "src", "pp"
)

private val ESSENTIAL_PARAMS = setOf("v", "list", "t", "start", "end", "index", "ab_channel")

enum class VideoQuality(val label: String, val apiValue: String) {
    BEST("Best / 4K", "max"),
    Q1080("1080p HD", "1080"),
    Q720("720p", "720"),
    Q480("480p", "480"),
    Q360("360p", "360")
}

private val COBALT_SUPPORTED = listOf(
    "youtube.com", "youtu.be", "tiktok.com", "instagram.com",
    "twitter.com", "x.com", "facebook.com", "fb.watch",
    "reddit.com", "soundcloud.com", "dailymotion.com", "vimeo.com",
    "tumblr.com", "bilibili.com", "streamable.com", "twitch.tv"
)

private val DIRECT_MEDIA_EXTS = listOf(
    ".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".mov", ".flv", ".m3u8", ".ogg", ".ts"
)

data class CapturedMediaItem(
    val url: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Helpers ──

private fun cleanUrl(raw: String): String {
    return try {
        val uri = Uri.parse(raw.trim())
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { name ->
            val lower = name.lowercase()
            if (lower in ESSENTIAL_PARAMS || lower !in TRACKING_PARAMS) {
                uri.getQueryParameter(name)?.let { builder.appendQueryParameter(name, it) }
            }
        }
        builder.build().toString()
    } catch (_: Exception) {
        raw.trim()
    }
}

/** Convert youtu.be, Shorts, and Music URLs to canonical watch?v= form. */
private fun normalizeYouTubeUrl(url: String): String {
    val uri = Uri.parse(url)
    val host = uri.host?.lowercase() ?: return url

    return when {
        host == "youtu.be" -> {
            val videoId = uri.lastPathSegment ?: return url
            Uri.parse("https://www.youtube.com/watch?v=$videoId").buildUpon().apply {
                uri.getQueryParameter("t")?.let { appendQueryParameter("t", it) }
            }.build().toString()
        }
        host.contains("youtube.com") && uri.pathSegments.contains("shorts") -> {
            val idx = uri.pathSegments.indexOf("shorts")
            val videoId = uri.pathSegments.getOrNull(idx + 1) ?: return url
            "https://www.youtube.com/watch?v=$videoId"
        }
        host.contains("music.youtube.com") -> {
            val videoId = uri.getQueryParameter("v") ?: uri.lastPathSegment ?: return url
            "https://www.youtube.com/watch?v=$videoId"
        }
        else -> url
    }
}

private fun isCobaltSupported(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
    return COBALT_SUPPORTED.any { supported ->
        host == supported || host.endsWith(".$supported")
    }
}

private fun isDirectMedia(url: String): Boolean {
    val lower = url.lowercase()
    return DIRECT_MEDIA_EXTS.any { lower.endsWith(it) }
}

private fun buildFilename(url: String, ext: String, audioOnly: Boolean): String {
    val ts = System.currentTimeMillis()
    val host = try {
        Uri.parse(url).host?.replace("www.", "")?.replace(".", "_") ?: "media"
    } catch (_: Exception) { "media" }
    val tag = if (audioOnly) "audio" else "video"
    return "linkshield_${host}_${tag}_$ts$ext"
}

private fun enqueueDirectDownload(context: Context, url: String, filename: String): Long {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val req = DownloadManager.Request(Uri.parse(url))
        .setTitle(filename)
        .setDescription("Downloading via LinkShield")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LinkShield/$filename")
        .addRequestHeader("User-Agent", CHROME_UA)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    return dm.enqueue(req)
}

private suspend fun cobaltFetch(
    client: okhttp3.OkHttpClient,
    url: String,
    quality: String,
    audioOnly: Boolean
): CobaltApiService.MediaResult = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().apply {
            put("url", url)
            put("vQuality", quality)
            put("isAudioOnly", audioOnly)
            put("aFormat", if (audioOnly) "mp3" else "mp3")
            put("isNoTTWatermark", true)
            put("isTTFullAudio", false)
            put("filenameStyle", "classic")
            put("downloadMode", "auto")
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val req = okhttp3.Request.Builder()
            .url("${CobaltApiService.API_BASE}/api/json")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val resp = client.newCall(req).execute()
        val respBody = resp.body?.string()

        if (!resp.isSuccessful || respBody == null) {
            return@withContext CobaltApiService.MediaResult(false, error = "HTTP ${resp.code}")
        }

        val json = JSONObject(respBody)
        val status = json.optString("status")

        when (status) {
            "stream", "redirect", "tunnel" -> {
                CobaltApiService.MediaResult(
                    success = true,
                    url = json.optString("url"),
                    filename = json.optString("filename", "download"),
                    isDirectDownload = status == "tunnel"
                )
            }
            "error" -> CobaltApiService.MediaResult(false, error = json.optString("text", "Unknown error"))
            else -> {
                val resultUrl = json.optString("url")
                if (resultUrl.isNotEmpty())
                    CobaltApiService.MediaResult(true, url = resultUrl, filename = "download")
                else
                    CobaltApiService.MediaResult(false, error = "Unexpected response: $status")
            }
        }
    } catch (e: Exception) {
        CobaltApiService.MediaResult(false, error = e.message ?: "Unknown error")
    }
}
@Composable
fun MediaGrabberScreen(
    dnsManager: DnsManager,
    licenseManager: LicenseManager,
    capturedMedia: List<CapturedMediaItem>,
    onClearCaptured: () -> Unit,
    onProRequired: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var inputUrl by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf(VideoQuality.Q720) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var useCobaltEngine by remember { mutableStateOf(true) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val remainingDownloads by remember { derivedStateOf { licenseManager.getRemainingDownloads() } }
    val isPro by remember { derivedStateOf { licenseManager.isPro() } }

    val scrollState = rememberScrollState()

    fun triggerDownload(targetUrl: String) {
        focusManager.clearFocus()
        if (!licenseManager.canDownload()) {
            onProRequired()
            Toast.makeText(context, "Free downloads limit reached. Upgrade to Pro!", Toast.LENGTH_LONG).show()
            return
        }

        val cleaned = cleanUrl(normalizeYouTubeUrl(targetUrl))
        if (cleaned.isBlank()) {
            errorMessage = "Please enter a valid URL."
            return
        }

        isLoading = true
        statusMessage = "Processing request..."
        errorMessage = null

        scope.launch {
            try {
                if (useCobaltEngine && isCobaltSupported(cleaned)) {
                    val res = cobaltFetch(dnsManager.okHttpClient, cleaned, selectedQuality.apiValue, isAudioOnly)
                    if (res.success && !res.url.isNullOrBlank()) {
                        val finalFilename = if (res.filename.isNotBlank()) res.filename else buildFilename(cleaned, if (isAudioOnly) ".mp3" else ".mp4", isAudioOnly)
                        enqueueDirectDownload(context, res.url, finalFilename)
                        licenseManager.consumeDownload()
                        statusMessage = "Download started: $finalFilename"
                        Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Fallback logic
                        if (isDirectMedia(cleaned)) {
                            val filename = buildFilename(cleaned, if (isAudioOnly) ".mp3" else ".mp4", isAudioOnly)
                            enqueueDirectDownload(context, cleaned, filename)
                            licenseManager.consumeDownload()
                            statusMessage = "Direct download started."
                        } else {
                            errorMessage = res.error ?: "Could not extract media. Try pasting a direct video file URL (.mp4, .m3u8, .mp3 etc.)"
                        }
                    }
                } else if (isDirectMedia(cleaned)) {
                    val filename = buildFilename(cleaned, if (isAudioOnly) ".mp3" else ".mp4", isAudioOnly)
                    enqueueDirectDownload(context, cleaned, filename)
                    licenseManager.consumeDownload()
                    statusMessage = "Direct download started."
                } else {
                    errorMessage = "Unsupported link or engine disabled. Paste direct file links (.mp4, .mp3, .m3u8)."
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Banner / Remaining Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                Column {
                    Text(
                        text = "Remaining free downloads",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isPro) "Unlimited (Pro)" else "$remainingDownloads / 20",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!isPro) {
                    TextButton(onClick = onProRequired) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upgrade")
                    }
                }
            }
        }

        // URL Input Section
        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste or captured URL") },
            placeholder = { Text("https://...") },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Link, contentDescription = null)
            },
            trailingIcon = {
                Row {
                    if (inputUrl.isNotEmpty()) {
                        IconButton(onClick = { inputUrl = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                    IconButton(onClick = {
                        clipboardManager.getText()?.text?.let { inputUrl = it }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { triggerDownload(inputUrl) }
            )
        )

        // Engine Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Engine", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Cobalt API (recommended)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                Switch(
                    checked = useCobaltEngine,
                    onCheckedChange = { useCobaltEngine = it }
                )
            }
        }

        // Quality & Audio Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { qualityMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Video Quality", style = MaterialTheme.typography.labelSmall)
                            Text(selectedQuality.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                DropdownMenu(
                    expanded = qualityMenuExpanded,
                    onDismissRequest = { qualityMenuExpanded = false }
                ) {
                    VideoQuality.values().forEach { q ->
                        DropdownMenuItem(
                            text = { Text(q.label) },
                            onClick = {
                                selectedQuality = q
                                qualityMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { isAudioOnly = !isAudioOnly },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isAudioOnly) Icons.Default.MusicNote else Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Format", style = MaterialTheme.typography.labelSmall)
                        Text(if (isAudioOnly) "Audio Only (.mp3)" else "Video + Audio", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Fetch & Download Button
        Button(
            onClick = { triggerDownload(inputUrl) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading && inputUrl.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
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
                Text("Fetch & Download")
            }
        }

        // Status & Error Banners
        if (statusMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(statusMessage!!, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(errorMessage!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Captured Media List (If available)
        if (capturedMedia.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Captured Media (${capturedMedia.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = onClearCaptured) {
                    Text("Clear List")
                }
            }

            capturedMedia.forEach { item ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            inputUrl = item.url
                            triggerDownload(item.url)
                        },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (item.type.contains("audio")) Icons.Default.AudioFile else Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.url,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                item.type,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(onClick = {
                            inputUrl = item.url
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Use URL")
                        }
                    }
                }
            }
        }
    }
}
