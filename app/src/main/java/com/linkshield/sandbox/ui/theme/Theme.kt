package com.linkshield.sandbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    secondary = NeonGreen,
    onSecondary = Color.Black,
    background = DarkBackground,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    error = AlertRed,
    onError = Color.Black,
    outline = NeonCyan.copy(alpha = 0.3f)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    secondary = Color(0xFF2E7D32),
    onSecondary = Color.White,
    background = LightBackground,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    error = AlertRed,
    onError = Color.White,
    outline = Color(0xFF00695C).copy(alpha = 0.3f)
)

@Composable
fun LinkShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
