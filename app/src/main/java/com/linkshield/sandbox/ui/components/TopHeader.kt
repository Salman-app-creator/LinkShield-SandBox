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
import androidx.compose.ui.draw.scale
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
    var shieldMenuExpanded by rememberSaveable {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 52.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = 4.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "LinkShield Sandbox",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )

            Spacer(Modifier.width(6.dp))

            Box {
                OutlinedButton(
                    onClick = {
                        shieldMenuExpanded = true
                    },
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(
                        horizontal = 8.dp
                    ),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Icon(
                        imageVector =
                            if (isShieldProtectionEnabled) {
                                Icons.Default.Security
                            } else {
                                Icons.Default.ShieldMoon
                            },
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(Modifier.width(3.dp))

                    Text(
                        text =
                            if (isShieldProtectionEnabled) {
                                "Shield ON"
                            } else {
                                "Shield OFF"
                            },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }

                DropdownMenu(
                    expanded = shieldMenuExpanded,
                    onDismissRequest = {
                        shieldMenuExpanded = false
                    }
                ) {

                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Shield Protection")
                                    Text(
                                        "AdBlocker",
                                        style =
                                            MaterialTheme.typography.labelSmall
                                    )
                                }

                                Switch(
                                    checked =
                                        isShieldProtectionEnabled,
                                    onCheckedChange = {
                                        onShieldProtectionToggle()
                                    }
                                )
                            }
                        },
                        onClick = {
                            onShieldProtectionToggle()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("WireGuard VPN")
                                    Text(
                                        if (isWireGuardEnabled) {
                                            "ON"
                                        } else {
                                            "OFF"
                                        },
                                        style =
                                            MaterialTheme.typography.labelSmall
                                    )
                                }

                                Switch(
                                    checked = isWireGuardEnabled,
                                    onCheckedChange = {
                                        onWireGuardToggle()
                                    }
                                )
                            }
                        },
                        onClick = {
                            onWireGuardToggle()
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier.height(24.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text =
                        if (trialDaysLeft > 0) {
                            "Trial: ${trialDaysLeft}d"
                        } else {
                            "Trial expired"
                        },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = 7.dp,
                        vertical = 4.dp
                    )
                )
            }

            Switch(
                checked = isDarkTheme,
                onCheckedChange = onThemeToggle,
                modifier = Modifier.scale(0.8f)
            )

            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Forward",
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onReload,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reload",
                    modifier = Modifier.size(18.dp)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(9.dp)
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Secure",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.width(5.dp))

                BasicTextField(
                    value = currentUrl,
                    onValueChange = onUrlChange,
                    singleLine = true,
                    cursorBrush =
                        SolidColor(
                            MaterialTheme.colorScheme.primary
                        ),
                    textStyle = TextStyle(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Go
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onGo = {
                                onNavigate()
                            }
                        ),
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onNavigate,
                modifier = Modifier.height(34.dp),
                contentPadding =
                    PaddingValues(horizontal = 10.dp),
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
