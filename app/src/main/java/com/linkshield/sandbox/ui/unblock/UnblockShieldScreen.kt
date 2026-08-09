package com.linkshield.sandbox.ui.unblock

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.dns.DnsProvider
import com.linkshield.sandbox.ui.components.TopHeaderBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ShieldViewModel : androidx.lifecycle.ViewModel() {
    private val _url = MutableStateFlow("https://example.com")
    val url: StateFlow<String> = _url
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack
    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _title = MutableStateFlow("Secure Browser")
    val title: StateFlow<String> = _title

    fun updateUrl(u: String) { _url.value = u }
    fun updateNav(back: Boolean, forward: Boolean) {
        _canGoBack.value = back
        _canGoForward.value = forward
    }
    fun setLoading(l: Boolean) { _isLoading.value = l }
    fun setProgress(p: Int) { _progress.value = p }
    fun setTitle(t: String) { _title.value = t }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnblockShieldScreen() {
    val context = LocalContext.current
    val vm: ShieldViewModel = viewModel()

    val url by vm.url.collectAsState()
    val canBack by vm.canGoBack.collectAsState()
    val canForward by vm.canGoForward.collectAsState()
    val progress by vm.progress.collectAsState()
    val loading by vm.isLoading.collectAsState()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var inputUrl by remember { mutableStateOf("") }

    val dnsManager = remember { DnsManager(context) }
    val savedProvider = remember { dnsManager.getSavedProvider() }
    var selectedDns by remember { mutableStateOf(savedProvider) }
    var dnsEnabled by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopHeaderBar()

        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            placeholder = { Text("Enter secure URL...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = {
                    if (inputUrl.isNotBlank()) {
                        val formatted = if (inputUrl.startsWith("http")) inputUrl else "https://$inputUrl"
                        webView?.loadUrl(formatted)
                        vm.updateUrl(formatted)
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Go")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { if (canBack) webView?.goBack() }, enabled = canBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = { webView?.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }
            IconButton(onClick = { if (canForward) webView?.goForward() }, enabled = canForward) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
            }
        }

        if (loading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        AndroidView(
            factory = {
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val u = request?.url?.toString() ?: return false
                            return u.startsWith("http://") || u.startsWith("https://")
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            url?.let { vm.updateUrl(it); vm.setLoading(true) }
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            vm.updateNav(view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                            vm.setLoading(false)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            vm.setProgress(newProgress)
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unblock Shield (DoH)", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = dnsEnabled,
                        onCheckedChange = {
                            dnsEnabled = it
                            if (it) dnsManager.buildClient(selectedDns)
                        }
                    )
                }
                Text(
                    if (dnsEnabled) "ACTIVE (${selectedDns.label})" else "INACTIVE",
                    color = if (dnsEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
                if (dnsEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DnsChip("Cloudflare", selectedDns is DnsProvider.Cloudflare) {
                            selectedDns = DnsProvider.Cloudflare
                            dnsManager.saveProvider(DnsProvider.Cloudflare)
                            dnsManager.buildClient(DnsProvider.Cloudflare)
                        }
                        DnsChip("AdGuard", selectedDns is DnsProvider.AdGuard) {
                            selectedDns = DnsProvider.AdGuard
                            dnsManager.saveProvider(DnsProvider.AdGuard)
                            dnsManager.buildClient(DnsProvider.AdGuard)
                        }
                        DnsChip("Quad9", selectedDns is DnsProvider.Quad9) {
                            selectedDns = DnsProvider.Quad9
                            dnsManager.saveProvider(DnsProvider.Quad9)
                            dnsManager.buildClient(DnsProvider.Quad9)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surface
        )
    )
}
