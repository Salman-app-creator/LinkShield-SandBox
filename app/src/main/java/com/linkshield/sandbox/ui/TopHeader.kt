package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R

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
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT: Logo
        Image(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // RIGHT: Controls + Address Bar
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ROW 1: Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Shield Dropdown
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
                                    onCheckedChange = null
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
                                    onCheckedChange = null
                                )
                            },
                            onClick = {
                                onWireGuardToggle()
                                dropdownExpanded = false
                            }
                        )
                    }
                }

                // Theme Switcher
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

                // Badge Indicator
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

            // ROW 2: Balanced Nav Buttons & Compact Address Bar + GO
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp),
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.4f)
                    )
                }

                IconButton(
                    onClick = onForward,
                    enabled = canGoForward,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        modifier = Modifier.size(18.dp),
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.4f)
                    )
                }

                IconButton(
                    onClick = onReload,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Custom Address Bar with Clear (X) Button
                val textColor = if (isDarkTheme) Color.White else Color(0xFF1E293B)
                val barBgColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                val borderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .background(barBgColor, RoundedCornerShape(17.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(17.dp))
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "SSL Secure",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF4CAF50)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    BasicTextField(
                        value = currentUrl,
                        onValueChange = onUrlChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = textColor,
                            fontSize = 12.sp
                        ),
                        cursorBrush = SolidColor(textColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                keyboardController?.hide()
                                onNavigate()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (currentUrl.isEmpty()) {
                                    Text(
                                        text = "Search or type URL",
                                        color = if (isDarkTheme) Color.Gray else Color.DarkGray,
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    // Clear (X) Button
                    if (currentUrl.isNotEmpty()) {
                        IconButton(
                            onClick = { onUrlChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Text",
                                modifier = Modifier.size(14.dp),
                                tint = if (isDarkTheme) Color.LightGray else Color.Gray
                            )
                        }
                    }
                }

                // GO Button
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onNavigate()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
