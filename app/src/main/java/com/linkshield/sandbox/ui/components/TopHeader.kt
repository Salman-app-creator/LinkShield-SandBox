package com.linkshield.sandbox.ui.components

// ─────────────────────────────────────────────────────────────────────────────
// TopHeader.kt — Build-fixed complete version
//
// Wireframe:
// ┌──────┬──────────────────────────────────────────────────────────────────┐
// │      │ [🛡 Shield ON]  [🛡 DNS ▾]  [Trial: 30d Left]  [☀️ ──◯ 🌙]    │
// │ LOGO │──────────────────────────────────────────────────────────────────│
// │      │ [←] [→] [↻]    [🔒 https://example.com/sandbox...            ]  │
// └──────┴──────────────────────────────────────────────────────────────────┘
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeader(
    currentUrl:     String,
    onUrlChange:    (String) -> Unit,
    isShieldActive: Boolean,
    onShieldToggle: () -> Unit,
    trialDaysLeft:  Int,
    isDarkTheme:    Boolean,
    onThemeToggle:  (Boolean) -> Unit,
    onMenuClick:    () -> Unit = {},
    canGoBack:      Boolean   = false,
    canGoForward:   Boolean   = false,
    onBack:         () -> Unit = {},
    onForward:      () -> Unit = {},
    onReload:       () -> Unit = {},
    onNavigate:     () -> Unit = {},
    isLoading:      Boolean   = false
) {
    val context   = LocalContext.current
    val onSurface = MaterialTheme.colorScheme.onSurface

    var showDnsMenu by remember { mutableStateOf(false) }

    val dnsProviders = listOf("Cloudflare", "WARP", "Google", "Quad9", "AdGuard")
    var selectedDns by remember { mutableStateOf(dnsProviders[0]) }

    Surface(
        modifier        = Modifier.fillMaxWidth(),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ── LOGO ──
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentWidth()
                    .padding(end = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Shield,
                        contentDescription = "Logo",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(30.dp)
                    )
                }
            }

            // ── RIGHT COLUMN ──
            Column(modifier = Modifier.weight(1f)) {

                // ══════════════════════════════════════════════════════════════
                // ROW 1: [🛡 Shield ON] [🛡 DNS ▾] [Trial: 30d Left] [☀ ─◯ 🌙]
                // ══════════════════════════════════════════════════════════════
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    // Shield chip
                    FilterChip(
                        selected   = isShieldActive,
                        onClick    = onShieldToggle,
                        label      = {
                            Text(
                                text       = if (isShieldActive) "Shield ON" else "Shield OFF",
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector        = if (isShieldActive)
                                    Icons.Default.Shield else Icons.Default.ShieldMoon,
                                contentDescription = null,
                                modifier           = Modifier.size(13.dp)
                            )
                        },
                        modifier = Modifier.height(28.dp),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor     = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor         = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor   = MaterialTheme.colorScheme.primary
                        )
                    )

                    // DNS dropdown
                    Box {
                        OutlinedButton(
                            onClick        = { showDnsMenu = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier       = Modifier.height(28.dp),
                            shape          = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Security,
                                contentDescription = "DNS",
                                modifier           = Modifier.size(12.dp),
                                tint               = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(selectedDns, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp))
                        }

                        DropdownMenu(
                            expanded         = showDnsMenu,
                            onDismissRequest = { showDnsMenu = false }
                        ) {
                            dnsProviders.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            p,
                                            fontWeight = if (p == selectedDns) FontWeight.Bold else FontWeight.Normal,
                                            color      = if (p == selectedDns)
                                                MaterialTheme.colorScheme.primary else onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (p == selectedDns)
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = { selectedDns = p; showDnsMenu = false }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text    = { Text("Disable DoH", color = MaterialTheme.colorScheme.error) },
                                onClick = { showDnsMenu = false }
                            )
                        }
                    }

                    // Trial badge
                    val badgeColor = when {
                        trialDaysLeft > 7  -> MaterialTheme.colorScheme.secondaryContainer
                        trialDaysLeft > 0  -> MaterialTheme.colorScheme.tertiaryContainer
                        else               -> MaterialTheme.colorScheme.errorContainer
                    }
                    val badgeTextColor = when {
                        trialDaysLeft > 7  -> MaterialTheme.colorScheme.onSecondaryContainer
                        trialDaysLeft > 0  -> MaterialTheme.colorScheme.onTertiaryContainer
                        else               -> MaterialTheme.colorScheme.onErrorContainer
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = badgeColor
                    ) {
                        Text(
                            text       = if (trialDaysLeft > 0) "Trial: ${trialDaysLeft}d Left"
                                         else "Trial Expired",
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color      = badgeTextColor,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Light/Dark Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier         = Modifier.scale(0.78f)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.LightMode,
                            contentDescription = "Light mode",
                            tint               = if (!isDarkTheme) MaterialTheme.colorScheme.primary
                                                 else onSurface.copy(alpha = 0.35f),
                            modifier           = Modifier.size(14.dp)
                        )

                        Switch(
                            checked         = isDarkTheme,
                            onCheckedChange = onThemeToggle,
                            thumbContent    = {
                                Icon(
                                    imageVector        = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    modifier           = Modifier.size(10.dp)
                                )
                            },
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .height(22.dp)
                        )

                        Icon(
                            imageVector        = Icons.Default.DarkMode,
                            contentDescription = "Dark mode",
                            tint               = if (isDarkTheme) MaterialTheme.colorScheme.primary
                                                 else onSurface.copy(alpha = 0.35f),
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // ROW 2: [←] [→] [↻]   [🔒 https://example.com/sandbox...  ]
                // ══════════════════════════════════════════════════════════════
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back
                    IconButton(
                        onClick  = onBack,
                        enabled  = canGoBack,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint               = if (canGoBack) onSurface else onSurface.copy(0.28f),
                            modifier           = Modifier.size(16.dp)
                        )
                    }

                    // Forward
                    IconButton(
                        onClick  = onForward,
                        enabled  = canGoForward,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            tint               = if (canGoForward) onSurface else onSurface.copy(0.28f),
                            modifier           = Modifier.size(16.dp)
                        )
                    }

                    // Refresh
                    IconButton(
                        onClick  = onReload,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint               = onSurface,
                            modifier           = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(3.dp))

                    // ── Address bar pill ──
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Security,
                            contentDescription = "Secure",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        BasicTextField(
                            value         = currentUrl,
                            onValueChange = onUrlChange,
                            singleLine    = true,
                            cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle     = TextStyle(
                                fontSize = 12.sp,
                                color    = onSurface
                            ),
                            keyboardOptions  = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions  = KeyboardActions(onGo = { onNavigate() }),
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                        )
                    }

                    Spacer(modifier = Modifier.width(3.dp))

                    // Go button
                    OutlinedButton(
                        onClick        = onNavigate,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier       = Modifier.height(30.dp),
                        shape          = RoundedCornerShape(6.dp)
                    ) {
                        Text("Go", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
