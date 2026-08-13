package com.linkshield.sandbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkshield.sandbox.dns.DohProvider
import com.linkshield.sandbox.dns.DnsManager

// ─────────────────────────────────────────────────────────────────────────────
// UnblockShieldScreen.kt
//
// This composable is ALWAYS kept in the composition tree by MainActivity —
// never removed on tab switch. When isVisible = false, alpha = 0f hides it
// from the user while keeping the WebView instance alive in the ViewModel,
// so active pages NEVER reload or lose state on tab switches.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UnblockShieldScreen(
    dnsManager:    DnsManager,
    viewModel:     UnblockShieldViewModel,
    isVisible:     Boolean,
    isDarkTheme:   Boolean  = true,
    onToggleTheme: () -> Unit = {}
) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Local URL bar text — tracks viewModel.currentUrl but allows in-progress edits
    var urlBarText   by remember { mutableStateOf(viewModel.currentUrl) }
    var showDnsMenu  by remember { mutableStateOf(false) }
    var isDohOn      by remember { mutableStateOf(dnsManager.isDohEnabled()) }

    val shieldTint by animateColorAsState(
        targetValue   = if (isDohOn) Color(0xFF00F0FF) else Color(0xFF90A4AE),
        animationSpec = tween(300),
        label         = "shieldTint"
    )

    // Sync URL bar when viewModel.currentUrl changes from WebView navigation
    val currentUrl = viewModel.currentUrl
    if (!urlBarText.equals(currentUrl, ignoreCase = true) && !viewModel.isLoading) {
        urlBarText = currentUrl
    }

    // Hardware back → WebView history (only when this tab is visible)
    BackHandler(enabled = isVisible && viewModel.canGoBack) {
        viewModel.goBack()
    }

    // ── Root column — alpha controls visibility without destroying WebView ─────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (isVisible) 1f else 0f)
    ) {

        // ── TOP BAR ───────────────────────────────────────────────────────────
        Surface(
            modifier        = Modifier.fillMaxWidth(),
            color           = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column {
                // Shield status strip
                if (isDohOn) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color    = Color(0xFF00F0FF).copy(alpha = 0.10f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Shield,
                                contentDescription = null,
                                tint               = Color(0xFF00F0FF),
                                modifier           = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text      = "SHIELD ACTIVE — ${dnsManager.getCurrentProvider().displayName}",
                                fontSize  = 9.sp,
                                color     = Color(0xFF00F0FF),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color    = Color(0xFF90A4AE).copy(alpha = 0.07f)
                    ) {
                        Text(
                            text      = "SHIELD INACTIVE",
                            fontSize  = 9.sp,
                            color     = Color(0xFF90A4AE),
                            modifier  = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Main controls row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
                ) {
                    // Back
                    IconButton(
                        onClick  = { viewModel.goBack() },
                        enabled  = viewModel.canGoBack && isVisible,
                        modifier = Modifier
                            .size(38.dp)
                            .alpha(if (viewModel.canGoBack) 1f else 0.30f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(20.dp))
                    }

                    // Forward
                    IconButton(
                        onClick  = { viewModel.goForward() },
                        enabled  = viewModel.canGoForward && isVisible,
                        modifier = Modifier
                            .size(38.dp)
                            .alpha(if (viewModel.canGoForward) 1f else 0.30f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", Modifier.size(20.dp))
                    }

                    // Refresh
                    IconButton(
                        onClick  = { viewModel.reload() },
                        enabled  = isVisible,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh", Modifier.size(20.dp))
                    }

                    // URL bar — weight(1f) takes all remaining horizontal space
                    OutlinedTextField(
                        value         = urlBarText,
                        onValueChange = { urlBarText = it },
                        modifier      = Modifier
                            .weight(1f)
                            .height(44.dp),
                        placeholder   = {
                            Text(
                                "Search or enter URL",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        },
                        trailingIcon  = {
                            if (urlBarText.isNotEmpty()) {
                                IconButton(
                                    onClick  = { urlBarText = "" },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Clear", Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine      = true,
                        textStyle       = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction    = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                viewModel.loadUrl(urlBarText)
                            }
                        ),
                        shape  = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                            focusedBorderColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            unfocusedBorderColor    = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    )

                    // Shield / DNS provider dropdown
                    Box {
                        IconButton(
                            onClick  = { if (isVisible) showDnsMenu = true },
                            enabled  = isVisible,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Shield,
                                contentDescription = if (isDohOn) "Shield ON" else "Shield OFF",
                                tint               = shieldTint,
                                modifier           = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded         = showDnsMenu,
                            onDismissRequest = { showDnsMenu = false }
                        ) {
                            Text(
                                "DNS Shield",
                                style      = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            HorizontalDivider()

                            // Disabled option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Disabled",
                                        color = if (!isDohOn)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    if (!isDohOn) Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    dnsManager.disableDoh()
                                    isDohOn     = false
                                    showDnsMenu = false
                                }
                            )

                            HorizontalDivider()

                            // All providers
                            DohProvider.entries.forEach { provider ->
                                val isSelected = isDohOn &&
                                    dnsManager.getCurrentProvider() == provider
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            provider.displayName,
                                            color = if (isSelected) Color(0xFF00F0FF)
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (isSelected) Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF00F0FF)
                                        )
                                    },
                                    onClick = {
                                        runCatching { dnsManager.enableDoh(provider) }
                                        isDohOn     = true
                                        showDnsMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Light / Dark theme toggle
                    IconButton(
                        onClick  = onToggleTheme,
                        enabled  = isVisible,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector        = if (isDarkTheme) Icons.Default.LightMode
                                                 else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme",
                            tint               = if (isDarkTheme) Color(0xFFFFB300)
                                                 else Color(0xFF5F6B7A),
                            modifier           = Modifier.size(19.dp)
                        )
                    }
                }

                // Loading progress strip — 2dp, zero extra vertical space
                AnimatedVisibility(visible = viewModel.isLoading) {
                    LinearProgressIndicator(
                        modifier   = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color      = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        // ── WEBVIEW — fills all remaining space ───────────────────────────────
        // getOrCreateWebView returns the cached instance from the ViewModel,
        // never recreating it on recomposition or tab switch.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    viewModel.getOrCreateWebView(
                        context    = ctx,
                        tabIndex   = 0,
                        dnsManager = dnsManager
                    )
                },
                update  = { _ ->
                    // All live state is captured via ViewModel — no update needed here
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
