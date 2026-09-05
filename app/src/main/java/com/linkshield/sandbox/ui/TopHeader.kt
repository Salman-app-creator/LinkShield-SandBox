package com.linkshield.sandbox.ui.components

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/components/TopHeader.kt

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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

// Shield security states
enum class ShieldState {
    CHECKING,   // Grey  - URL check ho rahi hai
    SAFE,       // Green - Safe hai
    SUSPICIOUS, // Yellow - Suspicious
    DANGEROUS   // Red   - Malicious/Phishing
}

@Composable
fun TopHeader(
    currentUrl: String = "",
    onUrlChange: (String) -> Unit = {},
    shieldState: ShieldState = ShieldState.SAFE,
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
    isProUser: Boolean = false,
    // VPN params removed — kept for compile compatibility if called from old code
    isShieldProtectionEnabled: Boolean = true,
    onShieldProtectionToggle: () -> Unit = {},
    isWireGuardEnabled: Boolean = false,
    onWireGuardToggle: () -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    var editingUrl by remember { mutableStateOf(currentUrl) }
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(currentUrl) {
        if (!isEditing) {
            editingUrl = currentUrl
        }
    }

    // Shield icon color — animates smoothly between states
    val shieldColor by animateColorAsState(
        targetValue = when (shieldState) {
            ShieldState.SAFE       -> Color(0xFF4CAF50) // Green
            ShieldState.SUSPICIOUS -> Color(0xFFFFC107) // Yellow
            ShieldState.DANGEROUS  -> Color(0xFFF44336) // Red
            ShieldState.CHECKING   -> Color(0xFF9E9E9E) // Grey
        },
        animationSpec = tween(600),
        label = "shieldColor"
    )

    val shieldEmoji = when (shieldState) {
        ShieldState.SAFE       -> "🛡️"
        ShieldState.SUSPICIOUS -> "⚠️"
        ShieldState.DANGEROUS  -> "🚨"
        ShieldState.CHECKING   -> "🔍"
    }

    val shieldTooltip = when (shieldState) {
        ShieldState.SAFE       -> "Safe"
        ShieldState.SUSPICIOUS -> "Suspicious"
        ShieldState.DANGEROUS  -> "Dangerous!"
        ShieldState.CHECKING   -> "Checking..."
    }

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

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ROW 1: Shield indicator + Theme + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Dynamic Shield — no dropdown, just colored indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = shieldColor.copy(alpha = 0.15f),
                        modifier = Modifier
                            .border(1.dp, shieldColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = shieldEmoji, fontSize = 11.sp)
                            Text(
                                text = shieldTooltip,
                                fontSize = 10.sp,
                                color = shieldColor
                            )
                        }
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

                // Pro/Trial badge
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

            // ROW 2: Nav buttons + Address bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        modifier = Modifier.size(18.dp),
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                               else Color.Gray.copy(alpha = 0.4f)
                    )
                }

                IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, "Forward",
                        modifier = Modifier.size(18.dp),
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface
                               else Color.Gray.copy(alpha = 0.4f)
                    )
                }

                IconButton(onClick = onReload, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Reload", modifier = Modifier.size(18.dp))
                }

                val textColor  = if (isDarkTheme) Color.White else Color(0xFF1E293B)
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
                        Icons.Default.Lock, "SSL",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = editingUrl,
                        onValueChange = {
                            editingUrl = it
                            isEditing = true
                            onUrlChange(it)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = textColor, fontSize = 12.sp),
                        cursorBrush = SolidColor(textColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            keyboardController?.hide()
                            isEditing = false
                            onNavigate()
                        }),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (editingUrl.isEmpty()) {
                                    Text(
                                        "Search or type URL",
                                        color = if (isDarkTheme) Color.Gray else Color.DarkGray,
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (editingUrl.isNotEmpty()) {
                        IconButton(
                            onClick = { editingUrl = ""; isEditing = false; onUrlChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close, "Clear",
                                modifier = Modifier.size(14.dp),
                                tint = if (isDarkTheme) Color.LightGray else Color.Gray
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { keyboardController?.hide(); isEditing = false; onNavigate() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, "Go",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
