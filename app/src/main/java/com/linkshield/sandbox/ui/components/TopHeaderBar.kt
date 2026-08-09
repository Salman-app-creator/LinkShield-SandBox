package com.linkshield.sandbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linkshield.sandbox.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeaderBar(
    isDarkTheme: Boolean = true,
    onThemeToggle: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "LinkShield Sandbox",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        actions = {
            // Theme Toggle Button
            IconButton(onClick = onThemeToggle) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkTheme) "Light Mode" else "Dark Mode",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Sandbox Active Pill
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .shadow(8.dp, CircleShape)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(NeonGreen.copy(alpha = 0.2f), NeonGreen.copy(alpha = 0.1f))
                        ),
                        shape = CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(NeonGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Sandbox Active",
                        color = NeonGreen,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}
