package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Row 1: Logo | Shield Button | Shield Icon | Trial Badge | Theme Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )

                FilterChip(
                    selected = isShieldActive,
                    onClick = onShieldToggle,
                    label = { Text(if (isShieldActive) "Shield ON" else "Shield OFF", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )

                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Trial: ${trialDaysLeft}d Left", modifier = Modifier.padding(4.dp), fontSize = 10.sp)
                }

                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { onThemeToggle() },
                    thumbContent = {
                        Icon(
                            if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Navigation Arrows + Refresh + URL Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }

                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(20.dp),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    singleLine = true
                )
            }
        }
    }
}
