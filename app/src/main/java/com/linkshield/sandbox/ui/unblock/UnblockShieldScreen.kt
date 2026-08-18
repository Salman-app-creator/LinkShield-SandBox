package com.linkshield.sandbox.ui.unblock

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import java.io.ByteArrayInputStream
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.SecurityChecker
import com.linkshield.sandbox.adblock.AdBlockEngine
import com.linkshield.sandbox.api.SecurityApiService
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.dns.DohProvider
import com.linkshield.sandbox.ui.components.TopHeader
import com.linkshield.sandbox.ui.grabber.CapturedMediaItem
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.vpn.WireGuardPermissionManager
import com.linkshield.sandbox.vpn.WireGuardVpnManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class MainTab(val label: String) {
    CHECK("Check"),
    BROWSE("Browse"),
    GRAB("Grab"),
    SECURE("Secure")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnblockShieldScreen(
    viewModel: UnblockShieldViewModel = viewModel(),
    initialUrl: String = "",
    isDarkTheme: Boolean = true,
    onThemeToggle: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dnsManager = remember { DnsManager(context.applicationContext) }
    var selectedTab by remember { mutableStateOf(MainTab.BROWSE) }
    var isShieldActive by remember { mutableStateOf(dnsManager.isDohEnabled()) }

    var webView: WebView? by remember { mutableStateOf(null) }
    val startUrl = remember(initialUrl, viewModel.currentUrl) {
        initialUrl.trim().takeIf { it.isNotBlank() }?.let { normalizeUrl(it) }
            ?: viewModel.currentUrl
    }
    var urlInput by remember(startUrl) { mutableStateOf(startUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }

    val capturedMediaList = viewModel.capturedMediaList.map { item ->
        CapturedMediaItem(
            url = item.url,
            title = item.title ?: "Media File",
            mimeType = item.type
        )
    }

    LaunchedEffect(startUrl) {
        if (startUrl.isNotBlank()) viewModel.updateUrl(startUrl)
    }

    BackHandler {
        when {
            selectedTab == MainTab.BROWSE && canGoBack -> webView?.goBack()
            selectedTab != MainTab.BROWSE -> selectedTab = MainTab.BROWSE
            else -> (context as? Activity)?.finish()
        }
    }

    Scaffold(
        topBar = {
            TopHeader(
                currentUrl = urlInput,
                onUrlChange = { urlInput = it },
                isShieldActive = isShieldActive,
                onShieldToggle = {
                    if (dnsManager.isDohEnabled()) dnsManager.disableDoh()
                    else dnsManager.enableDoh()
                    isShieldActive = dnsManager.isDohEnabled()
                },
                trialDaysLeft = 30,
                isDarkTheme = isDarkTheme,
                onThemeToggle = { dark ->
                    onThemeToggle(dark)
                },
                canGoBack = selectedTab == MainTab.BROWSE && canGoBack,
                canGoForward = selectedTab == MainTab.BROWSE && canGoForward,
                onBack = { webView?.goBack() },
                onForward = { webView?.goForward() },
                onReload = { webView?.reload() },
                onNavigate = {
                    keyboardController?.hide()
                    val target = normalizeUrl(urlInput)
                    urlInput = target
                    viewModel.updateUrl(target)
                    selectedTab = MainTab.BROWSE
                    webView?.loadUrl(target)
                },
                isLoading = isLoading,
                onDnsProviderChange = { providerName ->
                    val provider = when (providerName.substringBefore(" ")) {
                        "Cloudflare" -> if (providerName.contains("WARP")) DohProvider.CLOUDFLARE_WARP else DohProvider.CLOUDFLARE
                        "Google" -> DohProvider.GOOGLE
                        "Quad9" -> DohProvider.QUAD9
                        "AdGuard" -> DohProvider.ADGUARD
                        else -> DohProvider.CLOUDFLARE
                    }
                    dnsManager.enableDoh(provider)
                    isShieldActive = true
                },
                onDnsDisable = {
                    dnsManager.disableDoh()
                    isShieldActive = false
                }
            )
        },
        bottomBar = {
            NavigationBar(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                MainTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    MainTab.CHECK -> Icons.Default.Security
                                    MainTab.BROWSE -> Icons.Default.Public
                                    MainTab.GRAB -> Icons.Default.Download
                                    MainTab.SECURE -> Icons.Default.Shield
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainTab.BROWSE -> {
                    BrowserPane(
                        viewModel = viewModel,
                        startUrl = startUrl,
                        urlInput = urlInput,
                        onUrlChanged = { url ->
                            urlInput = url
                            viewModel.updateUrl(url)
                        },
                        onLoadingChanged = { isLoading = it },
                        onProgressChanged = { progress = it },
                        onNavigationChanged = { back, forward ->
                            canGoBack = back
                            canGoForward = forward
                        },
                        onWebViewReady = { webView = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopCenter)
                        )
                    }

                    BadgedBox(
                        badge = {
                            if (capturedMediaList.isNotEmpty()) Badge { Text("${capturedMediaList.size}") }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        FloatingActionButton(
                            onClick = { showMediaSheet = true },
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Detected media")
                        }
                    }

                    if (showMediaSheet) {
                        MediaLinksSheet(
                            context = context,
                            items = capturedMediaList,
                            onDismiss = { showMediaSheet = false },
                            onClear = { viewModel.clearCapturedMedia() }
                        )
                    }
                }

                MainTab.CHECK -> CheckTab()
                MainTab.GRAB -> MediaGrabberScreen()
                MainTab.SECURE -> SecureTab(dnsManager = dnsManager)
            }
        }
    }
}

@Composable
private fun BrowserPane(
    viewModel: UnblockShieldViewModel,
    startUrl: String,
    urlInput: String,
    onUrlChanged: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavigationChanged: (Boolean, Boolean) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    modifier: Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportMultipleWindows(false)

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingChanged(true)
                        url?.let(onUrlChanged)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingChanged(false)
                        onNavigationChanged(view?.canGoBack() == true, view?.canGoForward() == true)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString().orEmpty()
                        if (reqUrl.isNotBlank() && AdBlockEngine.getInstance().shouldBlock(reqUrl)) {
                            return WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                204,
                                "No Content",
                                emptyMap(),
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                        reqUrl.takeIf { isMediaUrl(it) }?.let {
                            val mType = when {
                                it.contains(".mp3", true) -> "Audio"
                                it.contains(".m3u8", true) -> "HLS Stream"
                                else -> "Video"
                            }
                            viewModel.addCapturedMedia(it, mType, view?.title)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val scheme = request?.url?.scheme?.lowercase()
                        return scheme != null && scheme !in setOf("http", "https")
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }

                    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                        viewModel.showCustomView(callback)
                    }

                    override fun onHideCustomView() {
                        viewModel.hideCustomView()
                    }
                }

                onWebViewReady(this)
                loadUrl(startUrl)
            }
        },
        update = { onWebViewReady(it) }
    )
}

