package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Resource safely resolve karne ka logic
    val logoResId = remember(context) {
        val resId = context.resources.getIdentifier("ic_launcher_foreground", "drawable", context.packageName)
        if (resId != 0) resId else context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ================= LEFT: PROPER SIZED LOGO =================
            if (logoResId != 0) {
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ================= RIGHT: ROW 1 & ROW 2 STACKED =================
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // ------------ ROW 1: Badges + Switch ------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Shield Chip
                    FilterChip(
                        selected = isShieldActive,
                        onClick = onShieldToggle,
                        label = {
                            Text(
                                if (isShieldActive) "Shield ON" else "Shield OFF",
                                fontSize = 10.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )

                    // Trial Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Trial: ${trialDaysLeft}d Left",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Theme Toggle
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeToggle,
                        thumbContent = {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp)
                            )
                        },
                        modifier = Modifier.scale(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ------------ ROW 2: Nav Buttons + Address Bar ------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(14.dp))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Fixed Width Address Bar
                    OutlinedTextField(
                        value = currentUrl,
                        onValueChange = onUrlChange,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Secure",
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go)
                    )
                }
            }
        }
    }
}
