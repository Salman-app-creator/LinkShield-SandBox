package com.linkshield.sandbox.ui.grabber

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.linkshield.sandbox.api.CobaltApiService
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import kotlinx.coroutines.launch

@Composable
fun LinkShieldGrabberScreen(
    onBackToBrowser: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val license = remember { LicenseManager(context.applicationContext) }
    val cobalt = remember {
        CobaltApiService(
            context.applicationContext,
            DnsManager(context.applicationContext)
        )
    }

    var inputUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<List<MediaQualityOption>>(emptyList()) }
    var selected by remember { mutableStateOf<MediaQualityOption?>(null) }
    var thumbnail by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }

    fun fetchMedia() {
        val url = inputUrl.trim()
        if (url.isBlank() || loading) return

        loading = true
        error = null
        options = emptyList()
        selected = null
        thumbnail = youtubeThumbnail(url)
        title = ""

        scope.launch {
            val result = runCatching {
                cobalt.fetchMediaOptions(url)
            }

            loading = false
            result.onSuccess { fetched ->
                options = fetched
                selected = fetched.firstOrNull()
                title = fetched.firstOrNull()?.title.orEmpty()
                if (fetched.isEmpty()) {
                    error = "No downloadable media was returned by Cobalt."
                }
            }.onFailure {
                error = it.message ?: "Media extraction failed."
            }
        }
    }

    LaunchedEffect(Unit) {
        MediaSnifferState.latestMedia.collect { media ->
            if (media != null && media.url.isNotBlank() && inputUrl.isBlank()) {
                inputUrl = media.url
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackToBrowser,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back to current website"
                )
            }
            Text(
                text = "Grabber",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (license.isProUser()) {
                                    "PRO — Unlimited Downloads"
                                } else {
                                    "${license.getRemainingDownloads()} Free Downloads Remaining"
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (license.isProUser()) {
                                    "Unlimited media downloads"
                                } else {
                                    "Upgrade to Pro for Unlimited"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (!license.isProUser()) {
                            TextButton(onClick = onUpgradeClick) {
                                Icon(Icons.Default.Upgrade, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Upgrade")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Paste or Fetch Link...") },
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as android.content.ClipboardManager
                            inputUrl = clipboard.primaryClip
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                .orEmpty()
                        }) {
                            Icon(Icons.Default.ContentPaste, "Paste")
                        }
                    }
                )
            }

            item {
                Button(
                    onClick = ::fetchMedia,
                    enabled = inputUrl.isNotBlank() && !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching media…")
                    } else {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Fetch Media")
                    }
                }
            }

            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        thumbnail?.let { image ->
                            AsyncImage(
                                model = image,
                                contentDescription = "Media preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alpha = 0.45f
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PlayCircle,
                                null,
                                Modifier.size(46.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                title.ifBlank { "Media Preview Area" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Text("Options:", fontWeight = FontWeight.Bold)
            }

            items(
                items = options,
                key = { it.url }
            ) { option ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected?.url == option.url) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected?.url == option.url,
                            onClick = { selected = option }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.displayLabel,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (option.mimeType.isNotBlank()) {
                                Text(
                                    option.mimeType,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        val option = selected ?: return@Button
                        if (!license.canDownload()) {
                            error = license.getRestrictionReason()
                            onUpgradeClick()
                            return@Button
                        }

                        scope.launch {
                            val saved = MediaFileDownloader.download(
                                context = context,
                                url = option.url,
                                filename = option.title,
                                mimeType = option.mimeType
                            )
                            if (saved) {
                                license.incrementDownloadCount()
                                error = null
                            } else {
                                error = "Download failed. Cobalt did not return a downloadable media file."
                            }
                        }
                    },
                    enabled = selected != null && !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download Selected")
                }
            }
        }
    }
}

private fun youtubeThumbnail(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    val id = when {
        host == "youtu.be" -> uri.pathSegments.firstOrNull()
        host.contains("youtube.com") -> {
            uri.getQueryParameter("v")
                ?: uri.pathSegments.dropWhile { it != "shorts" }.getOrNull(1)
        }
        else -> null
    }
    return id?.takeIf { it.isNotBlank() }
        ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
}

object MediaFileDownloader {
    suspend fun download(
        context: Context,
        url: String,
        filename: String,
        mimeType: String
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext false
        }

        // A playlist is not a playable file. Cobalt should normally return a
        // rendered/tunneled media URL; never save an m3u8/mpd document as .mp4.
        val cleanUrl = url.substringBefore("?").substringBefore("#").lowercase()
        if (cleanUrl.endsWith(".m3u8") || cleanUrl.endsWith(".mpd")) {
            return@withContext false
        }

        val client = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 LinkShieldSandbox/2.1")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val contentType = body.contentType()?.toString().orEmpty()
                if (contentType.contains("text/html", ignoreCase = true) ||
                    contentType.contains("application/xhtml", ignoreCase = true)
                ) {
                    return@withContext false
                }

                val safeName = sanitizeFilename(
                    filename,
                    mimeType.ifBlank { contentType },
                    url
                )

                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues().apply {
                        put(
                            android.provider.MediaStore.Downloads.DISPLAY_NAME,
                            safeName
                        )
                        put(
                            android.provider.MediaStore.Downloads.MIME_TYPE,
                            mimeType.ifBlank { contentType.ifBlank { "application/octet-stream" } }
                        )
                        put(
                            android.provider.MediaStore.Downloads.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_DOWNLOADS + "/LinkShield"
                        )
                        put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    ) ?: return@withContext false

                    try {
                        resolver.openOutputStream(uri)?.use { output ->
                            body.byteStream().use { input -> input.copyTo(output) }
                        } ?: return@withContext false

                        values.clear()
                        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        true
                    } catch (_: Exception) {
                        resolver.delete(uri, null, null)
                        false
                    }
                } else {
                    val dir = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        ),
                        "LinkShield"
                    )
                    if (!dir.exists()) dir.mkdirs()
                    val file = java.io.File(dir, safeName)
                    body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitizeFilename(
        filename: String,
        mimeType: String,
        url: String
    ): String {
        var name = filename
            .substringAfterLast('/')
            .substringBefore('?')
            .substringBefore('#')
            .ifBlank { "LinkShield_download" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()

        val lower = mimeType.lowercase()
        val ext = when {
            lower.contains("audio/mpeg") -> ".mp3"
            lower.contains("audio/mp4") -> ".m4a"
            lower.contains("video/mp4") -> ".mp4"
            lower.contains("webm") -> ".webm"
            lower.contains("ogg") -> ".ogg"
            else -> url
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('.')
                .takeIf { it.length in 2..5 }
                ?.let { ".$it" }
                ?: ".mp4"
        }

        if (!name.endsWith(ext, ignoreCase = true)) name += ext
        return name
    }
}
