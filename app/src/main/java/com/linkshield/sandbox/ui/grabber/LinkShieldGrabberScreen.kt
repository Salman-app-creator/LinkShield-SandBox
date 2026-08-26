package com.linkshield.sandbox.ui.grabber

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/grabber/LinkShieldGrabberScreen.kt

import android.app.DownloadManager
import android.content.Context
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

    var inputUrl by rememberSaveable { mutableStateOf(initialUrl ?: "") }
    var audioOnly by rememberSaveable { mutableStateOf(false) }
    var selectedResolution by rememberSaveable { mutableStateOf("1080p") }
    var fetched by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }

    // External URL auto-fill update
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank() && initialUrl != "about:blank") {
            inputUrl = initialUrl
            fetched = false
            errorMsg = null
        }
    }

    // Download result state
    var mediaUrl by rememberSaveable { mutableStateOf("") }
    var mediaFilename by rememberSaveable { mutableStateOf("") }
    var mediaMime by rememberSaveable { mutableStateOf("video/mp4") }
    var thumbnailUrl by rememberSaveable { mutableStateOf("") }

    val resolutions = listOf("360p", "480p", "720p", "1080p", "4K")

    val dnsManager = remember { DnsManager(context) }
    val effectivelyPro = isProUser || dnsManager.isProUser()
    val remainingDownloads = if (effectivelyPro) Int.MAX_VALUE else dnsManager.getRemainingDownloads()

    val cobaltService = remember {
        CobaltApiService(context, DnsManager(context.applicationContext))
    }

    // Fetch function extracted so it can be triggered from keyboard IME action too
    fun doFetch() {
        if (inputUrl.isBlank()) {
            errorMsg = "Enter a URL first"
            return
        }
        if (!effectivelyPro && remainingDownloads <= 0) {
            errorMsg = "Download limit reached. Upgrade to Pro."
            return
        }
        isLoading = true
        errorMsg = null
        keyboardController?.hide()

        scope.launch {
            thumbnailUrl = extractYoutubeThumbnail(inputUrl)
            val result = cobaltService.fetchMediaUrl(
                pageUrl      = inputUrl,
                downloadMode = if (audioOnly) "audio" else "auto",
                videoQuality = selectedResolution.replace("p", "").replace("4K", "2160")
            )
            isLoading = false
            if (result.success && result.url != null) {
                mediaUrl      = result.url
                mediaFilename = result.filename ?: "LinkShield_download"
                mediaMime     = result.mimeType ?: "video/mp4"
                fetched       = true
                if (!effectivelyPro) {
                    dnsManager.consumeDownload()
                }
            } else {
                errorMsg = result.error ?: "Failed to fetch media"
            }
        }
    }

    // ── Root column — statusBarsPadding() handles notification bar ──
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()          // Notification bar se clear
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ── Title row ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
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

        // ── License badge ──
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    if (effectivelyPro) {
                        Text(
                            "👑 PRO Unlimited",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (trialDaysLeft > 0) {
                        Text(
                            "[ $remainingDownloads Free Downloads Remaining ]",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Trial: $trialDaysLeft days left • Upgrade for unlimited",
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            "Trial Ended",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
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

        // ── URL input — keyboard Done action triggers fetch ──
        OutlinedTextField(
            value = inputUrl,
            onValueChange = { newVal ->
                // Strip accidental leading/trailing whitespace on paste
                inputUrl = newVal.trim()
                fetched = false
                errorMsg = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Paste video link here...") },
            leadingIcon = { Icon(Icons.Default.PlayCircle, null) },
            trailingIcon = {
                if (inputUrl.isNotEmpty()) {
                    IconButton(onClick = {
                        inputUrl = ""
                        fetched = false
                        errorMsg = null
                        thumbnailUrl = ""
                        mediaUrl = ""
                    }) {
                        Icon(Icons.Default.Close, "Clear", Modifier.size(18.dp))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction    = ImeAction.Go         // Shows "Go" on keyboard
            ),
            keyboardActions = KeyboardActions(
                onGo = { if (!fetched && !isLoading) doFetch() }
            ),
            shape = RoundedCornerShape(12.dp),
            isError = errorMsg != null
        )
        errorMsg?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        // ── Preview / thumbnail area ──
        Card(
            Modifier.fillMaxWidth().height(180.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(thumbnailUrl).crossfade(true).build(),
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                if (fetched) "✅ Ready to download" else "🎬 Tap Fetch",
                                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                            if (fetched && mediaFilename.isNotBlank())
                                Text(mediaFilename, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayCircle, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                isLoading -> "Fetching media..."
                                fetched -> "Ready to download"
                                else -> "Paste URL and tap Fetch"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        if (fetched && mediaFilename.isNotBlank())
                            Text(mediaFilename, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Text("Options:", fontWeight = FontWeight.Bold)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = audioOnly, onCheckedChange = { audioOnly = it })
            Text("Audio Only (MP3)", fontSize = 13.sp)
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
                        selected = (selectedResolution == res),
                        onClick  = { selectedResolution = res },
                        label    = { Text(res, fontSize = 12.sp) },
                        shape    = RoundedCornerShape(8.dp)
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

        // ── Fetch / Download button ──
        Button(
            onClick = {
                if (!fetched) {
                    doFetch()
                } else {
                    // Trigger actual download
                    if (mediaUrl.isBlank()) {
                        errorMsg = "No media URL available"
                        return@Button
                    }
                    val request = DownloadManager.Request(android.net.Uri.parse(mediaUrl))
                        .setTitle(mediaFilename)
                        .setDescription("LinkShield Sandbox download")
                        .setMimeType(mediaMime)
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        .setDestinationInExternalPublicDir(
                            android.os.Environment.DIRECTORY_DOWNLOADS,
                            "LinkShield/$mediaFilename"
                        )
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    runCatching {
                        dm.enqueue(request)
                        Toast.makeText(context, "Download started ✓", Toast.LENGTH_SHORT).show()
                        fetched = false
                    }.onFailure {
                        Toast.makeText(
                            context,
                            "Download failed: ${it.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            enabled  = inputUrl.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    isLoading -> "Fetching..."
                    fetched   -> "Download"
                    else      -> "Fetch Media"
                },
                fontWeight = FontWeight.Bold
            )
        }

        // Bottom spacing so button is not cut off by nav bar
        Spacer(Modifier.navigationBarsPadding())
    }
}

private fun extractYoutubeThumbnail(url: String): String {
    val clean = url.trim().lowercase()
    return when {
        "youtube.com/watch" in clean || "youtu.be/" in clean -> {
            val videoId = when {
                "youtu.be/" in clean -> url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").take(11)
                "v=" in clean -> url.substringAfter("v=").substringBefore("&").substringBefore("#").take(11)
                else -> ""
            }
            if (videoId.isNotBlank()) "https://img.youtube.com/vi/$videoId/hqdefault.jpg" else ""
        }
        else -> ""
    }
}
