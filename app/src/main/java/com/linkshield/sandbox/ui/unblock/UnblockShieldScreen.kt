package com.linkshield.sandbox.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.dns.DnsManager

// ─────────────────────────────────────────────────────────────────────────────
// Helper function to calculate remaining trial days
// ─────────────────────────────────────────────────────────────────────────────
fun getRemainingTrialDays(context: Context): Long {
    val prefs = context.getSharedPreferences("linkshield_prefs", Context.MODE_PRIVATE)
    val installTime = prefs.getLong("first_install_time", System.currentTimeMillis())
    val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
    val isProActivated = prefs.getBoolean("is_pro_activated", false)

    if (isProActivated) return -1 // -1 indicates Pro Version Activated

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
    val remainingDays = remember { getRemainingTrialDays(context) }
    val webView = viewModel.getOrCreateWebView(context, 0, dnsManager)

    // Ensure YouTube Auto Ad-Blocker and Active URL capturing are active on current WebView
    DisposableEffect(webView) {
        val originalClient = webView.webViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                originalClient?.onPageStarted(view, url, favicon)
                url?.let {
                    viewModel.updateUrl(it)
                    onUrlCaptured(it)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                originalClient?.onPageFinished(view, url)
                url?.let {
                    viewModel.updateUrl(it)
                    onUrlCaptured(it)
                }

                // 🛡️ YOUTUBE COSMETIC AD-BLOCKER INJECTION ENGINE 🛡️
                if (url != null && (url.contains("youtube.com") || url.contains("youtu.be"))) {
                    val adBlockerScript = """
                        (function() {
                            if (window.__ytAdBlockerInjected) return;
                            window.__ytAdBlockerInjected = true;

                            function removeAds() {
                                // 1. Auto-Skip Video Ads
                                var video = document.querySelector('video');
                                var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                                if (skipBtn) {
                                    try { skipBtn.click(); } catch(e){}
                                }
                                var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
                                if (adShowing && video && !isNaN(video.duration)) {
                                    video.currentTime = video.duration || 999;
                                }

                                // 2. Hide Static / Banner / Overlay Ads
                                var selectors = [
                                    'ytd-promoted-sparkles-web-renderer',
                                    'ytm-promoted-sparkles-web-renderer',
                                    '.ad-banner',
                                    '.ytp-ad-overlay-container',
                                    '#player-ads',
                                    'ytd-banner-promo-renderer',
                                    'ytd-statement-banner-renderer',
                                    '.ytp-ad-text-inline'
                                ];
                                selectors.forEach(function(sel) {
                                    document.querySelectorAll(sel).forEach(function(el) {
                                        el.style.display = 'none';
                                    });
                                });
                            }

                            setInterval(removeAds, 500);
                        })();
                    """.trimIndent()

                    view?.evaluateJavascript(adBlockerScript, null)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return originalClient?.shouldOverrideUrlLoading(view, request) ?: super.shouldOverrideUrlLoading(view, request)
            }
        }

        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Top Shield Header Status
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Side: Shield Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LinkShield Active",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right Side: Dynamic Trial / Pro Badge & DoH Status
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // DoH Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "DoH ON",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Professional Trial / Pro Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (remainingDays.toInt() == -1) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = when {
                                remainingDays.toInt() == -1 -> "PRO ACTIVE"
                                remainingDays > 0 -> "TRIAL: ${remainingDays}D LEFT"
                                else -> "TRIAL EXPIRED"
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (remainingDays.toInt() == -1) MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Navigation & URL Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.goBack() },
                enabled = viewModel.canGoBack
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            IconButton(
                onClick = { viewModel.goForward() },
                enabled = viewModel.canGoForward
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
            }

            IconButton(onClick = { viewModel.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }

            // Browser URL Display Bar
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(21.dp),
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
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Progress Bar when Loading
        if (viewModel.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Main Isolated WebView Container
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
