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

private const val GRABBER_CHROME_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
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
        .addRequestHeader("User-Agent", GRABBER_CHROME_UA)
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
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CobaltApiService.MediaResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadCount by remember { mutableIntStateOf(licenseManager.getDownloadCount()) }

    val isPro = licenseManager.isProUser()
    val remaining by remember {
        derivedStateOf {
            if (isPro) Int.MAX_VALUE else (20 - downloadCount).coerceAtLeast(0)
        }
    }
    val canDownload by remember { derivedStateOf { isPro || downloadCount < 20 } }

    var selectedQuality by remember { mutableStateOf(VideoQuality.Q720) }
    var audioOnly by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var useCobalt by remember { mutableStateOf(true) }

    LaunchedEffect(capturedMedia) {
        if (capturedMedia.isNotEmpty() && urlInput.isBlank()) {
            urlInput = cleanUrl(capturedMedia.last().url)
        }
    }

    fun executeDownload() {
        focusManager.clearFocus()
        if (!canDownload) {
            onProRequired()
            return
        }

        val targetUrl = normalizeYouTubeUrl(cleanUrl(urlInput))
        if (targetUrl.isBlank()) {
            error = "Please enter a valid media URL"
            return
        }

        isLoading = true
        error = null
        result = null

        scope.launch {
            if (isDirectMedia(targetUrl) || !useCobalt) {
                val ext = if (audioOnly) ".mp3" else ".mp4"
                val fname = buildFilename(targetUrl, ext, audioOnly)
                enqueueDirectDownload(context, targetUrl, fname)
                licenseManager.incrementDownloadCount()
                downloadCount = licenseManager.getDownloadCount()
                isLoading = false
                Toast.makeText(context, "Download started in background", Toast.LENGTH_LONG).show()
            } else {
                val client = dnsManager.getOkHttpClient()
                val res = cobaltFetch(client, targetUrl, selectedQuality.apiValue, audioOnly)
                isLoading = false
                if (res.success && !res.url.isNullOrEmpty()) {
                    result = res
                    enqueueDirectDownload(
                        context,
                        res.url,
                        res.filename ?: buildFilename(targetUrl, if (audioOnly) ".mp3" else ".mp4", audioOnly)
                    )
                    licenseManager.incrementDownloadCount()
                    downloadCount = licenseManager.getDownloadCount()
                    Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
                } else {
                    error = res.error ?: "Extraction failed. Try direct download mode below."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Green Hole HD Grabber",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "YouTube · TikTok · Instagram · Twitter/X · Direct files\nActive browser links auto-captured below.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isPro -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
                    remaining > 5 -> MaterialTheme.colorScheme.surfaceVariant
                    remaining > 0 -> Color(0xFFFF6F00).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isPro) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "PRO — Unlimited Downloads",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Column {
                        Text("Remaining free downloads", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$remaining / 20",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                remaining > 5 -> MaterialTheme.colorScheme.primary
                                remaining > 0 -> Color(0xFFFF6F00)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    TextButton(onClick = onProRequired) {
                        Icon(Icons.Default.Star, null, Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upgrade", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = capturedMedia.isNotEmpty(),
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Auto-Captured Streams (${capturedMedia.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        TextButton(onClick = onClearCaptured) {
                            Icon(Icons.Default.Clear, null, Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    capturedMedia.takeLast(5).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    urlInput = cleanUrl(item.url)
                                    result = null
                                    error = null
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (item.url.lowercase().contains(".mp3") || item.url.lowercase().contains(".m4a"))
                                    Icons.Default.AudioFile else Icons.Default.VideoFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title.ifBlank { "Media stream" },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    cleanUrl(item.url).let { if (it.length > 55) it.take(55) + "…" else it },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Use URL",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it; result = null; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste Video or Media Link") },
            placeholder = { Text("https://www.youtube.com/watch?v=...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Link, null) },
            trailingIcon = {
                Row {
                    if (urlInput.isNotEmpty()) {
                        IconButton(onClick = { urlInput = ""; result = null; error = null }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let {
                            urlInput = cleanUrl(it)
                            result = null
                            error = null
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, "Paste")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { executeDownload() })
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HighQuality, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Quality:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(6.dp))
                Box {
                    TextButton(onClick = { showQualityMenu = true }, enabled = !audioOnly) {
                        Text(if (audioOnly) "MP3 Audio" else selectedQuality.label, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                        VideoQuality.values().forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q.label) },
                                onClick = { selectedQuality = q; showQualityMenu = false }
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Audio Only", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = audioOnly,
                    onCheckedChange = { audioOnly = it }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cobalt API Engine", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = useCobalt,
                onCheckedChange = { useCobalt = it }
            )
        }

        Button(
            onClick = { executeDownload() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = urlInput.isNotBlank() && !isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Extracting Stream...")
            } else {
                Icon(Icons.Default.Download, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Media", style = MaterialTheme.typography.titleMedium)
            }
        }

        error?.let { errText ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Text(errText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
