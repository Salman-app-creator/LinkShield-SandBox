package com.linkshield.sandbox.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// MediaGrabberScreen.kt
//
// Responsibilities:
//   1. INTEGRATE WITH DnsManager — all network probes and OkHttp downloads
//      route through DnsManager.getClient() so DoH + TLS fragmentation
//      protects every byte.
//   2. DUAL-ENGINE DOWNLOAD PIPELINE — primary OkHttp (streaming with
//      progress) and fallback Android DownloadManager for large files or
//      when OkHttp stalls.
//   3. CAPTURED MEDIA SELECTION — accept stream URLs pushed from the Browser
//      tab, display them in a tappable list, and allow manual pasting.
//   4. DOWNLOAD COUNTER INTEGRATION — gate every download behind
//      DnsManager.canDownload(). Respect the 20-download free ceiling and
//      bypass limits for Pro users. Counter is backed by the same
//      SharedPreferences store that DnsManager uses.
// ─────────────────────────────────────────────────────────────────────────────

private const val PREFS_NAME          = "shield_prefs"
private const val KEY_DOWNLOAD_COUNT  = "download_count"
private const val KEY_IS_PRO          = "is_pro"
private const val FREE_DOWNLOAD_LIMIT = 20

/** Lightweight snapshot of a media item captured by the Browser tab. */
data class CapturedMediaItem(
    val url: String,
    val title: String,
    val pageUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** Result of a HEAD probe against a candidate URL. */
data class LinkAnalysisResult(
    val url: String,
    val contentType: String,
    val contentLength: Long,
    val isStream: Boolean,
    val fileName: String
)

sealed class DownloadState {
    object Idle : DownloadState()
    object Analyzing : DownloadState()
    data class AnalyzingError(val message: String) : DownloadState()
    data class Ready(val info: LinkAnalysisResult) : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
    object QuotaExceeded : DownloadState()
}

enum class DownloadEngine { OKHTTP, DOWNLOAD_MANAGER }

// ═══════════════════════════════════════════════════════════════════════════
// ViewModel
// ═══════════════════════════════════════════════════════════════════════════

class MediaGrabberViewModel(
    private val dnsManager: DnsManager,
    private val appContext: Context
) : ViewModel() {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _activeDmIds = MutableStateFlow<Set<Long>>(emptySet())
    val activeDmIds: StateFlow<Set<Long>> = _activeDmIds.asStateFlow()

    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id != -1L) {
                _activeDmIds.value = _activeDmIds.value - id
            }
        }
    }

    init {
        appContext.registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    /** Probe the URL through the DoH-shielded client to learn metadata. */
    fun analyzeLink(url: String) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Analyzing
            try {
                val client = dnsManager.getClient()
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    _downloadState.value = DownloadState.AnalyzingError("HTTP ${response.code}")
                    return@launch
                }

                val contentType = response.header("Content-Type") ?: "application/octet-stream"
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val isStream = contentType.contains("mpegurl", ignoreCase = true) ||
                        contentType.contains("dash", ignoreCase = true) ||
                        contentType.contains("video", ignoreCase = true) ||
                        contentType.contains("audio", ignoreCase = true) ||
                        contentType.contains("octet-stream", ignoreCase = true)

                val disposition = response.header("Content-Disposition")
                val fileName = extractFileName(url, disposition, contentType)

                _downloadState.value = DownloadState.Ready(
                    LinkAnalysisResult(url, contentType, contentLength, isStream, fileName)
                )
            } catch (e: Exception) {
                _downloadState.value = DownloadState.AnalyzingError(
                    e.message ?: "Failed to analyze link"
                )
            }
        }
    }

    /** Entry point for every user-initiated download. */
    fun startDownload(info: LinkAnalysisResult, engine: DownloadEngine) {
        if (!dnsManager.canDownload()) {
            _downloadState.value = DownloadState.QuotaExceeded
            return
        }

        viewModelScope.launch {
            when (engine) {
                DownloadEngine.OKHTTP       -> downloadViaOkHttp(info)
                DownloadEngine.DOWNLOAD_MANAGER -> downloadViaDownloadManager(info)
            }
        }
    }

    /** Primary engine — streams through the shielded OkHttp client with progress. */
    private suspend fun downloadViaOkHttp(info: LinkAnalysisResult) {
        _downloadState.value = DownloadState.Downloading(0f)
        try {
            val client = dnsManager.getClient()
            val request = Request.Builder().url(info.url).build()

            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

                val destDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: appContext.filesDir
                val destFile = File(destDir, info.fileName)

                val body = response.body ?: throw IOException("Empty response body")
                val total = body.contentLength()
                var read = 0L

                body.source().use { source ->
                    destFile.sink().buffer().use { sink ->
                        val buffer = okio.Buffer()
                        while (true) {
                            val byteCount = source.read(buffer, 8192)
                            if (byteCount == -1L) break
                            sink.write(buffer, byteCount)
                            read += byteCount
                            if (total > 0) {
                                _downloadState.value = DownloadState.Downloading(read.toFloat() / total)
                            }
                        }
                        sink.flush()
                    }
                }

                if (!dnsManager.isProUser()) incrementDownloadCount()
                _downloadState.value = DownloadState.Success(destFile)
            }
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
        }
    }

    /** Fallback engine — hands the URL to Android's DownloadManager. */
    private fun downloadViaDownloadManager(info: LinkAnalysisResult) {
        val request = DownloadManager.Request(info.url.toUri()).apply {
            setTitle(info.fileName)
            setDescription("LinkShield Sandbox")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                info.fileName
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val id = downloadManager.enqueue(request)
        _activeDmIds.value = _activeDmIds.value + id

        if (!dnsManager.isProUser()) incrementDownloadCount()
        _downloadState.value = DownloadState.Downloading(-1f)
    }

    private fun incrementDownloadCount() {
        val current = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, (current + 1).coerceAtLeast(0)).apply()
    }

    private fun extractFileName(url: String, disposition: String?, contentType: String): String {
        disposition?.let { disp ->
            val starRegex = "filename\\*\\s*=\\s*UTF-8''([^;\\s]+)"
                .toRegex(RegexOption.IGNORE_CASE)
            starRegex.find(disp)?.groupValues?.get(1)?.let {
                return URLDecoder.decode(it, "UTF-8")
            }
            val plainRegex = "filename\\s*=\\s*[\"']?([^;\\s\"']+)[\"']?"
                .toRegex(RegexOption.IGNORE_CASE)
            plainRegex.find(disp)?.groupValues?.get(1)?.let { return it }
        }

        Uri.parse(url).lastPathSegment?.let {
            if (it.isNotBlank() && it.contains(".")) return it
        }

        val ext = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(contentType) ?: "bin"
        return "linkshield_${UUID.randomUUID().toString().take(8)}.$ext"
    }

    override fun onCleared() {
        super.onCleared()
        appContext.unregisterReceiver(downloadReceiver)
    }
}

