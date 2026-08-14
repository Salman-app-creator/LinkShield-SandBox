package com.linkshield.sandbox.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.dns.DnsManager

fun getRemainingTrialDays(context: Context): Long {
    val prefs = context.getSharedPreferences("linkshield_prefs", Context.MODE_PRIVATE)
    val installTime = prefs.getLong("first_install_time", System.currentTimeMillis())
    val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
    val isProActivated = prefs.getBoolean("is_pro_activated", false)

    if (isProActivated) return -1

    val elapsedTime = System.currentTimeMillis() - installTime
    val remainingMillis = thirtyDaysInMillis - elapsedTime
    val remainingDays = remainingMillis / (1000 * 60 * 60 * 24)

    return if (remainingDays > 0) remainingDays else 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnblockShieldScreen(
    dnsManager: DnsManager,
    viewModel: UnblockShieldViewModel,
    isVisible: Boolean,
    onUrlCaptured: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("linkshield_prefs", Context.MODE_PRIVATE) }
    
    var showAcceptDialog by remember { mutableStateOf(!prefs.getBoolean("has_accepted_terms", false)) }
    var isShieldActive by remember { mutableStateOf(prefs.getBoolean("is_shield_active", true)) }
    var isStrictDoH by remember { mutableStateOf(prefs.getBoolean("is_strict_doh", true)) }
    val remainingDays = remember { getRemainingTrialDays(context) }

    val webView = viewModel.getOrCreateWebView(context, 0, dnsManager)

    // Terms & Conditions / Accept Dialog
    if (showAcceptDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Welcome to LinkShield Sandbox", fontWeight = FontWeight.Bold) },
            text = {
                Text("This app uses Encrypted DoH (DNS-over-HTTPS) to secure your web traffic and bypass restricted connections safely. Please accept to proceed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.edit().putBoolean("has_accepted_terms", true).apply()
                        showAcceptDialog = false
                    }
                ) {
                    Text("Accept & Continue")
                }
            }
        )
    }

    DisposableEffect(webView, isShieldActive, isStrictDoH) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    viewModel.updateUrl(it)
                    onUrlCaptured(it)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    viewModel.updateUrl(it)
                    onUrlCaptured(it)
                }

                if (url != null && (url.contains("youtube.com") || url.contains("youtu.be"))) {
                    val adBlockerScript = """
                        (function() {
                            if (window.__ytAdBlockerInjected) return;
                            window.__ytAdBlockerInjected = true;
                            function removeAds() {
                                var video = document.querySelector('video');
                                var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                                if (skipBtn) { try { skipBtn.click(); } catch(e){} }
                                var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
                                if (adShowing && video && !isNaN(video.duration)) {
                                    video.currentTime = video.duration || 999;
                                }
                                var selectors = ['.ad-banner', '.ytp-ad-overlay-container', '#player-ads', 'ytd-promoted-sparkles-web-renderer'];
                                selectors.forEach(function(sel) {
                                    document.querySelectorAll(sel).forEach(function(el) { el.style.display = 'none'; });
                                });
                            }
                            setInterval(removeAds, 500);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(adBlockerScript, null)
                }
            }

            // DoH Interceptor to bypass ISP blocks / ERR_CONNECTION_RESET
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (isShieldActive && request != null && request.isForMainFrame) {
                    val url = request.url.toString()
                    val response = dnsManager.resolveAndFetch(url, isStrictDoH)
                    if (response != null) return response
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 4.dp)
    ) {
        // Top Shield Header Status Card with Toggle Switch & Mode Controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isShieldActive) MaterialTheme.colorScheme.surfaceVariant 
                                 else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                isShieldActive = !isShieldActive
                                prefs.edit().putBoolean("is_shield_active", isShieldActive).apply()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isShieldActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                contentDescription = "Toggle Shield",
                                tint = if (isShieldActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isShieldActive) "LinkShield Active" else "Shield Disabled",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Strict DoH / Normal Mode Switch
                        FilterChip(
                            selected = isStrictDoH,
                            onClick = {
                                isStrictDoH = !isStrictDoH
                                prefs.edit().putBoolean("is_strict_doh", isStrictDoH).apply()
                            },
                            label = { 
                                Text(
                                    if (isStrictDoH) "DoH Strict" else "DoH Auto", 
                                    fontSize = 10.sp
                                ) 
                            },
                            modifier = Modifier.height(26.dp)
                        )

                        // Dynamic Trial / Pro Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (remainingDays.toInt() == -1) MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = when {
                                    remainingDays.toInt() == -1 -> "PRO ACTIVE"
                                    remainingDays > 0 -> "TRIAL: ${remainingDays}D LEFT"
                                    else -> "EXPIRED"
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Navigation Bar & URL Input Display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.goBack() }, enabled = viewModel.canGoBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = { viewModel.goForward() }, enabled = viewModel.canGoForward) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = { viewModel.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = viewModel.currentUrl.ifBlank { "https://..." },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (viewModel.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Isolated WebView Window
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
