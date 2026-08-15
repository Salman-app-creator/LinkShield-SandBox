package com.linkshield.sandbox.ui.unblock

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.dns.DohProvider

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnblockShieldScreen(
    dnsManager: DnsManager,
    onMediaFound: (String) -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: UnblockShieldViewModel = viewModel()
) {
    var urlText by remember { mutableStateOf(viewModel.currentUrl) }
    val dohProviders = remember { DohProvider.values().toList() }
    var selectedProvider by remember { mutableStateOf(dnsManager.getCurrentProvider()) }
    var isServerMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.currentUrl) {
        urlText = viewModel.currentUrl
    }

    val mediaUrls by viewModel.mediaUrls.collectAsState()
    LaunchedEffect(mediaUrls) {
        mediaUrls.lastOrNull()?.let { mediaItem ->
            onMediaFound(mediaItem.url)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "App Logo",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LinkShield",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = viewModel.adBlockEnabled,
                            onClick = { viewModel.adBlockEnabled = !viewModel.adBlockEnabled },
                            label = {
                                Text(
                                    if (viewModel.adBlockEnabled) "Shield ON" else "Shield OFF",
                                    fontSize = 11.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(30.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Box {
                            OutlinedButton(
                                onClick = { isServerMenuExpanded = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Server",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedProvider.displayName.split(" ").first(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand",
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = isServerMenuExpanded,
                                onDismissRequest = { isServerMenuExpanded = false }
                            ) {
                                dohProviders.forEach { provider ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = provider.displayName,
                                                fontWeight = if (provider == selectedProvider) FontWeight.Bold else FontWeight.Normal,
                                                color = if (provider == selectedProvider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            selectedProvider = provider
                                            dnsManager.enableDoh(provider)
                                            isServerMenuExpanded = false
                                            viewModel.reload()
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onToggleTheme() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Theme Toggle",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.goBack() },
                        enabled = viewModel.canGoBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { viewModel.goForward() },
                        enabled = viewModel.canGoForward,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = { viewModel.reload() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    BasicTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { viewModel.loadUrl(urlText) },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Go", fontSize = 12.sp)
                    }
                }
            }
        }

        if (viewModel.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                viewModel.getOrCreateWebView(context, 0, dnsManager)
            },
            update = { webView ->
                // WebView managed internally by ViewModel
            }
        )
    }
}
