package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R

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
    var dnsMenu by rememberSaveable { mutableStateOf(false) }
    var selectedDns by rememberSaveable { mutableStateOf("Cloudflare") }
    val providers = listOf("Cloudflare", "Google", "Quad9", "AdGuard")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "LinkShield Sandbox",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HeaderChip(
                        text = if (isShieldActive) "Shield ON" else "Shield OFF",
                        icon = if (isShieldActive) Icons.Default.Security else Icons.Default.ShieldMoon,
                        selected = isShieldActive,
                        onClick = onShieldToggle
                    )

                    Box {
                        HeaderButton(
                            text = selectedDns,
                            icon = Icons.Default.Security,
                            trailing = Icons.Default.ArrowDropDown,
                            onClick = { dnsMenu = true }
                        )
                        DropdownMenu(
                            expanded = dnsMenu,
                            onDismissRequest = { dnsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Secure Network") },
                                leadingIcon = { Icon(Icons.Default.Security, null) },
                                onClick = { dnsMenu = false; onOpenSecure() }
                            )
                            HorizontalDivider()
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider, fontWeight = if (provider == selectedDns) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = { if (provider == selectedDns) Icon(Icons.Default.Check, null) },
                                    onClick = {
                                        selectedDns = provider
                                        dnsMenu = false
                                        onDnsProviderChange(provider)
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Disable DoH", color = MaterialTheme.colorScheme.error) },
                                onClick = { dnsMenu = false; onDnsDisable() }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            if (trialDaysLeft > 0) "Trial: ${trialDaysLeft}d" else "Trial expired",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                        )
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeToggle,
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", Modifier.size(19.dp))
                }
                IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowForward, "Forward", Modifier.size(19.dp))
                }
                IconButton(onClick = onReload, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Refresh, "Reload", Modifier.size(19.dp))
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, "Secure", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(5.dp))
                    BasicTextField(
                        value = currentUrl,
                        onValueChange = onUrlChange,
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onNavigate() }),
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = onNavigate,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text(if (isLoading) "…" else "Go", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(icon, null, Modifier.size(15.dp)) },
        modifier = Modifier.height(32.dp)
    )
}

@Composable
private fun HeaderButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 7.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(icon, null, Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Icon(trailing, null, Modifier.size(14.dp))
    }
}
