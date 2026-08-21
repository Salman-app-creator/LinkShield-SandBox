package com.linkshield.sandbox.ui.grabber

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/grabber/LinkShieldGrabberScreen.kt
// ← REPLACE existing file
//
// ROOT CAUSE FIX — Trial period showing even after license activation:
//
// OLD signature:
//   fun LinkShieldGrabberScreen(onBackToBrowser, onUpgradeClick)
//   → Screen was creating its own LicenseManager() instance LOCALLY
//   → This local instance never got refreshed after Pro activation
//   → Result: Always showed "trial" even with valid key
//
// NEW signature:
//   fun LinkShieldGrabberScreen(onBackToBrowser, onUpgradeClick, isProUser, trialDaysLeft)
//   → Live license state flows IN from UnblockShieldScreen (which refreshes on tab switch)
//   → Screen just reads the passed-in values — no stale local instance
//
// UI_FREEZE_CONTRACT respected:
//   • No new @Composable functions
//   • No layout / color / typography changes
//   • Only state wiring corrected

import android.app.DownloadManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkShieldGrabberScreen(
    onBackToBrowser: () -> Unit,
    onUpgradeClick: () -> Unit,
    // ── FIX: receive live license state from parent (UnblockShieldScreen) ──
    isProUser: Boolean = false,
    trialDaysLeft: Int = 7
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputUrl by rememberSaveable { mutableStateOf("") }
    var audioOnly by rememberSaveable { mutableStateOf(false) }
    var selectedResolution by rememberSaveable { mutableStateOf("1080p") }
    var fetched by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }

    // Download result state
    var mediaUrl by rememberSaveable { mutableStateOf("") }
    var mediaFilename by rememberSaveable { mutableStateOf("") }
    var mediaMime by rememberSaveable { mutableStateOf("video/mp4") }

    val resolutions = listOf("360p", "480p", "720p", "1080p", "4K")

    // ── FIX: use passed-in isProUser instead of creating stale local instance
    val dnsManager = remember { DnsManager(context) }
    // DnsManager pro flag is synced by LicenseManager.activatePro()
    // isProUser from parent is the canonical source; dnsManager is fallback
    val effectivelyPro = isProUser || dnsManager.isProUser()
    val remainingDownloads = if (effectivelyPro) Int.MAX_VALUE else dnsManager.getRemainingDownloads()

    // Cobalt API service (lazy-init, single instance)
    val cobaltService = remember {
        CobaltApiService(context, DnsManager(context.applicationContext))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title row
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToBrowser, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, "Back to browser")
            }
            Text(
                "Grabber",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // ── License badge — now reflects actual Pro status ──────────────────
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
                        // FIX: this now correctly shows PRO after key activation
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

        // URL input
        OutlinedTextField(
            value = inputUrl,
            onValueChange = {
                inputUrl = it
                fetched = false
                errorMsg = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Paste video link here...") },
            leadingIcon = { Icon(Icons.Default.PlayCircle, null) },
            trailingIcon = {
                if (inputUrl.isNotEmpty()) {
                    IconButton(onClick = { inputUrl = ""; fetched = false }) {
                        Icon(Icons.Default.ArrowBack, "Clear", Modifier.size(18.dp))
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            isError = errorMsg != null
        )
        errorMsg?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        // Preview area
        Card(
            Modifier.fillMaxWidth().height(180.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                            isLoading -> "Fetching media..."
                            fetched   -> "Ready to download"
                            else      -> "Media Preview Area"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    if (fetched && mediaFilename.isNotBlank()) {
                        Text(
                            mediaFilename,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

        // Fetch / Download button
        Button(
            onClick = {
                if (!fetched) {
                    // ── FETCH phase ─────────────────────────────────────────
                    if (inputUrl.isBlank()) {
                        errorMsg = "Enter a URL first"
                        return@Button
                    }
                    if (!effectivelyPro && remainingDownloads <= 0) {
                        errorMsg = "Download limit reached. Upgrade to Pro."
                        return@Button
                    }
                    isLoading = true
                    errorMsg  = null

                    scope.launch {
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
                } else {
                    // ── DOWNLOAD phase ──────────────────────────────────────
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
                        fetched = false   // reset for next download
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
    }
}
