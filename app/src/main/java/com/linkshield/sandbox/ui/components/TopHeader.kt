package com.linkshield.sandbox.ui.components

// ─────────────────────────────────────────────────────────────────────────────
// TopHeader.kt
//
// Wireframe implemented EXACTLY:
//
// ┌──────┬──────────────────────────────────────────────────────────────────┐
// │      │ [🛡 Shield ON]  [🛡 DNS ▾]  [Trial: 30d Left]  [☀️ ──◯ 🌙]    │
// │ LOGO │──────────────────────────────────────────────────────────────────│
// │      │ [←] [→] [↻]    [🔒 https://example.com/sandbox...            ]  │
// └──────┴──────────────────────────────────────────────────────────────────┘
//
// LOGO spans both rows via IntrinsicSize.Min on the outer Row.
// All icon tints use MaterialTheme.colorScheme.onSurface → auto contrast
// in both Dark and Light modes.
//
// Function signature is UNCHANGED from the original file so MainActivity.kt
// and MainScreen.kt require ZERO modifications.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeader(
    // ── Exact same signature as the original — NO changes needed in callers ──
    currentUrl:     String,
    onUrlChange:    (String) -> Unit,
    isShieldActive: Boolean,
    onShieldToggle: () -> Unit,
    trialDaysLeft:  Int,
    isDarkTheme:    Boolean,
    onThemeToggle:  (Boolean) -> Unit,
    onMenuClick:    () -> Unit = {},
    // Optional extras used by UnblockShieldScreen (have defaults so old callers compile)
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

    // Use ic_app_logo — the exact drawable declared in AndroidManifest.xml
    // android:icon="@drawable/ic_app_logo"
    val logoResId = remember(context) {
        // Primary: ic_app_logo (drawable) — same resource Android uses for the launcher icon
        val primary = context.resources.getIdentifier("ic_app_logo", "drawable", context.packageName)
        if (primary != 0) primary
        else {
            // Fallback chain if project is renamed: try mipmap ic_launcher
            val mipmap = context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
            if (mipmap != 0) mipmap
            else context.resources.getIdentifier("ic_launcher_foreground", "drawable", context.packageName)
        }
    }

    var showDnsMenu by remember { mutableStateOf(false) }

    // DNS providers list — kept self-contained so this file has no DnsManager dep
    val dnsProviders = listOf("Cloudflare", "WARP", "Google", "Quad9", "AdGuard")
    var selectedDns by remember { mutableStateOf(dnsProviders[0]) }

    Surface(
        modifier        = Modifier.fillMaxWidth(),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        // ── OUTER ROW: Logo (left, tall) + Stacked rows (right) ───────────────
        // IntrinsicSize.Min on the Row makes the Logo Box match the height of
        // the right-side Column automatically, producing the "merged cell" look.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // ── LOGO — spans both rows via fillMaxHeight ───────────────────────
            Box(
                modifier = Modifier
                    .fillMaxHeight()               // matches the stacked Column's height
                    .wrapContentWidth()
                    .padding(end = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (logoResId != 0) {
                    Image(
                        painter            = painterResource(id = logoResId),
                        contentDescription = "LinkShield",
                        modifier           = Modifier
                            .size(52.dp)           // 52dp → visually large and symmetric
                            .clip(CircleShape)
                    )
                } else {
                    // Fallback shield icon if no logo drawable found
                    Box(
                        modifier         = Modifier
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
            }

            // ── RIGHT COLUMN: Row 1 + Row 2 stacked ───────────────────────────
            Column(modifier = Modifier.weight(1f)) {

                // ══════════════════════════════════════════════════════════════
                // ROW 1: [🛡 Shield ON] [🛡 DNS ▾] [Trial: 30d Left] [☀ ─◯ 🌙]
                //         FilterChip     OutlinedBtn  Surface badge    Switch
                // SpaceBetween so they spread to both edges naturally.
                // ══════════════════════════════════════════════════════════════
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    // 1a. Shield ON / OFF chip
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

                    // 1b. DNS provider dropdown button
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
                                                MaterialTheme.colorScheme.primary
                                            else
                                                onSurface
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

                    // 1c. Trial badge
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

                    // 1d. Light/Dark Switch with sun ☀ and moon 🌙 icons on both ends
                    // Scale 0.75f keeps it compact in the header row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier         = Modifier.scale(0.78f)
                    ) {
                        // Sun icon (Light mode indicator)
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

                        // Moon icon (Dark mode indicator)
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
                //         nav buttons         horizontally scrollable URL bar
                // Buttons are compact (size 28dp) to leave maximum space for bar.
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
                            tint               = onSurface,   // always full contrast
                            modifier           = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(3.dp))

                    // ── Address bar pill ────────────────────────────────────────
                    // Pill-shaped background, scrollable text, 🔒 lock prefix.
                    // Uses BasicTextField so text color is always onSurface —
                    // fixes the "invisible text in Light mode" bug.
                    Row(
                        modifier          = Modifier
                            .weight(1f)
                            .background(
                                color  = MaterialTheme.colorScheme.surfaceVariant,
                                shape  = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Lock icon prefix — HTTPS signal
                        val isHttps = currentUrl.startsWith("https://", ignoreCase = true)
                        Icon(
                            imageVector        = Icons.Default.Lock,
                            contentDescription = if (isHttps) "Secure" else "Not secure",
                            tint               = if (isHttps) MaterialTheme.colorScheme.primary
                                                 else onSurface.copy(alpha = 0.45f),
                            modifier           = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))

                        // URL text field — horizontally scrollable, explicit color
                        BasicTextField(
                            value         = currentUrl,
                            onValueChange = onUrlChange,
                            singleLine    = true,
                            cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle     = TextStyle(
                                fontSize = 12.sp,
                                color    = onSurface   // ← fixes light-mode invisible text bug
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onNavigate() }),
                            decorationBox   = { inner ->
                                if (currentUrl.isEmpty()) {
                                    Text(
                                        "Enter URL or search...",
                                        fontSize = 12.sp,
                                        color    = onSurface.copy(alpha = 0.4f)
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }

                // Progress bar — 2dp, only visible when loading
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier   = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .height(2.dp),
                        color      = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}
