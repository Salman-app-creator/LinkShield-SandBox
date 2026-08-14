package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    onThemeToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // ROW 1: Logo, App Title & Native Sliding Theme Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: Metallic Shield Logo & Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "LinkShield Logo",
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LinkShield",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right Side: Shield Toggle, Trial Badge & Sliding Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = isShieldActive,
                        onClick = onShieldToggle,
                        label = { Text(if (isShieldActive) "Shield ON" else "Shield OFF", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(13.dp)) }
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Trial ${trialDaysLeft}d",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Native Sliding Switch for Dark/Light Theme
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onThemeToggle() },
                        thumbContent = {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "Toggle Theme",
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ROW 2: Navigation & URL Address Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
                }

                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Secure",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}
