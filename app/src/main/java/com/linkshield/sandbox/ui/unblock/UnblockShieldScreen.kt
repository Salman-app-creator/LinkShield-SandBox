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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.R
import com.linkshield.sandbox.dns.DnsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnblockShieldScreen(
    dnsManager: DnsManager,
    viewModel: UnblockShieldViewModel,
    isVisible: Boolean = true,
    onUrlCaptured: (String) -> Unit = {}
) {
    if (!isVisible) return

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("linkshield_prefs", Context.MODE_PRIVATE) }
    val clipboardManager = LocalClipboardManager.current

    var hasAcceptedTerms by remember { mutableStateOf(prefs.getBoolean("has_accepted_terms", false)) }
    var isDefaultSet by remember { mutableStateOf(prefs.getBoolean("is_default_browser", false)) }
    var isShieldActive by remember { mutableStateOf(prefs.getBoolean("is_shield_active", true)) }
    var isDarkMode by remember { mutableStateOf(prefs.getBoolean("is_dark_mode", false)) }
    var isProActivated by remember { mutableStateOf(prefs.getBoolean("is_pro_activated", false)) }
    var downloadCount by remember { mutableStateOf(prefs.getInt("download_count", 0)) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // STEP 1: Mandatory Disclaimer / Terms
    if (!hasAcceptedTerms) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Disclaimer & Terms", fontWeight = FontWeight.Bold) },
            text = {
                Text("LinkShield Sandbox uses Encrypted DoH (DNS-over-HTTPS) to protect your browsing and bypass blocks safely. Accept to proceed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.edit().putBoolean("has_accepted_terms", true).apply()
                        hasAcceptedTerms = true
                    }
                ) {
                    Text("Accept & Continue")
                }
            }
        )
    }
    // STEP 2: Mandatory Set As Default Browser
    else if (!isDefaultSet) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Enable LinkShield Protection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To enable DoH proxy protection and link sandboxing, LinkShield must be set as your default browser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        openDefaultBrowserSettings(context)
                        prefs.edit().putBoolean("is_default_browser", true).apply()
                        isDefaultSet = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Shield (Set Default)")
                }
            }
        }
    }
    // MAIN APP SCREEN
    else {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                    // ROW 1: Logo, Shield State, Dark Mode Toggle, Trial/Pro Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    isShieldActive = !isShieldActive
                                    prefs.edit().putBoolean("is_shield_active", isShieldActive).apply()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isShieldActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                    contentDescription = "Shield State",
                                    tint = if (isShieldActive) MaterialTheme.colorScheme.primary else Color.Red
                                )
                            }
                            Text(
                                text = if (isShieldActive) "Shield ON" else "OFF",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    isDarkMode = !isDarkMode
                                    prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = "Theme Toggle"
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isProActivated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = if (isProActivated) "PRO UNLOCKED" else "TRIAL MODE",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // ROW 2: Nav Controls & Address Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.goBack() }, enabled = viewModel.canGoBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = { viewModel.goForward() }, enabled = viewModel.canGoForward) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(onClick = { viewModel.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = viewModel.currentUrl.ifBlank { "https://..." },
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
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
                        onClick = { selectedTab = 1 },
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
                    0 -> ShieldWebView(
                        viewModel = viewModel,
                        dnsManager = dnsManager,
                        isShieldActive = isShieldActive,
                        onUrlCaptured = onUrlCaptured
                    )
                    1 -> GrabberTab(
                        currentUrl = viewModel.currentUrl,
                        downloadCount = downloadCount,
                        isPro = isProActivated,
                        onDownloadExecute = {
                            if (!isProActivated && downloadCount >= 20) {
                                selectedTab = 2
                                Toast.makeText(context, "20 Free Downloads Limit Exceeded! Upgrade to Pro.", Toast.LENGTH_LONG).show()
                            } else {
                                downloadCount++
                                prefs.edit().putInt("download_count", downloadCount).apply()
                                Toast.makeText(context, "Parsing & Downloading Media...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    2 -> UpgradeTab(
                        isPro = isProActivated,
                        onActivateKey = { key ->
                            val usedKeys = prefs.getStringSet("claimed_keys", mutableSetOf()) ?: mutableSetOf()
                            if (usedKeys.contains(key)) {
                                Toast.makeText(context, "This License Key is already claimed!", Toast.LENGTH_LONG).show()
                            } else {
                                prefs.edit()
                                    .putBoolean("is_pro_activated", true)
                                    .putStringSet("claimed_keys", usedKeys.toMutableSet().apply { add(key) })
                                    .apply()
                                isProActivated = true
                                Toast.makeText(context, "Pro Features Successfully Unlocked!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCopyAccount = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ShieldWebView(
    viewModel: UnblockShieldViewModel,
    dnsManager: DnsManager,
    isShieldActive: Boolean,
    onUrlCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val webView = remember { viewModel.getOrCreateWebView(context, 0, dnsManager) }

    DisposableEffect(webView, isShieldActive) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    viewModel.updateUrl(it)
                    onUrlCaptured(it)
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return super.shouldInterceptRequest(view, request)
            }
        }
        onDispose { }
    }

    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
}

@Composable
fun GrabberTab(
    currentUrl: String,
    downloadCount: Int,
    isPro: Boolean,
    onDownloadExecute: () -> Unit
) {
    var manualUrl by remember { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Media Grabber & Downloader", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Free Downloads Remaining: ${if (isPro) "UNLIMITED" else "${20 - downloadCount}/20"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = manualUrl,
            onValueChange = { manualUrl = it },
            label = { Text("Target URL / Social Link") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDownloadExecute,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Parse & Download HD Video")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onDownloadExecute,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download Audio Only (MP3)")
        }
    }
}

@Composable
fun UpgradeTab(
    isPro: Boolean,
    onActivateKey: (String) -> Unit,
    onCopyAccount: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Upgrade to Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Account Details for Payment:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Easypaisa: 03001234567")
                    IconButton(onClick = { onCopyAccount("03001234567") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("JazzCash: 03007654321")
                    IconButton(onClick = { onCopyAccount("03007654321") }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isPro) {
            Text("PRO Version is Active on this Device!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        } else {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Enter Purchased License Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (keyInput.isNotBlank()) onActivateKey(keyInput.trim())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Activate Key")
            }
        }
    }
}

private fun openDefaultBrowserSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            context.startActivity(intent)
            return
        }
    }
    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    context.startActivity(intent)
}
