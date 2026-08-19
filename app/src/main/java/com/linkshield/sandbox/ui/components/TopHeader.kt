package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeader(
    currentUrl: String = "",
    onUrlChange: (String) -> Unit = {},
    isShieldProtectionEnabled: Boolean = true,
    onShieldProtectionToggle: () -> Unit = {},
    isWireGuardEnabled: Boolean = false,
    onWireGuardToggle: () -> Unit = {},
    trialDaysLeft: Int = 30,
    isDarkTheme: Boolean = false,
    onThemeToggle: (Boolean) -> Unit = {},
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    onReload: () -> Unit = {},
    onNavigate: () -> Unit = {},
    isLoading: Boolean = false,
    isProUser: Boolean = false
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT: Spanned Logo (Height spans Row 1 + Row 2)
        Image(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // RIGHT: Stacked Row 1 and Row 2
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ROW 1: Sleek Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Dropdown Button
                Box {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (isShieldProtectionEnabled) "🛡️ Shield 🔻" else "🛡️ Off 🔻",
                            fontSize = 11.sp
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Enable DNS Shield", fontSize = 12.sp) },
                            leadingIcon = {
                                Checkbox(
                                    checked = isShieldProtectionEnabled,
                                    onCheckedChange = {
                                        onShieldProtectionToggle()
                                        dropdownExpanded = false
                                    }
                                )
                            },
                            onClick = {
                                onShieldProtectionToggle()
                                dropdownExpanded = false
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Connect WireGuard", fontSize = 12.sp) },
                            leadingIcon = {
                                Checkbox(
                                    checked = isWireGuardEnabled,
                                    onCheckedChange = {
                                        onWireGuardToggle()
                                        dropdownExpanded = false
                                    }
                                )
                            },
                            onClick = {
                                onWireGuardToggle()
                                dropdownExpanded = false
                            }
                        )
                    }
                }

                // 2. Compact Theme Switcher
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("☀️", fontSize = 10.sp)
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeToggle,
                        modifier = Modifier.scale(0.6f)
                    )
                    Text("🌙", fontSize = 10.sp)
                }

                // 3. Pro/Trial Badge Indicator
                if (isProUser) {
                    Surface(
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "👑 PRO",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "⏳ Trial: ${trialDaysLeft}d",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // ROW 2: Minimalist Navigation & Integrated Address Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(16.dp),
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }

                IconButton(
                    onClick = onForward,
                    enabled = canGoForward,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        modifier = Modifier.size(16.dp),
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }

                IconButton(
                    onClick = onReload,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        modifier = Modifier.size(16.dp)
                    )
                }

                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    singleLine = true,
                    placeholder = { Text("Search or type URL", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "SSL Secure",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onNavigate() }),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
