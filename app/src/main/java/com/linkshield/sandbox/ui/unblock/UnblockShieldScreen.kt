package com.linkshield.sandbox.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.R
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.dns.DohProvider
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.Upgrade.UpgradeScreen
import com.linkshield.sandbox.ui.disclaimer.FirstLaunchDisclaimerDialog
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen

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
    val capturedMedia by viewModel.mediaUrls.collectAsState()

    var hasAcceptedDisclaimer by remember {
        mutableStateOf(disclaimerManager.hasAccepted())
    }

    var isFirstLaunchComplete by remember {
        mutableStateOf(licenseManager.isFirstLaunchComplete())
    }

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    var showRestrictionDialog by remember {
        mutableStateOf(false)
    }

    var showServerMenu by remember {
        mutableStateOf(false)
    }

    var addressBarText by remember {
        mutableStateOf(viewModel.currentUrl)
    }

    val addressFocusRequester = remember {
        FocusRequester()
    }

    LaunchedEffect(viewModel.currentUrl) {
        addressBarText = viewModel.currentUrl
    }

    LaunchedEffect(Unit) {
        if (!licenseManager.isAccessAllowed()) {
            showRestrictionDialog = true
            selectedTab = 2
        }
    }

    if (!hasAcceptedDisclaimer) {
        FirstLaunchDisclaimerDialog {
            disclaimerManager.accept()
            hasAcceptedDisclaimer = true
        }
        return
    }

    if (!isFirstLaunchComplete) {
        EnableProtectionScreen {
            openDefaultBrowserSettings(context)
            licenseManager.setFirstLaunchComplete()
            isFirstLaunchComplete = true
        }
        return
    }

    if (showRestrictionDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestrictionDialog = false
            },
            title = {
                Text(
                    "Upgrade Required",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    licenseManager.getRestrictionReason()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestrictionDialog = false
                        selectedTab = 2
                    }
                ) {
                    Text("Upgrade")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestrictionDialog = false
                    }
                ) {
                    Text("Later")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(
                        horizontal = 8.dp,
                        vertical = 5.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        painter = painterResource(
                            id = R.mipmap.ic_launcher
                        ),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(40.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val shieldOn =
                            dnsManager.isShieldPersistedOn()

                        IconButton(
                            onClick = {
                                if (shieldOn) {
                                    dnsManager.disableDoh()
                                } else {
                                    dnsManager.enableDoh()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector =
                                    if (shieldOn) {
                                        Icons.Default.Shield
                                    } else {
                                        Icons.Default.ShieldMoon
                                    },
                                contentDescription = "Shield Toggle",
                                tint =
                                    if (shieldOn) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Red
                                    }
                            )
                        }

                        Text(
                            text = if (shieldOn) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        Box {
                            IconButton(
                                onClick = {
                                    showServerMenu = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "DNS Server"
                                )
                            }

                            DropdownMenu(
                                expanded = showServerMenu,
                                onDismissRequest = {
                                    showServerMenu = false
                                }
                            ) {
                                DohProvider.entries.forEach { provider ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(provider.displayName)
                                        },
                                        onClick = {
                                            dnsManager.enableDoh(provider)
                                            showServerMenu = false

                                            Toast.makeText(
                                                context,
                                                "DNS: ${provider.displayName}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        leadingIcon = {
                                            if (
                                                dnsManager.getCurrentProvider() ==
                                                provider
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    null
                                                )
                                            }
                                        }
                                    )
                                }

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = {
                                        Text("Use Android DNS")
                                    },
                                    onClick = {
                                        dnsManager.disableDoh()
                                        showServerMenu = false
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = onThemeToggle,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector =
                                    if (isDarkTheme) {
                                        Icons.Default.DarkMode
                                    } else {
                                        Icons.Default.LightMode
                                    },
                                contentDescription = "Theme Toggle"
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                licenseManager.isProUser() ->
                                    MaterialTheme.colorScheme.primaryContainer

                                licenseManager.isTrialActive() ->
                                    MaterialTheme.colorScheme.secondaryContainer

                                else ->
                                    MaterialTheme.colorScheme.errorContainer
                            }
                        ) {
                            Text(
                                text =
                                    licenseManager.getStatusBadgeText(),
                                modifier = Modifier.padding(
                                    horizontal = 7.dp,
                                    vertical = 3.dp
                                ),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.height(5.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.goBack()
                        },
                        enabled = viewModel.canGoBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back"
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.goForward()
                        },
                        enabled = viewModel.canGoForward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            "Forward"
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.reload()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            "Refresh"
                        )
                    }

                    OutlinedTextField(
                        value = addressBarText,
                        onValueChange = {
                            addressBarText = it
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .focusRequester(
                                addressFocusRequester
                            ),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 12.sp
                        ),
                        placeholder = {
                            Text(
                                "Enter URL or search...",
                                fontSize = 12.sp
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                viewModel.loadUrl(addressBarText)
                                focusManager.clearFocus()
                            }
                        ),
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor =
                                MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor =
                                MaterialTheme.colorScheme.surface
                        )
                    )

                    IconButton(
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(
                                    viewModel.currentUrl
                                )
                            )

                            Toast.makeText(
                                context,
                                "URL copied",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            "Copy URL"
                        )
                    }
                }
            }
        },
                bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                    },
                    icon = {
                        Icon(
                            Icons.Default.Shield,
                            "Shield"
                        )
                    },
                    label = {
                        Text("Shield")
                    }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        if (!licenseManager.canDownload()) {
                            selectedTab = 2

                            Toast.makeText(
                                context,
                                licenseManager.getRestrictionReason(),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            selectedTab = 1
                        }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Download,
                            "Grabber"
                        )
                    },
                    label = {
                        Text("Grabber")
                    }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                    },
                    icon = {
                        Icon(
                            Icons.Default.Star,
                            "Upgrade"
                        )
                    },
                    label = {
                        Text("Upgrade")
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> ShieldWebView(
                    viewModel = viewModel,
                    dnsManager = dnsManager
                )

                1 -> MediaGrabberScreen(
                    dnsManager = dnsManager,
                    licenseManager = licenseManager,
                    activeUrl = viewModel.currentUrl,
                    capturedMedia = capturedMedia,
                    onClearCaptured = viewModel::clearMedia,
                    onUpgradeRequired = {
                        selectedTab = 2
                    }
                )

                2 -> UpgradeScreen(
                    licenseManager = licenseManager
                )
            }
        }
    }
}

@Composable
private fun EnableProtectionScreen(
    onEnable: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    "Enable LinkShield Protection",
                    style =
                        MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Set LinkShield as the default browser to enable the sandbox.",
                    style =
                        MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = onEnable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Default.OpenInBrowser,
                        null
                    )

                    Spacer(
                        Modifier.width(8.dp)
                    )

                    Text(
                        "Enable Protection",
                        fontWeight = FontWeight.Bold
                    )
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

    val webView = remember {
        viewModel.getOrCreateWebView(
            context,
            0,
            dnsManager
        )
    }

    AndroidView(
        factory = {
            webView
        },
        update = { view ->
            view.layoutParams =
                view.layoutParams.apply {
                    width =
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT

                    height =
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                }
        },
        modifier = Modifier.fillMaxSize()
    )
}
private fun openDefaultBrowserSettings(
    context: Context
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager =
            context.getSystemService(
                RoleManager::class.java
            )

        if (
            roleManager?.isRoleAvailable(
                RoleManager.ROLE_BROWSER
            ) == true
        ) {
            context.startActivity(
                roleManager.createRequestRoleIntent(
                    RoleManager.ROLE_BROWSER
                )
            )

            return
        }
    }

    context.startActivity(
        Intent(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
        )
    )
}
        
