package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShieldMoon
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

/**
 * Browser-only header. This component must only be mounted by SandboxBrowserScreen.
 *
 * The Shield dropdown intentionally contains exactly two controls:
 * 1. Shield Protection / AdBlocker
 * 2. WireGuard VPN
 *
 * No server selection or backend settings belong here.
 */
@Composable
fun TopHeader(
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    isShieldProtectionEnabled: Boolean,
    onShieldProtectionToggle: () -> Unit,
    isWireGuardEnabled: Boolean,
    onWireGuardToggle: () -> Unit,
    trialDaysLeft: Int,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onNavigate: () -> Unit,
    isLoading: Boolean = false
) {
    var shieldMenu by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Only the LinkShield logo spans the two header rows.
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "LinkShield Sandbox",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
            )

            Spacer(Modifier.width(6.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { shieldMenu = true },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Icon(
                                if (isShieldProtectionEnabled) Icons.Default.Security else Icons.Default.ShieldMoon,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isShieldProtectionEnabled) "Shield ON" else "Shield OFF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = shieldMenu,
                            onDismissRequest = { shieldMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Shield Protection")
                                            Text(
                                                "AdBlocker",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isShieldProtectionEnabled,
                                            onCheckedChange = { onShieldProtectionToggle() }
                                        )
                                    }
                                },
                                onClick = { onShieldProtectionToggle() }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("WireGuard VPN")
                                            Text(
                                                if (isWireGuardEnabled) "ON" else "OFF",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isWireGuardEnabled,
                                            onCheckedChange = { onWireGuardToggle() }
                                        )
                                    }
                                },
                                onClick = { onWireGuardToggle() }
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

                    Spacer(Modifier.weight(1f))

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeToggle,
                        modifier = Modifier.height(30.dp)
                    )
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
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            "Secure",
                            Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(5.dp))
                        BasicTextField(
                            value = currentUrl,
                            onValueChange = onUrlChange,
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onNavigate() }),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = onNavigate,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 11.dp),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text(
                            if (isLoading) "…" else "Go",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
