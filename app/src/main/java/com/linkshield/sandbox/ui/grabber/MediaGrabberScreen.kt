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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private val TRACKING_PARAMS = setOf(
    "igsh", "si", "utm_source", "utm_medium", "utm_campaign", "utm_content",
    "utm_term", "utm_id", "fbclid", "gclid", "ttclid", "ref", "spm",
    "tracking_id", "aff_id", "feature", "app", "src"
)

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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun cleanUrl(raw: String): String {
    return try {
        val uri     = Uri.parse(raw.trim())
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { name ->
            if (name.lowercase() !in TRACKING_PARAMS) {
                uri.getQueryParameter(name)?.let { builder.appendQueryParameter(name, it) }
            }
        }
        builder.build().toString()
    } catch (_: Exception) {
        raw.trim()
    }
}

private fun isCobaltSupported(url: String): Boolean =
    COBALT_SUPPORTED.any { url.lowercase().contains(it) }

private fun isDirectMedia(url: String): Boolean {
    val lower = url.lowercase()
    return DIRECT_MEDIA_EXTS.any { lower.contains(it) }
}

private fun mimeFor(filename: String): String {
    val l = filename.lowercase()
    return when {
        l.endsWith(".mp3") || l.endsWith(".m4a") || l.endsWith(".ogg") -> "audio/mpeg"
        l.endsWith(".webm")                                            -> "video/webm"
        l.endsWith(".mkv")                                             -> "video/x-matroska"
        l.endsWith(".m3u8")                                            -> "application/x-mpegURL"
        l.endsWith(".ts")                                              -> "video/mp2t"
        else                                                           -> "video/mp4"
    }
}

private fun buildFilename(url: String, ext: String, audioOnly: Boolean): String {
    val ts   = System.currentTimeMillis()
    val host = try { Uri.parse(url).host?.replace("www.", "")?.replace(".", "_") ?: "media" }
               catch (_: Exception) { "media" }
    val tag  = if (audioOnly) "audio" else "video"
    return "linkshield_${host}_${tag}_$ts$ext"
}

private fun enqueueDirectDownload(context: Context, url: String, filename: String): Long {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val req = DownloadManager.Request(Uri.parse(url))
        .setTitle(filename)
        .setDescription("Downloading via LinkShield")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LinkShield/$filename")
        .addRequestHeader(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile"
        )
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    return dm.enqueue(req)
}

