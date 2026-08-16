package com.linkshield.sandbox.ui.grabber

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaGrabberScreen(
    activeUrl: String = "",
    capturedMedia: List<CapturedMediaItem> = emptyList(),
    dnsManager: DnsManager? = null,
    licenseManager: LicenseManager? = null,
    onBack: () -> Unit = {},
    onClearCaptured: () -> Unit = {},
    onUpgradeRequired: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    val latest =
        capturedMedia.lastOrNull()

    var inputUrl by remember {
        mutableStateOf(
            latest?.url ?: activeUrl
        )
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var audioOnly by remember {
        mutableStateOf(false)
    }

    var highQuality by remember {
        mutableStateOf(true)
    }

    var remaining by remember(
        licenseManager
    ) {
        mutableIntStateOf(
            licenseManager
                ?.getRemainingDownloads()
                ?.coerceAtMost(20)
                ?: 20
        )
    }

    LaunchedEffect(
        latest?.url,
        activeUrl
    ) {
        val detected =
            latest?.url
                ?.takeIf { it.isNotBlank() }
                ?: activeUrl

        if (detected.isNotBlank()) {
            inputUrl = detected
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription =
                        "Back to WebView"
                )
            }

            Text(
                "Media Grabber",
                style =
                    MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(
                    rememberScrollState()
                )
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme
                            .primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    Text(
                        "$remaining Free Downloads Remaining",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Upgrade to Pro for Unlimited",
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = inputUrl,
                onValueChange = {
                    inputUrl = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Paste or Fetch Link...")
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (inputUrl.isNotBlank()) {
                        IconButton(
                            onClick = {
                                inputUrl = ""
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription =
                                    "Clear URL"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(
                                RoundedCornerShape(10.dp)
                            )
                            .background(
                                MaterialTheme.colorScheme
                                    .surfaceVariant
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme
                                    .outline.copy(
                                        alpha = 0.3f
                                    ),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint =
                                MaterialTheme.colorScheme
                                    .primary.copy(
                                        alpha = 0.65f
                                    )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        latest?.title
                            ?.takeIf { it.isNotBlank() }
                            ?: "Media Preview",
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        latest?.pageUrl ?: activeUrl,
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Options",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = audioOnly,
                        onCheckedChange = {
                            audioOnly = it
                            if (it) {
                                highQuality = false
                            }
                        }
                    )
                    Text("Audio Only")
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = highQuality,
                        onCheckedChange = {
                            highQuality = it
                            if (it) {
                                audioOnly = false
                            }
                        }
                    )
                    Text("High Quality")
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val url = inputUrl.trim()

                    if (url.isBlank()) {
                        Toast.makeText(
                            context,
                            "Please enter a valid link",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    if (remaining <= 0) {
                        onUpgradeRequired()
                        return@Button
                    }

                    loading = true

                    scope.launch {
                        try {
                            val manager =
                                dnsManager
                                    ?: DnsManager(context)

                            val cobalt =
                                CobaltApiService(
                                    context,
                                    manager
                                )

                            val result =
                                withContext(Dispatchers.IO) {
                                    cobalt.fetchMediaUrl(url)
                                }

                            if (
                                result.success &&
                                !result.url.isNullOrBlank()
                            ) {
                                enqueueDownload(
                                    context = context,
                                    url = result.url,
                                    filename =
                                        result.filename
                                            ?.takeIf {
                                                it.isNotBlank()
                                            }
                                            ?: if (audioOnly) {
                                                "LinkShield_Audio.mp3"
                                            } else {
                                                "LinkShield_Video.mp4"
                                            }
                                )

                                licenseManager
                                    ?.incrementDownloadCount()

                                remaining =
                                    licenseManager
                                        ?.getRemainingDownloads()
                                        ?.coerceAtMost(20)
                                        ?: (remaining - 1)

                                Toast.makeText(
                                    context,
                                    "Download started",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    result.error
                                        ?: "Media extraction failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message
                                    ?: "Download failed",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled =
                    !loading && remaining > 0,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Download",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
                        Spacer(Modifier.height(12.dp))

            if (capturedMedia.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearCaptured,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Captured Media")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun enqueueDownload(
    context: Context,
    url: String,
    filename: String
) {
    val safeName =
        filename
            .substringAfterLast("/")
            .substringBefore("?")
            .ifBlank {
                "LinkShield_Download.mp4"
            }

    val request =
        DownloadManager.Request(
            Uri.parse(url)
        ).apply {
            setTitle(safeName)
            setDescription(
                "Downloading via LinkShield"
            )
            setNotificationVisibility(
                DownloadManager.Request
                    .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "LinkShield/$safeName"
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

    val manager =
        context.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

    manager.enqueue(request)
}
