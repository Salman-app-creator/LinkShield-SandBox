package com.linkshield.sandbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.dns.DohProvider
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.CapturedMediaItem
import com.linkshield.sandbox.ui.MediaGrabberScreen
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel
import com.linkshield.sandbox.ui.disclaimer.FirstLaunchDisclaimerDialog
import com.linkshield.sandbox.ui.license.ProUpgradeDialog
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {

    private lateinit var dnsManager:        DnsManager
    private lateinit var licenseManager:    LicenseManager
    private lateinit var disclaimerManager: DisclaimerManager
    private lateinit var themeManager:      ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dnsManager        = DnsManager(this)
        licenseManager    = LicenseManager(this)
        disclaimerManager = DisclaimerManager(this)
        themeManager      = ThemeManager(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        // URL intercepted from an external app (WhatsApp, Telegram, etc.)
        val interceptedUrl: String? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent?.data?.toString()
            else               -> intent?.getStringExtra("url")
        }

        setContent {
            var isDark by rememberSaveable { mutableStateOf(themeManager.isDarkTheme()) }

            LinkShieldTheme(darkTheme = isDark) {
                LinkShieldApp(
                    dnsManager        = dnsManager,
                    licenseManager    = licenseManager,
                    disclaimerManager = disclaimerManager,
                    interceptedUrl    = interceptedUrl,
                    isDark            = isDark,
                    onToggleTheme     = {
                        isDark = !isDark
                        themeManager.setTheme(
                            if (isDark) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                        )
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root composable
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LinkShieldApp(
    dnsManager:        DnsManager,
    licenseManager:    LicenseManager,
    disclaimerManager: DisclaimerManager,
    interceptedUrl:    String?,
    isDark:            Boolean,
    onToggleTheme:     () -> Unit
) {
    // ── First-launch disclaimer ───────────────────────────────────────────────
    var disclaimerAccepted by rememberSaveable { mutableStateOf(disclaimerManager.hasAccepted()) }
    if (!disclaimerAccepted) {
        FirstLaunchDisclaimerDialog(onAccept = {
            disclaimerManager.accept()
            disclaimerAccepted = true
        })
        return
    }

    // ── ViewModel — Activity-scoped, survives all tab switches ────────────────
    val unblockViewModel: UnblockShieldViewModel = viewModel()

    LaunchedEffect(interceptedUrl) {
        if (!interceptedUrl.isNullOrBlank()) unblockViewModel.loadUrl(interceptedUrl)
    }

    val mediaItems  by unblockViewModel.mediaUrls.collectAsState(initial = emptyList())
    val capturedMedia = remember(mediaItems) {
        mediaItems.map {
            CapturedMediaItem(url = it.url, title = it.title, pageUrl = it.pageUrl, timestamp = it.timestamp)
        }
    }

    // ── Navigation state ──────────────────────────────────────────────────────
    var selectedTab    by rememberSaveable { mutableIntStateOf(0) }
    var showProDialog  by remember { mutableStateOf(false) }

    if (showProDialog) {
        ProUpgradeDialog(
            licenseManager = licenseManager,
            onDismiss      = { showProDialog = false },
            onUnlocked     = { showProDialog = false }
        )
    }

    // ── Back-press: browser history → browser tab → exit ─────────────────────
    BackHandler(enabled = selectedTab != 0 || unblockViewModel.canGoBack) {
        when {
            selectedTab == 0 && unblockViewModel.canGoBack -> unblockViewModel.goBack()
            selectedTab != 0                               -> selectedTab = 0
        }
    }

    // ── Main scaffold ─────────────────────────────────────────────────────────
    Scaffold(
        modifier  = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavBar(
                selectedTab   = selectedTab,
                onTabSelected = { selectedTab = it },
                onProClick    = { showProDialog = true }
            )
        }
    ) { inner ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(inner)
        ) {
            // Browser tab — ALWAYS in composition tree; only alpha/visibility changes.
            // WebView instance inside ViewModel is never destroyed on tab switch.
            UnblockShieldScreen(
                dnsManager = dnsManager,
                viewModel  = unblockViewModel,
                isVisible  = selectedTab == 0,
                isDarkTheme = isDark,
                onToggleTheme = onToggleTheme
            )

            if (selectedTab == 1) {
                MediaGrabberScreen(
                    dnsManager      = dnsManager,
                    licenseManager  = licenseManager,
                    capturedMedia   = capturedMedia,
                    onClearCaptured = { unblockViewModel.clearMedia() },
                    onProRequired   = { showProDialog = true }
                )
            }

            if (selectedTab == 2) {
                SettingsScreen(
                    dnsManager     = dnsManager,
                    licenseManager = licenseManager,
                    isDark         = isDark,
                    onToggleTheme  = onToggleTheme,
                    onUpgradeClick = { showProDialog = true }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom navigation bar — 3 tabs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BottomNavBar(
    selectedTab:   Int,
    onTabSelected: (Int) -> Unit,
    onProClick:    () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            icon     = { Icon(Icons.Default.Home, contentDescription = "Browser") },
            label    = { Text("Browser") },
            selected = selectedTab == 0,
            onClick  = { onTabSelected(0) }
        )
        NavigationBarItem(
            icon     = { Icon(Icons.Default.Download, contentDescription = "Grabber") },
            label    = { Text("Grabber") },
            selected = selectedTab == 1,
            onClick  = { onTabSelected(1) }
        )
        NavigationBarItem(
            icon     = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label    = { Text("Settings") },
            selected = selectedTab == 2,
            onClick  = { if (selectedTab == 2) onProClick() else onTabSelected(2) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen
// Displays: DNS provider selector, trial days remaining, download quota,
// license key activation, dark/light theme toggle.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsScreen(
    dnsManager:     DnsManager,
    licenseManager: LicenseManager,
    isDark:         Boolean,
    onToggleTheme:  () -> Unit,
    onUpgradeClick: () -> Unit
) {
    var isShieldOn   by remember { mutableStateOf(dnsManager.isDohEnabled()) }
    var providerName by remember { mutableStateOf(dnsManager.getCurrentProvider().displayName) }
    var showProviderMenu by remember { mutableStateOf(false) }
    val isPro            = licenseManager.isProUser()
    val trialActive      = licenseManager.trialActive()
    val trialDays        = licenseManager.trialDaysRemaining()
    val remaining        = licenseManager.getRemainingDownloads()

    // License key input state
    var keyInput     by remember { mutableStateOf("") }
    var keyError     by remember { mutableStateOf<String?>(null) }
    var keySuccess   by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // ── DNS Shield toggle ─────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("DNS Shield", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "DoH + SNI fragmentation — bypasses ISP blocks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked         = isShieldOn,
                        onCheckedChange = { on ->
                            isShieldOn = on
                            if (on) {
                                runCatching {
                                    dnsManager.enableDoh()
                                    providerName = dnsManager.getCurrentProvider().displayName
                                }.onFailure { isShieldOn = false; providerName = "Failed" }
                            } else {
                                dnsManager.disableDoh()
                                providerName = "Off"
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Provider selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Provider: $providerName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isShieldOn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isShieldOn) {
                        Box {
                            TextButton(onClick = { showProviderMenu = true }) {
                                Text("Change", style = MaterialTheme.typography.labelMedium)
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded         = showProviderMenu,
                                onDismissRequest = { showProviderMenu = false }
                            ) {
                                DohProvider.entries.forEach { p ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text    = { Text(p.displayName) },
                                        onClick = {
                                            runCatching { dnsManager.enableDoh(p) }
                                            providerName    = dnsManager.getCurrentProvider().displayName
                                            showProviderMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // ── Trial / Pro status ────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isPro        -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    trialActive  -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    else         -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f)
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isPro) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text("PRO USER", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Unlimited downloads & Shield access", style = MaterialTheme.typography.bodyMedium)
                } else if (trialActive) {
                    Text(
                        "Free Trial Active",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "$trialDays days remaining — Shield is free during trial",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Trial Expired",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.error
                    )
                    Text("Upgrade to Pro to continue using DNS Shield & downloads.", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (!isPro) {
                    Text(
                        "Remaining free downloads: ${if (remaining == Int.MAX_VALUE) "∞" else remaining.toString()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onUpgradeClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Text("Upgrade to Pro — Rs. 350 / \$1.25")
                    }
                }
            }
        }

        HorizontalDivider()

        // ── License key activation ────────────────────────────────────────────
        Text("Activate License Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        AnimatedVisibility(visible = keySuccess) {
            Text(
                "Pro activated successfully!",
                color  = Color(0xFF00E676),
                style  = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (!keySuccess) {
            OutlinedTextField(
                value         = keyInput,
                onValueChange = { keyInput = it.uppercase().take(20); keyError = null },
                label         = { Text("License Key (LSHD-XXXX-XXXX-CCCC)") },
                singleLine    = true,
                isError       = keyError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType   = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp)
            )
            keyError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick  = {
                    if (keyInput.isBlank()) { keyError = "Enter your license key"; return@Button }
                    if (licenseManager.validateKey(keyInput.trim())) keySuccess = true
                    else keyError = "Invalid or already used key. Contact support on WhatsApp."
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp)
            ) {
                Text("Activate Pro")
            }
        }

        HorizontalDivider()

        // ── Theme toggle ──────────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Dark Theme", style = MaterialTheme.typography.titleMedium)
            Switch(checked = isDark, onCheckedChange = { onToggleTheme() })
        }

        HorizontalDivider()

        // ── About ─────────────────────────────────────────────────────────────
        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("LinkShield Sandbox v2.0", style = MaterialTheme.typography.bodyMedium)
        Text("Privacy Sandbox + DNS Shield + Media Grabber", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(32.dp))
    }
}
