package com.linkshield.sandbox.ui.grabber

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.dns.DnsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────
private const val TAG                   = "MediaGrabberScreen"
private const val COBALT_API_URL        = "https://api.cobalt.tools/api/json"
private const val FREE_LIMIT            = 20
private const val PREFS_NAME            = "shield_prefs"
private const val KEY_DOWNLOAD_COUNT    = "download_count"
private const val KEY_IS_PRO            = "is_pro"

// Price strings shown in the upgrade sheet
private const val PRICE_PKR             = "Rs. 350"
private const val PRICE_USD             = "$1.25"

// Chrome Mobile UA — same as UnblockShieldScreen for consistency
private const val CHROME_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/124.0.0.0 Mobile Safari/537.36"

// ─────────────────────────────────────────────────────────────────────────────
// URL Sanitization — strip tracking/session query params that cause API 400s.
// Cleaned before every Cobalt API call AND before DownloadManager enqueue.
//
// Strips: ?igsh=, ?si=, &utm_source, &utm_medium, &utm_campaign,
//         &utm_content, &utm_term, ?fbclid=, ?ref=, &ref=, ?s=
// ─────────────────────────────────────────────────────────────────────────────
private val STRIP_PARAMS = listOf(
    "igsh", "si", "utm_source", "utm_medium", "utm_campaign",
    "utm_content", "utm_term", "utm_id", "fbclid", "ref",
    "s", "feature", "app", "src"
)

