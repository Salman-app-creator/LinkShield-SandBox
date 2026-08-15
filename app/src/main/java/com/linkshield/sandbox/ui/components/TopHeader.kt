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
import androidx.compose.ui.res.painterResource
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
    onMenuClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        // Row with IntrinsicSize.Min allows Logo to take the combined height of Row 1 + Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ================= LEFT: LOGO (Spans Row 1 + Row 2) =================
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Apna App Logo Image ID
                contentDescription = "App Logo",
                modifier = Modifier
                    .fillMaxHeight()
                    .width(52.dp)
                    .padding(end = 6.dp)
                    .clip(CircleShape)
            )

            // ================= RIGHT: STACKED ROW 1 & ROW 2 =================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ------------ ROW 1: Status Badges, Trial & Theme Switch ------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Shield Toggle Chip
                    FilterChip(
                        selected = isShieldActive,
                        onClick = onShieldToggle,
                        label = {
                            Text(
                                if (isShieldActive) "Shield ON" else "Shield OFF",
                                fontSize = 11.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        modifier = Modifier.height(32.dp)
                    )

                    // Shield Icon Badge
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Shield Indicator",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )

                    // Trial Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Trial: ${trialDaysLeft}d Left",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Theme Toggle Switch (☀️ ──◯ 🌙)
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeToggle,
                        thumbContent = {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        modifier = Modifier.scale(0.75f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ------------ ROW 2: Nav Controls & Address Bar ------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Address Bar (No text clipping)
                    OutlinedTextField(
                        value = currentUrl,
                        onValueChange = onUrlChange,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Secure",
                                modifier = Modifier.size(14.dp)
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