// ─────────────────────────────────────────────────────────────────────────────
// Cobalt API fetch — quality + audio-only support
// ─────────────────────────────────────────────────────────────────────────────
private suspend fun cobaltFetch(
    client:    okhttp3.OkHttpClient,
    url:       String,
    quality:   String,
    audioOnly: Boolean
): CobaltApiService.MediaResult = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().apply {
            put("url",               url)
            put("vQuality",          quality)
            put("isAudioOnly",       audioOnly)
            put("aFormat",           if (audioOnly) "mp3" else "mp3")
            put("isNoTTWatermark",   true)
            put("isTTFullAudio",     false)
            put("filenameStyle",     "classic")
            put("downloadMode",      "auto")
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val req = okhttp3.Request.Builder()
            .url("${CobaltApiService.API_BASE}/api/json")
            .addHeader("Accept",       "application/json")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val resp     = client.newCall(req).execute()
        val respBody = resp.body?.string()

        if (!resp.isSuccessful || respBody == null) {
            return@withContext CobaltApiService.MediaResult(false, error = "HTTP ${resp.code}")
        }

        val json   = JSONObject(respBody)
        val status = json.optString("status")

        when (status) {
            "stream", "redirect", "tunnel" -> {
                CobaltApiService.MediaResult(
                    success          = true,
                    url              = json.optString("url"),
                    filename         = json.optString("filename", "download"),
                    isDirectDownload = status == "tunnel"
                )
            }
            "error" -> CobaltApiService.MediaResult(false, error = json.optString("text", "Unknown error"))
            else    -> {
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

// ─────────────────────────────────────────────────────────────────────────────
// MediaGrabberScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MediaGrabberScreen(
    dnsManager:      DnsManager,
    licenseManager:  LicenseManager,
    capturedMedia:   List<CapturedMediaItem>,
    onClearCaptured: () -> Unit,
    onProRequired:   () -> Unit
) {
    val context          = LocalContext.current
    val scope            = rememberCoroutineScope()
    val clipboard        = LocalClipboardManager.current
    val focusManager     = LocalFocusManager.current

    var urlInput         by remember { mutableStateOf("") }
    var isLoading        by remember { mutableStateOf(false) }
    var result           by remember { mutableStateOf<CobaltApiService.MediaResult?>(null) }
    var error            by remember { mutableStateOf<String?>(null) }
    var downloadCount    by remember { mutableIntStateOf(licenseManager.getDownloadCount()) }

    val isPro            = licenseManager.isProUser()
    val remaining by remember {
        derivedStateOf {
            if (isPro) Int.MAX_VALUE else (20 - downloadCount).coerceAtLeast(0)
        }
    }
    val canDownload by remember { derivedStateOf { isPro || downloadCount < 20 } }

    var selectedQuality  by remember { mutableStateOf(VideoQuality.Q720) }
    var audioOnly        by remember { mutableStateOf(false) }
    var showQualityMenu  by remember { mutableStateOf(false) }
    var useCobalt        by remember { mutableStateOf(true) }

    // Auto-fill latest captured URL into input when switching to this tab
    LaunchedEffect(capturedMedia) {
        if (capturedMedia.isNotEmpty() && urlInput.isBlank()) {
            urlInput = cleanUrl(capturedMedia.last().url)
        }
    }

    // ── Outer scrollable layout ───────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier            = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector        = Icons.Default.Download,
                    contentDescription = null,
                    modifier           = Modifier.size(44.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Green Hole HD Grabber",
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "YouTube · TikTok · Instagram · Twitter/X · Direct files\n" +
                    "Active browser links auto-captured below.",
                    style     = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Quota badge ───────────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isPro          -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
                    remaining > 5  -> MaterialTheme.colorScheme.surfaceVariant
                    remaining > 0  -> Color(0xFFFF6F00).copy(alpha = 0.15f)
                    else           -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f)
                }
            )
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isPro) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "PRO — Unlimited Downloads",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Column {
                        Text("Remaining free downloads", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$remaining / 20",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = when {
                                remaining > 5 -> MaterialTheme.colorScheme.primary
                                remaining > 0 -> Color(0xFFFF6F00)
                                else          -> MaterialTheme.colorScheme.error
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

        // ── Auto-captured media list ───────────────────────────────────────────
        AnimatedVisibility(
            visible = capturedMedia.isNotEmpty(),
            enter   = fadeIn(tween(200)) + expandVertically(),
            exit    = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "Auto-Captured Streams (${capturedMedia.size})",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.secondary
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
                                    result   = null
                                    error    = null
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (item.url.lowercase().contains(".mp3") ||
                                                  item.url.lowercase().contains(".m4a"))
                                    Icons.Default.AudioFile else Icons.Default.VideoFile,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title.ifBlank { "Media stream" },
                                    style     = MaterialTheme.typography.labelMedium,
                                    maxLines  = 1,
                                    overflow  = TextOverflow.Ellipsis
                                )
                                Text(
                                    cleanUrl(item.url).let { if (it.length > 55) it.take(55) + "…" else it },
                                    style  = MaterialTheme.typography.bodySmall,
                                    color  = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Use URL",
                                tint     = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── URL input ─────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = urlInput,
            onValueChange = { urlInput = it; error = null; result = null },
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("Paste or captured URL") },
            placeholder   = { Text("https://youtube.com/watch?v=...") },
            leadingIcon   = { Icon(Icons.Default.Link, null) },
            trailingIcon  = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (urlInput.isNotBlank()) {
                        IconButton(onClick = { urlInput = ""; error = null; result = null }) {
                            Icon(Icons.Default.Clear, "Clear", Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = {
                        val clip = clipboard.getText()?.text?.trim() ?: ""
                        if (clip.isNotBlank()) { urlInput = clip; error = null; result = null }
                    }) {
                        Icon(Icons.Default.ContentPaste, "Paste", Modifier.size(18.dp))
                    }
                }
            },
            singleLine      = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus() }),
            shape           = RoundedCornerShape(14.dp)
        )

        // ── Engine selector ───────────────────────────────────────────────────
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { useCobalt = !useCobalt },
            shape    = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (useCobalt) Icons.Default.Cloud else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text("Engine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (useCobalt) "Cobalt API (recommended)" else "Direct / Native Fallback",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Switch(checked = useCobalt, onCheckedChange = { useCobalt = it })
            }
        }

        // ── Quality & audio picker — only for Cobalt engine ───────────────────
        AnimatedVisibility(
            visible = useCobalt,
            enter   = fadeIn(tween(200)) + expandVertically(),
            exit    = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Quality dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showQualityMenu = true },
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.HighQuality, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Video Quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(selectedQuality.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                            Text("▼", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                        VideoQuality.entries.forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q.label) },
                                leadingIcon = {
                                    if (selectedQuality == q) Icon(
                                        Icons.Default.CheckCircle, null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = { selectedQuality = q; showQualityMenu = false }
                            )
                        }
                    }
                }

                // Audio only toggle
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .clickable { audioOnly = !audioOnly }
                        .padding(vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Audio Only (.mp3)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = audioOnly, onCheckedChange = { audioOnly = it })
                }
            }
        }

        // ── Fetch button ──────────────────────────────────────────────────────
        Button(
            onClick = {
                when {
                    urlInput.isBlank() -> { error = "Please enter or paste a URL."; return@Button }
                    !canDownload       -> { onProRequired(); return@Button }
                    else -> {
                        focusManager.clearFocus()
                        val clean = cleanUrl(urlInput)
                        isLoading = true
                        error     = null
                        result    = null

                        scope.launch {
                            val res = fetchMedia(
                                url        = clean,
                                useCobalt  = useCobalt,
                                quality    = selectedQuality.apiValue,
                                audioOnly  = audioOnly,
                                client     = dnsManager.getClient()
                            )
                            isLoading = false
                            if (res.success) {
                                result = res
                            } else {
                                error = res.error ?: "Could not extract media. Try the Direct engine or paste a direct file URL."
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape   = RoundedCornerShape(14.dp),
            enabled = !isLoading,
            colors  = ButtonDefaults.buttonColors(
                containerColor = if (!canDownload)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primary,
                contentColor = if (!canDownload)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color       = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Fetching media…", style = MaterialTheme.typography.labelLarge)
            } else if (!canDownload) {
                Icon(Icons.Default.Star, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upgrade to Download", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (useCobalt) "Fetch & Download" else "Grab Direct Stream",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // ── Error card ────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = error != null,
            enter   = fadeIn(tween(200)) + expandVertically(),
            exit    = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier              = Modifier.padding(14.dp),
                    verticalAlignment     = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Warning, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // ── Result card ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = result?.success == true,
            enter   = fadeIn(tween(250)) + expandVertically(),
            exit    = fadeOut(tween(150)) + shrinkVertically()
        ) {
            result?.let { res ->
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                    )
                ) {
                    Column(
                        modifier            = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ready to Download", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }

                        if (!res.filename.isNullOrBlank()) {
                            Text("📁  ${res.filename}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (useCobalt && !audioOnly) {
                            Text("Quality: ${selectedQuality.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        res.url?.let { downloadUrl ->
                            HorizontalDivider()
                            Button(
                                onClick = {
                                    if (!licenseManager.canDownload()) { onProRequired(); return@Button }

                                    val ext = when {
                                        audioOnly -> ".mp3"
                                        downloadUrl.lowercase().endsWith(".webm") -> ".webm"
                                        else -> ".mp4"
                                    }
                                    val filename = res.filename
                                        ?: buildFilename(downloadUrl, ext, audioOnly)

                                    enqueueDirectDownload(context, downloadUrl, filename)

                                    if (!licenseManager.isProUser()) {
                                        licenseManager.incrementDownload()
                                        downloadCount = licenseManager.getDownloadCount()
                                    }
                                    Toast.makeText(context, "Download started: $filename", Toast.LENGTH_LONG).show()
                                    result = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Download")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dual-engine fetch
// Engine 1: Cobalt API — for supported platforms
// Engine 2: Direct DownloadManager — for direct files / fallback
// ─────────────────────────────────────────────────────────────────────────────
private suspend fun fetchMedia(
    url:       String,
    useCobalt: Boolean,
    quality:   String,
    audioOnly: Boolean,
    client:    okhttp3.OkHttpClient
): CobaltApiService.MediaResult {
    if (useCobalt && isCobaltSupported(url)) {
        val res = cobaltFetch(client, url, quality, audioOnly)
        if (res.success) return res
        // Cobalt failed → fall through to direct
    }

    // Direct / native engine
    return if (isDirectMedia(url)) {
        val ext  = DIRECT_MEDIA_EXTS.firstOrNull { url.lowercase().contains(it) } ?: ".mp4"
        val name = buildFilename(url, ext, audioOnly)
        CobaltApiService.MediaResult(success = true, url = url, filename = name, isDirectDownload = true)
    } else {
        CobaltApiService.MediaResult(
            success = false,
            error   = "Could not extract media. Try pasting a direct video file URL (.mp4, .m3u8, .mp3 etc.)"
        )
    }
}
