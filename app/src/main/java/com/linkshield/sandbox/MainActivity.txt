package com.linkshield.sandbox

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.CapturedMediaItem
import com.linkshield.sandbox.ui.MediaGrabberScreen
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel

// ─────────────────────────────────────────────────────────────────────────────
// MainActivity.kt
//
// Responsibilities:
//   1. Initialize a single, persistent DnsManager tied to Activity lifecycle.
//   2. Host Jetpack Compose root with edge-to-edge system bars.
//   3. Bottom navigation switching Browser / Grabber / Settings.
//   4. Share UnblockShieldViewModel across Browser & Grabber so media URLs
//      captured in the WebView flow directly into MediaGrabberScreen
//      without ever destroying the active WebView.
//   5. Clean lifecycle — ViewModel.onCleared destroys WebViews automatically.
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private lateinit var dnsManager: DnsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Persistent DNS shield instance
        dnsManager = DnsManager(this)

        // 2. Edge-to-edge + system-bar configuration
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        setContent {
            LinkShieldTheme {
                // 3. Hoist the Browser ViewModel here so the same instance
                //    (and its WebView cache + media flow) survives tab switches.
                val unblockViewModel: UnblockShieldViewModel = viewModel()
                val mediaItems by unblockViewModel.mediaUrls.collectAsState(initial = emptyList())

                // 4. Map internal MediaItem → CapturedMediaItem for Grabber screen
                val capturedMedia = remember(mediaItems) {
                    mediaItems.map {
                        CapturedMediaItem(
                            url = it.url,
                            title = it.title,
                            pageUrl = it.pageUrl,
                            timestamp = it.timestamp
                        )
                    }
                }

                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf("Browser", "Grabber", "Settings")

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    icon = {
                                        when (index) {
                                            0 -> Icon(Icons.Default.Home, contentDescription = label)
                                            1 -> Icon(Icons.Default.Download, contentDescription = label)
                                            else -> Icon(Icons.Default.Settings, contentDescription = label)
                                        }
                                    },
                                    label = { Text(label) },
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index }
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
                            0 -> UnblockShieldScreen(
                                dnsManager = dnsManager,
                                viewModel = unblockViewModel
                            )
                            1 -> MediaGrabberScreen(
                                dnsManager = dnsManager,
                                capturedMedia = capturedMedia,
                                onClearCaptured = { unblockViewModel.clearMedia() }
                            )
                            else -> SettingsScreen(dnsManager = dnsManager)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
// ═══════════════════════════════════════════════════════════════════════════
// Theme
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun LinkShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// Settings Tab
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun SettingsScreen(dnsManager: DnsManager) {
    var isShieldOn by remember { mutableStateOf(dnsManager.isDohEnabled()) }
    var providerName by remember { mutableStateOf(dnsManager.getCurrentProvider().displayName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // ── DNS Shield toggle ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("DNS Shield", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Bypass ISP blocking with DoH",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = isShieldOn,
                onCheckedChange = { enabled ->
                    isShieldOn = enabled
                    if (enabled) {
                        try {
                            dnsManager.enableDoh()
                            providerName = dnsManager.getCurrentProvider().displayName
                        } catch (_: Exception) {
                            isShieldOn = false
                            providerName = "Failed"
                        }
                    } else {
                        dnsManager.disableDoh()
                        providerName = "Off"
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Provider: $providerName", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        // ── Licence / quota status ─────────────────────────────────────────
        if (dnsManager.isProUser()) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    "PRO USER",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        } else {
            Text(
                "Free downloads remaining: ${dnsManager.getRemainingDownloads()}",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { /* Billing Flow */ }) {
                Text("Upgrade to Pro")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // ── About ──────────────────────────────────────────────────────────
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text("LinkShield Sandbox v1.0")
        Text("Secure browsing with DNS-over-HTTPS")
    }
}
