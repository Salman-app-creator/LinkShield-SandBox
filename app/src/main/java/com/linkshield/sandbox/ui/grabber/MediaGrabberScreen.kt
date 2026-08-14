package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.CapturedMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI

private const val GRABBER_CHROME_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

data class MediaOption(
    val url: String,
    val quality: String,
    val type: String,
    val extension: String
)

fun cleanMediaUrl(rawUrl: String): String {
    return try {
        val uri = URI(rawUrl.trim())
        if (uri.host != null && (uri.host.contains("youtube.com") || uri.host.contains("youtu.be"))) {
            if (uri.host.contains("youtu.be")) {
                val videoId = uri.path.substringAfter("/")
                "https://www.youtube.com/watch?v=$videoId"
            } else if (uri.query != null && uri.query.contains("v=")) {
                val params = uri.query.split("&")
                val vParam = params.firstOrNull { it.startsWith("v=") }
                if (vParam != null) {
                    "https://www.youtube.com/watch?$vParam"
                } else rawUrl.trim()
            } else rawUrl.trim()
        } else {
            rawUrl.trim().split("?")[0]
        }
    } catch (e: Exception) {
        rawUrl.trim()
    }
}

@Composable
fun MediaGrabberScreen(
    dnsManager: DnsManager,
    licenseManager: LicenseManager,
    activeUrl: String = "",
    capturedMedia: List<CapturedMediaItem> = emptyList(),
    onClearCaptured: () -> Unit = {},
    onUpgradeRequired: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf(activeUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mediaList by remember { mutableStateOf<List<MediaOption>>(emptyList()) }

    LaunchedEffect(activeUrl) {
        if (activeUrl.isNotBlank()) {
            urlInput = activeUrl
        }
    }

    fun checkLicenseAndDownload(downloadAction: () -> Unit) {
        if (licenseManager.isProUser()) {
            downloadAction()
            return
        }
        if (!licenseManager.isTrialActive()) {
            Toast.makeText(context, "Trial ended. Please upgrade to Pro.", Toast.LENGTH_LONG).show()
            onUpgradeRequired()
            return
        }
        if (!licenseManager.incrementDownloadCount()) {
            Toast.makeText(context, "20 free downloads used. Upgrade to Pro.", Toast.LENGTH_LONG).show()
            onUpgradeRequired()
            return
        }
        downloadAction()
    }

    fun processUrl() {
        focusManager.clearFocus()
        val rawTarget = urlInput.trim()
        if (rawTarget.isBlank()) {
            errorMessage = "URL enter karna zaroori hai."
            return
        }

        val sanitizedUrl = cleanMediaUrl(rawTarget)

        isLoading = true
        errorMessage = null
        mediaList = emptyList()

        scope.launch(Dispatchers.IO) {
            try {
                val client = dnsManager.getClient()
                val jsonPayload = JSONObject().apply {
                    put("url", sanitizedUrl)
                }

                val request = Request.Builder()
                    .url("https://api.cobalt.tools/api/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", GRABBER_CHROME_UA)
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseData = response.body?.string()

                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (response.isSuccessful && responseData != null) {
                        val json = JSONObject(responseData)
                        val status = json.optString("status")

                        if (status == "stream" || status == "redirect" || status == "picker") {
                            val downloadUrl = json.optString("url")
                            val list = mutableListOf<MediaOption>()

                            if (downloadUrl.isNotBlank()) {
                                list.add(MediaOption(downloadUrl, "1080p Full HD", "VIDEO", "mp4"))
                                list.add(MediaOption(downloadUrl, "720p HD", "VIDEO", "mp4"))
                                list.add(MediaOption(downloadUrl, "480p SD", "VIDEO", "mp4"))
                                list.add(MediaOption(downloadUrl, "360p Low", "VIDEO", "mp4"))
                                list.add(MediaOption(downloadUrl, "MP3 Audio Only", "AUDIO", "mp3"))
                            } else if (status == "picker") {
                                val pickerArray = json.optJSONArray("picker")
                                if (pickerArray != null) {
                                    for (i in 0 until pickerArray.length()) {
                                        val item = pickerArray.getJSONObject(i)
                                        val pUrl = item.optString("url")
                                        val pType = item.optString("type", "video")
                                        if (pUrl.isNotBlank()) {
                                            list.add(
                                                MediaOption(
                                                    url = pUrl,
                                                    quality = "Option #${i + 1}",
                                                    type = pType.uppercase(),
                                                    extension = if (pType == "photo") "jpg" else "mp4"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            mediaList = list
                        } else {
                            errorMessage = json.optString("text", "Media extract karne mein masla aaya.")
                        }
                    } else {
                        errorMessage = "Server response error: ${response.code}. Link verify karein."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMessage = "Network Error: ${e.localizedMessage ?: "Unknown error"}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Green Hole Grabber",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Social media video ya audio link paste karein aur direct download karein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste Video / Media Link") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    trailingIcon = {
                        if (urlInput.isNotEmpty()) {
                            IconButton(onClick = { urlInput = "" }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { processUrl() })
                )

                Button(
                    onClick = { processUrl() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extracting Media...")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Get Media Links")
                    }
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (mediaList.isNotEmpty()) {
            Text(
                text = "Select Download Quality:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(mediaList) { item ->
                    MediaItemCard(
                        media = item,
                        onDownloadClick = {
                            checkLicenseAndDownload {
                                enqueueDirectDownload(context, item.url, item.extension, item.quality)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaItemCard(
    media: MediaOption,
    onDownloadClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (media.extension == "mp3") Icons.Default.MusicNote else Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.quality,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Format: ${media.type} (.${media.extension})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onDownloadClick,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Download", fontSize = 12.sp)
            }
        }
    }
}

private fun enqueueDirectDownload(context: Context, url: String, extension: String, qualityTag: String) {
    try {
        val fileName = "LinkShield_${qualityTag.replace(" ", "_")}_${System.currentTimeMillis()}.$extension"
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
            setTitle("LinkShield Media Download")
            setDescription("Downloading $qualityTag media file...")
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            addRequestHeader("User-Agent", GRABBER_CHROME_UA)
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "Download Started: $qualityTag", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
