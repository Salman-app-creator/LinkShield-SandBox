package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeader(
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    isShieldActive: Boolean,
    onShieldToggle: () -> Unit,
    trialDaysLeft: Int,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onDnsProviderChange: (String) -> Unit = {},
    onDnsDisable: () -> Unit = {},
    onOpenSecure: () -> Unit = {},
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    onReload: () -> Unit = {},
    onNavigate: () -> Unit = {},
    isLoading: Boolean = false
) {
    var showDnsMenu by remember { mutableStateOf(false) }
    val dnsProviders = listOf("Cloudflare", "WARP", "Google", "Quad9", "AdGuard")
    var selectedDns by remember { mutableStateOf(dnsProviders.first()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "LinkShield Sandbox",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                     .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(Modifier.width(6.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = isShieldActive,
                        onClick = onShieldToggle,
                        label = {
                            Text(
                                if (isShieldActive) "Shield ON" else "Shield OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isShieldActive) Icons.Default.Security else Icons.Default.ShieldMoon,
                                null,
                                Modifier.size(12.dp)
                            )
                        },
                        modifier = Modifier.height(26.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showDnsMenu = true },
                            contentPadding = PaddingValues(horizontal = 5.dp),
                            modifier = Modifier.height(26.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Security, "Secure settings", Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(selectedDns, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(13.dp))
                        }

                        DropdownMenu(
                            expanded = showDnsMenu,
                            onDismissRequest = { showDnsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Secure Network") },
                                leadingIcon = { Icon(Icons.Default.Security, null) },
                                onClick = {
                                    showDnsMenu = false
                                    onOpenSecure()
                                }
                            )
                            HorizontalDivider()
                            dnsProviders.forEach { provider ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            provider,
                                            fontWeight = if (provider == selectedDns) FontWeight.Bold else FontWeight.Normal,
                                            color = if (provider == selectedDns) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (provider == selectedDns) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = {
                                        selectedDns = provider
                                        showDnsMenu = false
                                        onDnsProviderChange(provider)
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Disable DoH", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showDnsMenu = false
                                    onDnsDisable()
                                }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (trialDaysLeft > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            if (trialDaysLeft > 0) "Trial ${trialDaysLeft}d" else "Trial Expired",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.scale(0.72f)
                    ) {
                        Icon(Icons.Default.LightMode, null, Modifier.size(13.dp))
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onThemeToggle,
                            modifier = Modifier.height(22.dp)
                        )
                        Icon(Icons.Default.DarkMode, null, Modifier.size(13.dp))
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowBack, "Back", Modifier.size(16.dp))
                    }
                    IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowForward, "Forward", Modifier.size(16.dp))
                    }
                    IconButton(onClick = onReload, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, "Reload", Modifier.size(16.dp))
                    }

                    Spacer(Modifier.width(2.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, "Secure", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        BasicTextField(
                            value = currentUrl,
                            onValueChange = onUrlChange,
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onNavigate() }),
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                        )
                    }

                    Spacer(Modifier.width(3.dp))
                    OutlinedButton(
                        onClick = onNavigate,
                        contentPadding = PaddingValues(horizontal = 7.dp),
                        modifier = Modifier.height(29.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(if (isLoading) "…" else "Go", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
