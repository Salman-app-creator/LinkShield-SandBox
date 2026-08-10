package com.linkshield.sandbox.ui.grabber

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
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
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MediaGrabberScreen(
    licenseManager: LicenseManager,
    dnsManager:     DnsManager,
    onProRequired:  () -> Unit,
    sharedUrl:      String? = null
) {
    val context          = LocalContext.current
    val scope            = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val focusManager     = LocalFocusManager.current

    var urlInput      by remember { mutableStateOf("") }
    var isLoading     by remember { mutableStateOf(false) }
    var result        by remember { mutableStateOf<CobaltApiService.MediaResult?>(null) }
    var error         by remember { mutableStateOf<String?>(null) }
    var downloadCount by remember { mutableStateOf(licenseManager.getDownloadCount()) }
    val isPro          = licenseManager.isProUser()

    val cobaltApi          = remember { CobaltApiService(context, dnsManager) }
    var lastAutoFetchedUrl by remember { mutableStateOf<String?>(null) }

    // ── Auto-fill + auto-fetch when switching to Grabber tab ──────────────────
    // sharedUrl comes from the active WebView URL via onUrlChanged callback.
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank() && sharedUrl != urlInput) {
            urlInput = sharedUrl
            error    = null
            result   = null

            // Auto-trigger fetch only for recognizable media platforms / direct files
            if (isValidMediaUrl(sharedUrl) && sharedUrl != lastAutoFetchedUrl) {
                lastAutoFetchedUrl = sharedUrl
                delay(400L)     // small delay so UI settles before fetch starts
                performFetch(
                    targetUrl      = sharedUrl,
                    licenseManager = licenseManager,
                    isPro          = isPro,
                    cobaltApi      = cobaltApi,
                    context        = context,
                    dnsManager     = dnsManager,
                    scope          = scope,
                    focusManager   = focusManager,
                    onProRequired  = onProRequired,
                    setLoading     = { isLoading      = it },
                    setError       = { error          = it },
                    setResult      = { result         = it },
                    setDownloadCount = { downloadCount = it }
                )
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header card
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
                    imageVector        = Icons.Default.Download,
                    contentDescription = null,
                    modifier           = Modifier.size(48.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Media Grabber",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Paste a link from YouTube, TikTok, Instagram, Twitter/X, or any direct file URL.\n" +
                    "The active browser URL is auto-filled when you switch to this tab.",
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Download counter (free users) / PRO badge
        if (!isPro) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (downloadCount >= 20)
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Free Downloads Used", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$downloadCount / 20",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (downloadCount >= 20)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
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
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "PRO ACTIVE — Unlimited Downloads",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // URL input
        OutlinedTextField(
            value         = urlInput,
            onValueChange = {
                urlInput = it
                error    = null
                result   = null
            },
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("Paste URL here") },
            placeholder   = { Text("https://youtube.com/watch?v=...") },
            leadingIcon   = { Icon(Icons.Default.Link, contentDescription = null) },
            trailingIcon  = {
                IconButton(onClick = {
                    val clipText = clipboardManager.getText()?.text ?: ""
                    urlInput     = clipText
                    error        = null
                    result       = null
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                }
            },
            singleLine      = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction    = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = { focusManager.clearFocus() }
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Fetch button
        Button(
            onClick = {
                if (urlInput.isBlank()) {
                    error = "Please enter a URL"
                    return@Button
                }
                performFetch(
                    targetUrl      = urlInput,
                    licenseManager = licenseManager,
                    isPro          = isPro,
                    cobaltApi      = cobaltApi,
                    context        = context,
                    dnsManager     = dnsManager,
                    scope          = scope,
                    focusManager   = focusManager,
                    onProRequired  = onProRequired,
                    setLoading     = { isLoading      = it },
                    setError       = { error          = it },
                    setResult      = { result         = it },
                    setDownloadCount = { downloadCount = it }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape   = RoundedCornerShape(12.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(24.dp),
                    color       = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fetch & Download", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Error card
        AnimatedVisibility(visible = error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Warning,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.error
                    )
                    Text(
                        error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Result card
        AnimatedVisibility(visible = result != null && result?.success == true) {
            val res = result ?: return@AnimatedVisibility

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier            = Modifier.padding(20.dp),
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
                            "URL: ${if (url.length > 60) url.take(60) + "…" else url}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                val filename = res.filename
                                    ?: "download_${System.currentTimeMillis()}.mp4"
                                val mimeType = when {
                                    filename.endsWith(".mp3")               -> "audio/mpeg"
                                    filename.endsWith(".m4a")               -> "audio/mp4"
                                    filename.endsWith(".pdf")               -> "application/pdf"
                                    filename.endsWith(".zip")               -> "application/zip"
                                    filename.endsWith(".jpg") ||
                                    filename.endsWith(".jpeg")              -> "image/jpeg"
                                    filename.endsWith(".png")               -> "image/png"
                                    else                                    -> "video/mp4"
                                }
                                cobaltApi.startDownload(url, filename, mimeType)
                                Toast.makeText(
                                    context,
                                    "Download started: $filename",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp)
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

// ─────────────────────────────────────────────────────────────────────────────
// Dual-engine fetch logic
//
// Engine 1: Cobalt API  — for YouTube, TikTok, Instagram, Twitter/X etc.
// Engine 2: HTML5 fallback — for custom / unsupported sites.
//   When Cobalt returns a 4xx/5xx or the URL isn't from a known platform,
//   we attempt to download the URL directly via Android DownloadManager with
//   the page's domain as Referer. This covers HTML5 <video> sources that were
//   extracted by the JS bridge in UnblockShieldScreen.
// ─────────────────────────────────────────────────────────────────────────────
private fun performFetch(
    targetUrl:       String,
    licenseManager:  LicenseManager,
    isPro:           Boolean,
    cobaltApi:       CobaltApiService,
    context:         Context,
    dnsManager:      DnsManager,
    scope:           kotlinx.coroutines.CoroutineScope,
    focusManager:    androidx.compose.ui.focus.FocusManager,
    onProRequired:   () -> Unit,
    setLoading:      (Boolean) -> Unit,
    setError:        (String?) -> Unit,
    setResult:       (CobaltApiService.MediaResult?) -> Unit,
    setDownloadCount:(Int) -> Unit
) {
    if (!licenseManager.canDownload()) {
        onProRequired()
        return
    }

    focusManager.clearFocus()
    setLoading(true)
    setError(null)
    setResult(null)

    scope.launch {
        // ── Engine 1: Cobalt API ──────────────────────────────────────────
        val cobaltResult = cobaltApi.fetchMediaUrl(targetUrl.trim())

        if (cobaltResult.success && cobaltResult.url != null) {
            // Cobalt succeeded
            setResult(cobaltResult)
            if (!isPro) {
                licenseManager.incrementDownload()
                setDownloadCount(licenseManager.getDownloadCount())
            }
            setLoading(false)
            return@launch
        }

        // ── Engine 2: HTML5 / direct-URL fallback ─────────────────────────
        // Attempt only when the URL looks like a direct media file or the
        // JS extractor in UnblockShieldScreen surfaced a <video> src.
        val lowerUrl = targetUrl.lowercase()
        val isDirectMedia = listOf(
            ".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".mov",
            ".flv", ".avi", ".ogg", ".m3u8"
        ).any { lowerUrl.endsWith(it) } ||
            lowerUrl.contains("blob:") ||
            lowerUrl.contains("videoplayback") ||
            lowerUrl.contains("googlevideo.com")

        if (isDirectMedia) {
            // Build a synthetic filename
            val ext      = listOf(".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".mov")
                .firstOrNull { lowerUrl.endsWith(it) } ?: ".mp4"
            val filename = "linkshield_${System.currentTimeMillis()}$ext"
            val mimeType = when (ext) {
                ".mp3", ".m4a"  -> "audio/mpeg"
                ".webm"         -> "video/webm"
                else            -> "video/mp4"
            }

            // Download directly via DownloadManager with Referer header
            val referer = try {
                val uri = Uri.parse(targetUrl)
                "${uri.scheme}://${uri.host}"
            } catch (_: Exception) { targetUrl }

            val dmRequest = DownloadManager.Request(Uri.parse(targetUrl))
                .setTitle(filename)
                .setDescription("Downloading via LinkShield")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, "LinkShield/$filename"
                )
                .addRequestHeader("Referer", referer)
                .addRequestHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(dmRequest)

            setResult(
                CobaltApiService.MediaResult(
                    success          = true,
                    url              = targetUrl,
                    filename         = filename,
                    isDirectDownload = true
                )
            )
            if (!isPro) {
                licenseManager.incrementDownload()
                setDownloadCount(licenseManager.getDownloadCount())
            }
        } else {
            // Both engines failed
            setError(cobaltResult.error ?: "Could not extract media. Try copying the direct video URL from the browser.")
        }

        setLoading(false)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Returns true for URLs that should auto-trigger a fetch on Grabber tab switch.
// Only well-known platforms + direct media extensions qualify, to avoid
// flooding the API with every visited URL.
// ─────────────────────────────────────────────────────────────────────────────
private fun isValidMediaUrl(url: String): Boolean {
    val lower     = url.lowercase()
    val platforms = listOf(
        "youtube.com/watch", "youtu.be/",
        "tiktok.com/",       "instagram.com/",
        "twitter.com/",      "x.com/",
        "facebook.com/",     "fb.watch/",
        "reddit.com/",       "soundcloud.com/",
        "dailymotion.com/",  "vimeo.com/"
    )
    val directExts = listOf(
        ".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".mov", ".flv"
    )
    return platforms.any { lower.contains(it) } || directExts.any { lower.endsWith(it) }
}