@Composable
private fun CheckTab() {
    val scope = rememberCoroutineScope()
    val api = remember { SecurityApiService() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var expandedUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Link Security Check", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Expand shortened links and check the destination before opening it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://example.com/...") }
        )
        Button(
            onClick = {
                val target = normalizeUrl(input)
                if (target.isBlank()) return@Button
                loading = true
                resultText = null
                scope.launch(Dispatchers.IO) {
                    val local = SecurityChecker.analyzeUrl(target)
                    val remote = api.checkAndExpand(target)
                    expandedUrl = remote.second.expandedUrl.takeIf { remote.second.success }
                    resultText = when {
                        remote.first.isMalicious -> "DANGEROUS: ${remote.first.message}"
                        remote.first.isSuspicious || local.isDangerous -> "SUSPICIOUS: ${remote.first.message.ifBlank { local.warnings.joinToString() }}"
                        else -> "No threat detected. Local score: ${local.score}/100"
                    }
                    loading = false
                }
            },
            enabled = input.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Check Link")
        }
        resultText?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (it.startsWith("DANGEROUS")) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(it, fontWeight = FontWeight.Bold)
                    expandedUrl?.let { url ->
                        Spacer(Modifier.height(8.dp))
                        Text("Expanded: $url", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecureTab(dnsManager: DnsManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    val vpnManager = remember { WireGuardVpnManager(context.applicationContext) }
    var dohEnabled by remember { mutableStateOf(dnsManager.isDohEnabled()) }
    var vpnConnected by remember { mutableStateOf(vpnManager.isConnected()) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Secure Network", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DNS-over-HTTPS", fontWeight = FontWeight.Bold)
                Text(
                    if (dohEnabled) "DNS requests are routed through the selected DoH resolver."
                    else "DoH is disabled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (dohEnabled) dnsManager.disableDoh() else dnsManager.enableDoh()
                        dohEnabled = dnsManager.isDohEnabled()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (dohEnabled) "Disable DNS Shield" else "Enable DNS Shield") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("WireGuard", fontWeight = FontWeight.Bold)
                Text(
                    if (vpnManager.hasConfiguration()) "A valid WireGuard configuration is installed."
                    else "No WireGuard configuration is installed; the VPN engine will not start without one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (vpnConnected) {
                            scope.launch {
                                vpnManager.disconnect()
                                vpnConnected = vpnManager.isConnected()
                            }
                        } else if (vpnManager.hasConfiguration() && activity != null) {
                            val permissionManager = WireGuardPermissionManager(activity)
                            if (permissionManager.prepare()) {
                                scope.launch {
                                    val result = vpnManager.connect()
                                    vpnConnected = result.isSuccess && vpnManager.isConnected()
                                    message = result.exceptionOrNull()?.message
                                }
                            } else {
                                message = "Approve the Android VPN permission, then tap Connect again."
                            }
                        }
                    },
                    enabled = vpnConnected || vpnManager.hasConfiguration(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (vpnConnected) "Disconnect WireGuard" else "Connect WireGuard") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaLinksSheet(
    context: Context,
    items: List<CapturedMediaItem>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Detected Media (${items.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (items.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear All") }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text("No media links detected yet.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    items(items) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Media Link", item.url))
                                    Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) return trimmed
    return "https://$trimmed"
}

private fun isMediaUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mp3") || lower.contains(".webm") || lower.contains(".m4a") || lower.contains(".mpd")
}