private fun sanitizeUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    return try {
        val uri     = Uri.parse(trimmed)
        val builder = uri.buildUpon().clearQuery()
        val params  = uri.queryParameterNames
        for (param in params) {
            if (STRIP_PARAMS.none { param.equals(it, ignoreCase = true) }) {
                val value = uri.getQueryParameter(param)
                if (value != null) builder.appendQueryParameter(param, value)
            }
        }
        builder.build().toString()
    } catch (e: Exception) {
        Log.w(TAG, "URL sanitization failed, using raw: ${e.message}")
        trimmed
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Platform detection — used to decide whether Cobalt API supports the URL.
// For unsupported platforms we skip Engine 1 and go straight to Engine 2.
// ─────────────────────────────────────────────────────────────────────────────
private val COBALT_SUPPORTED_HOSTS = listOf(
    "youtube.com", "youtu.be",
    "tiktok.com",
    "instagram.com",
    "twitter.com", "x.com",
    "facebook.com", "fb.watch",
    "soundcloud.com",
    "vimeo.com",
    "dailymotion.com",
    "reddit.com",
    "tumblr.com",
    "bilibili.com",
    "streamable.com",
    "twitch.tv"
)

private fun isCobaltSupported(url: String): Boolean {
    val lower = url.lowercase()
    return COBALT_SUPPORTED_HOSTS.any { lower.contains(it) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────
data class MediaResult(
    val success:          Boolean,
    val url:              String?  = null,
    val filename:         String?  = null,
    val isAudio:          Boolean  = false,
    val isDirectDownload: Boolean  = false,
    val error:            String?  = null
)

// ─────────────────────────────────────────────────────────────────────────────
// MediaGrabberScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGrabberScreen(
    dnsManager:      DnsManager,
    onProRequired:   () -> Unit        = {},
    sharedUrl:       String?           = null,
    extractedVideoUrl: String?         = null
) {
    val context          = LocalContext.current
    val scope            = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val focusManager     = LocalFocusManager.current
    val prefs            = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Trial / Pro state — read from SharedPreferences ───────────────────────
    var isPro             by remember { mutableStateOf(prefs.getBoolean(KEY_IS_PRO, false)) }
    var downloadCount     by remember { mutableIntStateOf(prefs.getInt(KEY_DOWNLOAD_COUNT, 0)) }
    val remainingDownloads get() = if (isPro) Int.MAX_VALUE else maxOf(0, FREE_LIMIT - downloadCount)
    val canDownload       get() = isPro || downloadCount < FREE_LIMIT

    // ── UI state ──────────────────────────────────────────────────────────────
    var urlInput         by rememberSaveable { mutableStateOf("") }
    var isLoading        by remember { mutableStateOf(false) }
    var result           by remember { mutableStateOf<MediaResult?>(null) }
    var error            by remember { mutableStateOf<String?>(null) }
    var preferAudio      by rememberSaveable { mutableStateOf(false) }
    var showUpgradeSheet by remember { mutableStateOf(false) }
    val upgradeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var lastAutoUrl      by remember { mutableStateOf<String?>(null) }

    // ── Auto-fill URL from active WebView tab ─────────────────────────────────
    // `sharedUrl`       = current WebView page URL (from onUrlChanged callback)
    // `extractedVideoUrl` = HTML5 <video> src (from onVideoExtracted callback)
    // Priority: extractedVideoUrl > sharedUrl (direct video link is better)
    LaunchedEffect(extractedVideoUrl, sharedUrl) {
        val candidate = when {
            !extractedVideoUrl.isNullOrBlank() -> extractedVideoUrl
            !sharedUrl.isNullOrBlank()         -> sharedUrl
            else                               -> return@LaunchedEffect
        }
        if (candidate == lastAutoUrl) return@LaunchedEffect
        lastAutoUrl = candidate
        urlInput    = candidate
        error       = null
        result      = null
        Log.d(TAG, "Auto-filled URL: $candidate")

        // Auto-trigger fetch only for known media platforms or direct files
        if (isCobaltSupported(candidate) || isDirectMediaFile(candidate)) {
            if (canDownload) {
                scope.launch {
                    performFetch(
                        rawUrl      = candidate,
                        preferAudio = preferAudio,
                        dnsManager  = dnsManager,
                        context     = context,
                        prefs       = prefs,
                        onStart     = { isLoading = true; error = null; result = null },
                        onSuccess   = { res ->
                            isLoading     = false
                            result        = res
                            downloadCount = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)
                        },
                        onError     = { msg ->
                            isLoading = false
                            error     = msg
                        }
                    )
                }
            }
        }
    }

    // ── Upgrade bottom sheet ──────────────────────────────────────────────────
    if (showUpgradeSheet) {
        UpgradeBottomSheet(
            sheetState   = upgradeSheetState,
            priceLocal   = PRICE_PKR,
            priceUsd     = PRICE_USD,
            onDismiss    = { showUpgradeSheet = false },
            onActivated  = {
                // Called by ProUpgradeDialog / key validation — refresh state
                isPro         = prefs.getBoolean(KEY_IS_PRO, false)
                downloadCount = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)
                showUpgradeSheet = false
            }
        )
    }

    // ── Main screen layout ────────────────────────────────────────────────────
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
                modifier            = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector        = Icons.Default.Download,
                    contentDescription = null,
                    modifier           = Modifier.size(44.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text  = "Media Grabber",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text      = "YouTube • TikTok • Instagram • Twitter/X • and more.\n" +
                                "Active browser URL is auto-filled when you switch here.",
                    style     = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Trial / Pro status badge ──────────────────────────────────────────
        DownloadStatusBadge(
            isPro              = isPro,
            remainingDownloads = remainingDownloads,
            onUpgradeClick     = { showUpgradeSheet = true }
        )

        // ── URL input field ───────────────────────────────────────────────────
        OutlinedTextField(
            value         = urlInput,
            onValueChange = {
                urlInput = it
                error    = null
                result   = null
            },
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("Paste or auto-filled URL") },
            placeholder   = { Text("https://youtube.com/watch?v=...") },
            leadingIcon   = {
                Icon(
                    imageVector        = Icons.Default.Link,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon  = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (urlInput.isNotBlank()) {
                        IconButton(onClick = { urlInput = ""; error = null; result = null }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier           = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text?.trim() ?: ""
                            if (clip.isNotBlank()) {
                                urlInput = clip
                                error    = null
                                result   = null
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "Paste from clipboard",
                            modifier           = Modifier.size(18.dp)
                        )
                    }
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
            shape  = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        )

        // ── Audio / Video toggle ──────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FormatToggleButton(
                label     = "Video",
                selected  = !preferAudio,
                icon      = { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)) },
                modifier  = Modifier.weight(1f),
                onClick   = { preferAudio = false; result = null }
            )
            FormatToggleButton(
                label     = "Audio Only",
                selected  = preferAudio,
                icon      = { Icon(Icons.Default.MusicNote, null, Modifier.size(16.dp)) },
                modifier  = Modifier.weight(1f),
                onClick   = { preferAudio = true; result = null }
            )
        }

        // ── Fetch button ──────────────────────────────────────────────────────
        Button(
            onClick = {
                when {
                    urlInput.isBlank() -> {
                        error = "Please enter or paste a URL first."
                    }
                    !canDownload -> {
                        showUpgradeSheet = true
                    }
                    else -> {
                        focusManager.clearFocus()
                        scope.launch {
                            performFetch(
                                rawUrl      = urlInput,
                                preferAudio = preferAudio,
                                dnsManager  = dnsManager,
                                context     = context,
                                prefs       = prefs,
                                onStart     = { isLoading = true; error = null; result = null },
                                onSuccess   = { res ->
                                    isLoading     = false
                                    result        = res
                                    downloadCount = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)
                                },
                                onError     = { msg ->
                                    isLoading = false
                                    error     = msg
                                    if (!canDownload) showUpgradeSheet = true
                                }
                            )
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
                Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upgrade to Download", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fetch & Download", style = MaterialTheme.typography.labelLarge)
            }
        }

        // ── Error card ────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = error != null,
            enter   = fadeIn(tween(200)) + expandVertically(),
            exit    = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier              = Modifier.padding(14.dp),
                    verticalAlignment     = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Warning,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.error,
                        modifier           = Modifier.size(20.dp)
                    )
                    Text(
                        text  = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
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
                MediaResultCard(
                    result  = res,
                    context = context
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DownloadStatusBadge
// Shows remaining trial count for free users; PRO badge for pro users.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DownloadStatusBadge(
    isPro:              Boolean,
    remainingDownloads: Int,
    onUpgradeClick:     () -> Unit
) {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPro                  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
                remainingDownloads > 5 -> MaterialTheme.colorScheme.surfaceVariant
                remainingDownloads > 0 -> Color(0xFFFF6F00).copy(alpha = 0.15f)
                else                   -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f)
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
                    Icon(
                        imageVector        = Icons.Default.Star,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(20.dp)
                    )
                    Text(
                        text       = "PRO — Unlimited Downloads",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector        = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(20.dp)
                )
            } else {
                Column {
                    Text(
                        text  = "Free Downloads Remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text       = "$remainingDownloads / $FREE_LIMIT",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = when {
                            remainingDownloads > 5 -> MaterialTheme.colorScheme.primary
                            remainingDownloads > 0 -> Color(0xFFFF6F00)
                            else                   -> MaterialTheme.colorScheme.error
                        }
                    )
                }
                TextButton(onClick = onUpgradeClick) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text      = "Upgrade Pro",
                        fontSize  = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FormatToggleButton — Video / Audio Only selector
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FormatToggleButton(
    label:    String,
    selected: Boolean,
    icon:     @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    Surface(
        onClick       = onClick,
        modifier      = modifier,
        shape         = RoundedCornerShape(12.dp),
        color         = if (selected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        contentColor  = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        border        = if (selected)
            androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        else
            null
    ) {
        Row(
            modifier              = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text       = label,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MediaResultCard — shown after a successful fetch
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MediaResultCard(result: MediaResult, context: Context) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier            = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = "Media Ready!",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }

            if (!result.filename.isNullOrBlank()) {
                Text(
                    text  = "📁  ${result.filename}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (result.isDirectDownload) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Text(
                        text     = "✔  Download queued in system tray",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                result.url?.let { url ->
                    Button(
                        onClick  = {
                            val filename = result.filename
                                ?: "linkshield_${System.currentTimeMillis()}.mp4"
                            enqueueDownload(context, url, filename)
                            Toast.makeText(context, "Download started: $filename", Toast.LENGTH_LONG).show()
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

// ─────────────────────────────────────────────────────────────────────────────
// UpgradeBottomSheet — shown when free limit is hit
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpgradeBottomSheet(
    sheetState:  SheetState,
    priceLocal:  String,
    priceUsd:    String,
    onDismiss:   () -> Unit,
    onActivated: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
        modifier         = Modifier.wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Star,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(52.dp)
            )

            Text(
                text       = "Upgrade to LinkShield Pro",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
                textAlign  = TextAlign.Center
            )

            Text(
                text      = "You have used all $FREE_LIMIT free downloads.\n" +
                            "Upgrade once to get unlimited downloads, full Shield DoH bypass, and all future features.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Pricing card
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = "LinkShield Pro",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text       = "$priceLocal  /  $priceUsd",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ProFeatureRow("Unlimited media downloads")
                    ProFeatureRow("Full DNS Shield bypass (no VPN needed)")
                    ProFeatureRow("Auto-grab from any browser tab")
                    ProFeatureRow("HTML5 video fallback extraction")
                    ProFeatureRow("All future updates included")
                }
            }

            // Payment instructions
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text       = "How to Activate:",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text  = "1. Pay $priceLocal / $priceUsd via Easypaisa, JazzCash, or USDT.\n" +
                                "2. Chat on WhatsApp to send payment proof.\n" +
                                "3. Receive your Pro Key and enter it in the Upgrade screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick  = onDismiss.also { onActivated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Star, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Go to Upgrade Screen", style = MaterialTheme.typography.labelLarge)
            }

            TextButton(onClick = onDismiss) {
                Text("Maybe Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProFeatureRow(text: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// performFetch — Dual-Engine fetch logic
//
// Engine 1 (Cobalt API):
//   - Called for all supported platforms (YouTube, TikTok, Instagram, etc.)
//   - URL is sanitized before sending to strip tracking params (fixes HTTP 400)
//   - Sends POST to COBALT_API_URL with {url, vCodec, aFormat, isAudioOnly}
//   - On success: returns MediaResult with download URL + filename
//
// Engine 2 (HTML5 / Direct URL fallback):
//   - Triggered when:
//       (a) Cobalt returns 400/500 or any non-success status
//       (b) The URL is not on a Cobalt-supported platform
//       (c) The URL is a direct media file (.mp4, .mp3, .m3u8, etc.)
//   - Enqueues directly via Android DownloadManager with correct
//     Referer + User-Agent headers so CDN auth checks pass
//
// After any successful download: increments KEY_DOWNLOAD_COUNT in prefs.
// ─────────────────────────────────────────────────────────────────────────────
private suspend fun performFetch(
    rawUrl:      String,
    preferAudio: Boolean,
    dnsManager:  DnsManager,
    context:     Context,
    prefs:       SharedPreferences,
    onStart:     () -> Unit,
    onSuccess:   (MediaResult) -> Unit,
    onError:     (String) -> Unit
) = withContext(Dispatchers.IO) {
    withContext(Dispatchers.Main) { onStart() }

    val isPro         = prefs.getBoolean(KEY_IS_PRO, false)
    val downloadCount = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    // Gate check — should already be checked by caller, but double-guard here
    if (!isPro && downloadCount >= FREE_LIMIT) {
        withContext(Dispatchers.Main) {
            onError("Free download limit reached. Please upgrade to Pro.")
        }
        return@withContext
    }

    val cleanUrl = sanitizeUrl(rawUrl)
    Log.d(TAG, "Fetching: $cleanUrl (sanitized from: $rawUrl)")

    // ── Engine 1: Cobalt API ──────────────────────────────────────────────────
    if (isCobaltSupported(cleanUrl)) {
        try {
            val bodyJson = JSONObject().apply {
                put("url",          cleanUrl)
                put("vCodec",       "h264")
                put("vQuality",     "720")
                put("aFormat",      "mp3")
                put("isAudioOnly",  preferAudio)
                put("isNoTTWatermark", true)
                put("isTTFullAudio",   false)
            }
            val requestBody = bodyJson.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(COBALT_API_URL)
                .post(requestBody)
                .addHeader("Accept",       "application/json")
                .addHeader("User-Agent",   CHROME_MOBILE_UA)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = dnsManager.getClient().newCall(request).execute()
            val body     = response.body?.string() ?: ""

            Log.d(TAG, "Cobalt response ${response.code}: $body")

            if (response.isSuccessful && body.isNotBlank()) {
                val json   = JSONObject(body)
                val status = json.optString("status", "")

                if (status == "stream" || status == "redirect" || status == "success") {
                    val url      = json.optString("url", "")
                    val pickerArray = json.optJSONArray("picker")
                    val finalUrl = when {
                        url.isNotBlank()          -> url
                        pickerArray != null &&
                        pickerArray.length() > 0  ->
                            pickerArray.getJSONObject(0).optString("url", "")
                        else                      -> ""
                    }

                    if (finalUrl.isNotBlank()) {
                        val ext      = if (preferAudio) ".mp3" else ".mp4"
                        val filename = buildFilename(cleanUrl, ext)

                        // Increment download counter
                        if (!isPro) {
                            prefs.edit()
                                .putInt(KEY_DOWNLOAD_COUNT, downloadCount + 1)
                                .apply()
                        }

                        withContext(Dispatchers.Main) {
                            onSuccess(
                                MediaResult(
                                    success  = true,
                                    url      = finalUrl,
                                    filename = filename,
                                    isAudio  = preferAudio
                                )
                            )
                        }
                        return@withContext
                    }
                }

                // Cobalt returned an error status — fall through to Engine 2
                val errorText = json.optString("text", "Cobalt extraction failed (status: $status)")
                Log.w(TAG, "Cobalt non-success: $errorText")
            } else {
                Log.w(TAG, "Cobalt HTTP ${response.code} — falling back to Engine 2")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cobalt Engine 1 exception: ${e.message}")
        }
    }

    // ── Engine 2: Direct URL / HTML5 fallback ─────────────────────────────────
    // Handles: direct media file links, HTML5 <video> src extracted by JS bridge,
    // custom/unsupported site URLs, and Cobalt failures.
    if (isDirectMediaFile(cleanUrl) || !isCobaltSupported(cleanUrl)) {
        try {
            val ext      = detectExtension(cleanUrl)
            val filename = buildFilename(cleanUrl, ext)
            val referer  = extractReferer(cleanUrl)

            enqueueDownload(
                context   = context,
                url       = cleanUrl,
                filename  = filename,
                referer   = referer,
                userAgent = CHROME_MOBILE_UA
            )

            if (!isPro) {
                prefs.edit()
                    .putInt(KEY_DOWNLOAD_COUNT, downloadCount + 1)
                    .apply()
            }

            withContext(Dispatchers.Main) {
                onSuccess(
                    MediaResult(
                        success          = true,
                        url              = cleanUrl,
                        filename         = filename,
                        isDirectDownload = true
                    )
                )
            }
            return@withContext
        } catch (e: Exception) {
            Log.e(TAG, "Engine 2 fallback failed: ${e.message}")
        }
    }

    // Both engines failed
    withContext(Dispatchers.Main) {
        onError(
            "Could not extract media from this URL.\n" +
            "Try copying the direct video link from the browser address bar."
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// enqueueDownload — Android DownloadManager enqueue with headers
// ─────────────────────────────────────────────────────────────────────────────
private fun enqueueDownload(
    context:   Context,
    url:       String,
    filename:  String,
    referer:   String   = "",
    userAgent: String   = CHROME_MOBILE_UA
) {
    val mimeType = detectMimeType(filename)
    val request  = DownloadManager.Request(Uri.parse(url)).apply {
        setTitle(filename)
        setDescription("Downloading via LinkShield")
        setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )
        setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS, "LinkShield/$filename"
        )
        if (referer.isNotBlank()) addRequestHeader("Referer",    referer)
        addRequestHeader("User-Agent", userAgent)
        addRequestHeader("Accept",     "*/*")
        setAllowedOverMetered(true)
        setAllowedOverRoaming(true)
    }
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
    Log.d(TAG, "DownloadManager enqueued: $filename")
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper utilities
// ─────────────────────────────────────────────────────────────────────────────

private val DIRECT_MEDIA_EXTENSIONS = listOf(
    ".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".mov",
    ".flv", ".avi", ".ogg", ".m3u8", ".ts", ".m4v"
)

private fun isDirectMediaFile(url: String): Boolean {
    val lower = url.lowercase()
    return DIRECT_MEDIA_EXTENSIONS.any { lower.contains(it) }
}

private fun detectExtension(url: String): String {
    val lower = url.lowercase()
    return DIRECT_MEDIA_EXTENSIONS.firstOrNull { lower.contains(it) } ?: ".mp4"
}

private fun detectMimeType(filename: String): String {
    val lower = filename.lowercase()
    return when {
        lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".ogg") -> "audio/mpeg"
        lower.endsWith(".webm")                                                    -> "video/webm"
        lower.endsWith(".mkv")                                                     -> "video/x-matroska"
        lower.endsWith(".m3u8")                                                    -> "application/x-mpegURL"
        lower.endsWith(".ts")                                                      -> "video/mp2t"
        else                                                                       -> "video/mp4"
    }
}

private fun buildFilename(url: String, ext: String): String {
    val timestamp = System.currentTimeMillis()
    return try {
        val host = Uri.parse(url).host
            ?.replace("www.", "")
            ?.replace(".", "_")
            ?: "media"
        "linkshield_${host}_$timestamp$ext"
    } catch (_: Exception) {
        "linkshield_$timestamp$ext"
    }
}

private fun extractReferer(url: String): String {
    return try {
        val uri = Uri.parse(url)
        "${uri.scheme}://${uri.host}"
    } catch (_: Exception) {
        url
    }
}
