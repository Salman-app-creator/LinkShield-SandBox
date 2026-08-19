package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App Logo
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )

            // Right Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dropdown Button for Shield Toggle
                Box {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(if (isShieldProtectionEnabled) "🛡️ Shield 🔻" else "🛡️ Off 🔻")
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Enable Protection") },
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
                    }
                }

                // Theme Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☀️")
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeToggle,
                        modifier = Modifier.scale(0.8f)
                    )
                    Text("🌙")
                }

                // Trial / Pro Badge
                if (isProUser) {
                    Surface(
                        color = Color(0xFFFFD700),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "👑 PRO",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "⏳ Trial: ${trialDaysLeft}d",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
