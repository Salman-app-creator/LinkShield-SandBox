package com.linkshield.sandbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan, onPrimary = Color.Black,
    secondary = NeonGreen, onSecondary = Color.Black,
    background = DarkBackground, surface = SurfaceDark,
    onSurface = TextPrimaryDark, surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark, error = AlertRed,
    outline = NeonCyan.copy(alpha = 0.3f)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF004D40), onPrimary = Color.White,
    secondary = Color(0xFF1B5E20), onSecondary = Color.White,
    background = LightBackground, surface = SurfaceLight,
    onSurface = TextPrimaryLight, surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight, error = AlertRed,
    outline = Color(0xFF004D40).copy(alpha = 0.3f)
)

@Composable
fun LinkShieldTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme, typography = Typography, content = content)
}
