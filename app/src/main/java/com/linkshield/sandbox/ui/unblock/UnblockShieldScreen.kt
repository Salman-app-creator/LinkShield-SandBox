package com.linkshield.sandbox.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.R
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.dns.DohProvider
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.disclaimer.FirstLaunchDisclaimerDialog
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.ui.Upgrade.UpgradeScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnblockShieldScreen(
    dnsManager: DnsManager,
    viewModel: UnblockShieldViewModel,
    licenseManager: LicenseManager,
    disclaimerManager: DisclaimerManager,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    isVisible: Boolean = true
) {
    if (!isVisible) return

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    // ── CRITICAL FIX: Use mutableState so Compose recomposes when values change ──
    var hasAcceptedDisclaimer by remember { mutableStateOf(disclaimerManager.hasAccepted()) }
    var isFirstLaunchComplete by remember { mutableStateOf(licenseManager.isFirstLaunchComplete()) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showRestrictionDialog by remember { mutableStateOf(false) }
    var showServerMenu by remember { mutableStateOf(false) }
    var addressBarText by remember { mutableStateOf(viewModel.currentUrl) }
    val addressFocusRequester = remember { FocusRequester() }

    LaunchedEffect(viewModel.currentUrl) {
        addressBarText = viewModel.currentUrl
    }

    LaunchedEffect(Unit) {
        if (!licenseManager.isProUser() && !licenseManager.isAccessAllowed()) {
            showRestrictionDialog = true
            selectedTab = 2
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // STEP 1: Mandatory Disclaimer — CANNOT be dismissed
    // ═════════════════════════════════════════════════════════════════
    if (!hasAcceptedDisclaimer) {
        FirstLaunchDisclaimerDialog(
            onAccept = {
                disclaimerManager.accept()
                hasAcceptedDisclaimer = true   // <-- Forces recomposition
            }
        )
        return
    }

    // ═════════════════════════════════════════════════════════════════
    // STEP 2: Mandatory Default Browser — CANNOT be skipped
    // ═════════════════════════════════════════════════════════════════
    if (!isFirstLaunchComplete) {
        EnableProtectionScreen(
            onEnable = {
                openDefaultBrowserSettings(context)
                licenseManager.setFirstLaunchComplete()
                isFirstLaunchComplete = true   // <-- Forces recomposition
            }
        )
        return
    }

    // ═════════════════════════════════════════════════════════════════
    // Restriction Dialog (when trial expired + limit reached)
    // ═════════════════════════════════════════════════════════════════
    if (showRestrictionDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictionDialog = false },
            title = { Text("Upgrade Required", fontWeight = FontWeight.Bold) },
            text = { Text(licenseManager.getRestrictionReason()) },
            confirmButton = {
                Button(onClick = { showRestrictionDialog = false; selectedTab = 2 }) {
                    Text("Upgrade")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictionDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    // ═════════════════════════════════════════════════════════════════
    // MAIN APP SCREEN
    // ═════════════════════════════════════════════════════════════════
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // ── ROW 1: Logo | Shield | Server | Mode | Badge ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(32.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isShieldOn = dnsManager.isShieldPersistedOn()
                        IconButton(
                            onClick = {
                                if (isShieldOn) dnsManager.disableDoh() else dnsManager.enableDoh()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isShieldOn) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                contentDescription = "Shield Toggle",
                                tint = if (isShieldOn) MaterialTheme.colorScheme.primary else Color.Red
                            )
                        }
                        Text(
                            text = if (isShieldOn) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        // Server dropdown
                        Box {
                            IconButton(
                                onClick = { showServerMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Server",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = showServerMenu,
                                onDismissRequest = { showServerMenu = false }
                            ) {
                                DohProvider.entries.forEach { provider ->
                                    DropdownMenuItem(
                                        text = { Text(provider.displayName) },
                                        onClick = {
                                            dnsManager.enableDoh(provider)
                                            showServerMenu = false
                                            Toast.makeText(context, "Server: ${provider.displayName}", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = {
                                            if (dnsManager.getCurrentProvider() == provider) {
                                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Disable DoH") },
                                    onClick = {
                                        dnsManager.disableDoh()
                                        showServerMenu = false
                                    }
                                )
                            }
                        }

                        // Dark/Light toggle
                        IconButton(
                            onClick = onThemeToggle,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "Theme Toggle"
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Trial / Pro badge
                        val badgeText = licenseManager.getStatusBadgeText()
                        val badgeColor = when {
                            licenseManager.isProUser() -> MaterialTheme.colorScheme.primaryContainer
                            licenseManager.isTrialActive() -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeColor
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── ROW 2: Back | Forward | Refresh | Address Bar | Copy ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.goBack() },
                        enabled = viewModel.canGoBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { viewModel.goForward() },
                        enabled = viewModel.canGoForward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(
                        onClick = { viewModel.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }

                    OutlinedTextField(
                        value = addressBarText,
                        onValueChange = { addressBarText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .focusRequester(addressFocusRequester),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        placeholder = { Text("Enter URL or search...", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = {
                            viewModel.loadUrl(addressBarText)
                            focusManager.clearFocus()
                        }),
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(viewModel.currentUrl))
                            Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Shield") },
                    label = { Text("Shield") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        if (!licenseManager.isProUser() && !licenseManager.isTrialActive() && licenseManager.getDownloadCount() >= 20) {
                            selectedTab = 2
                            Toast.makeText(context, "Download limit reached. Upgrade to Pro.", Toast.LENGTH_LONG).show()
                        } else {
                            selectedTab = 1
                        }
                    },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Grabber") },
                    label = { Text("Grabber") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Upgrade") },
                    label = { Text("Upgrade") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ShieldWebView(viewModel = viewModel, dnsManager = dnsManager)
                1 -> MediaGrabberScreen(
                    dnsManager = dnsManager,
                    licenseManager = licenseManager,
                    activeUrl = viewModel.currentUrl,
                    onUpgradeRequired = { selectedTab = 2 }
                )
                2 -> UpgradeScreen(licenseManager = licenseManager)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
// Enable Protection Screen — MANDATORY, cannot be bypassed
// ═════════════════════════════════════════════════════════════════
@Composable
private fun EnableProtectionScreen(onEnable: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Enable LinkShield Protection",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "To activate DNS-over-HTTPS shielding and secure link sandboxing, LinkShield must be set as your default browser. This step is required and cannot be skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onEnable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Protection", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ShieldWebView(
    viewModel: UnblockShieldViewModel,
    dnsManager: DnsManager
) {
    val context = LocalContext.current
    val webView = remember { viewModel.getOrCreateWebView(context, 0, dnsManager) }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { viewModel.updateUrl(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                viewModel.updateLoading(false)
                viewModel.updateNavigationState(
                    view?.canGoBack() == true,
                    view?.canGoForward() == true
                )
                url?.let { viewModel.updateUrl(it) }
                view?.evaluateJavascript(JS_MEDIA_INTERCEPTOR, null)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                return super.shouldInterceptRequest(view, request)
            }
        }
        onDispose { }
    }

    AndroidView(factory = {