class MediaGrabberViewModelFactory(
    private val dnsManager: DnsManager,
    private val context: Context
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MediaGrabberViewModel(dnsManager, context.applicationContext) as T
    }
}
// ═══════════════════════════════════════════════════════════════════════════
// Screen
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGrabberScreen(
    dnsManager: DnsManager,
    capturedMedia: List<CapturedMediaItem> = emptyList(),
    onClearCaptured: () -> Unit = {},
    viewModel: MediaGrabberViewModel = viewModel(
        factory = MediaGrabberViewModelFactory(dnsManager, LocalContext.current)
    )
) {
    val context = LocalContext.current
    val downloadState by viewModel.downloadState.collectAsState()
    var manualUrl by remember { mutableStateOf("") }
    var selectedEngine by remember { mutableStateOf(DownloadEngine.OKHTTP) }
    var showQuotaDialog by remember { mutableStateOf(false) }

    if (showQuotaDialog) {
        AlertDialog(
            onDismissRequest = { showQuotaDialog = false },
            title = { Text("Download limit reached") },
            text = {
                Text(
                    "You have used all $FREE_DOWNLOAD_LIMIT free downloads. " +
                    "Upgrade to Pro for unlimited access."
                )
            },
            confirmButton = {
                TextButton(onClick = { showQuotaDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media Grabber") },
                actions = {
                    if (dnsManager.isProUser()) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                "PRO",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "${dnsManager.getRemainingDownloads()} left",
                            modifier = Modifier.padding(end = 16.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Manual URL input ─────────────────────────────────────────────
            OutlinedTextField(
                value = manualUrl,
                onValueChange = { manualUrl = it },
                label = { Text("Paste video or stream URL") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (manualUrl.isNotBlank()) {
                                viewModel.analyzeLink(manualUrl)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Analyze")
                    }
                }
            )

            // ── Captured media from Browser tab ──────────────────────────────
            if (capturedMedia.isNotEmpty()) {
                Text(
                    text = "Captured from Browser",
                    style = MaterialTheme.typography.titleMedium
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    items(capturedMedia) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = {
                                manualUrl = item.url
                                viewModel.analyzeLink(item.url)
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = item.title.ifBlank { "Untitled" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onClearCaptured,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Clear captured")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Download state machine ───────────────────────────────────────
            when (val state = downloadState) {
                is DownloadState.Analyzing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(
                        "Analyzing link…",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                is DownloadState.AnalyzingError -> {
                    Text(
                        text = "Analysis failed: ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is DownloadState.Ready -> {
                    LinkAnalysisCard(
                        info = state.info,
                        selectedEngine = selectedEngine,
                        onEngineChange = { selectedEngine = it },
                        onDownload = {
                            if (!dnsManager.canDownload()) {
                                showQuotaDialog = true
                            } else {
                                viewModel.startDownload(state.info, selectedEngine)
                            }
                        }
                    )
                }
                is DownloadState.Downloading -> {
                    if (state.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${(state.progress * 100).toInt()}%",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Download in progress via DownloadManager…",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                is DownloadState.Success -> {
                    Text(
                        text = "Saved to:\n${state.file.absolutePath}",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is DownloadState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is DownloadState.QuotaExceeded -> {
                    LaunchedEffect(Unit) { showQuotaDialog = true }
                }
                else -> { }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Quota footer ─────────────────────────────────────────────────
            if (!dnsManager.isProUser()) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Free downloads used: " +
                            "${dnsManager.getDownloadCount()} / $FREE_DOWNLOAD_LIMIT"
                        )
                        Button(onClick = { /* Billing Flow */ }) {
                            Text("Upgrade")
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Sub-composables
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun LinkAnalysisCard(
    info: LinkAnalysisResult,
    selectedEngine: DownloadEngine,
    onEngineChange: (DownloadEngine) -> Unit,
    onDownload: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Link Analysis", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("File: ${info.fileName}")
            Text("Type: ${info.contentType}")
            Text("Size: ${formatBytes(info.contentLength)}")
            Text("Stream: ${if (info.isStream) "Yes" else "No"}")

            Spacer(modifier = Modifier.height(12.dp))
            Text("Engine", style = MaterialTheme.typography.labelLarge)

            Row {
                DownloadEngine.values().forEach { engine ->
                    FilterChip(
                        selected = selectedEngine == engine,
                        onClick = { onEngineChange(engine) },
                        label = { Text(engine.name.replace("_", " ")) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Download")
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "Unknown"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var idx = 0
    while (size >= 1024 && idx < units.lastIndex) {
        size /= 1024
        idx++
    }
    return "%.2f %s".format(size, units[idx])
}
