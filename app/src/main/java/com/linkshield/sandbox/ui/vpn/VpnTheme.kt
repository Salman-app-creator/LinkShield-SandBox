package com.linkshield.sandbox.ui.vpn

import androidx.compose.ui.graphics.Color

/**
 * VpnTheme.kt
 *
 * Design tokens for the VPN screen.
 * Dark, security-focused palette consistent with a sandbox/privacy app.
 */
object VpnTheme {

    // ── Background layers ─────────────────────────────────────────────────────
    val backgroundDark      = Color(0xFF0A0E1A)   // Near-black navy
    val surfaceCard         = Color(0xFF111827)   // Dark card bg
    val surfaceElevated     = Color(0xFF1A2235)   // Slightly lighter surface

    // ── Accent ────────────────────────────────────────────────────────────────
    val accentCyan          = Color(0xFF00D4FF)   // Active / connected
    val accentCyanDim       = Color(0xFF0099BB)
    val accentGreen         = Color(0xFF22C55E)   // Connected indicator dot
    val accentRed           = Color(0xFFEF4444)   // Error / disconnected dot
    val accentOrange        = Color(0xFFF59E0B)   // Connecting/busy

    // ── Text ──────────────────────────────────────────────────────────────────
    val textPrimary         = Color(0xFFE2E8F0)
    val textSecondary       = Color(0xFF94A3B8)
    val textMuted           = Color(0xFF475569)

    // ── Button states ─────────────────────────────────────────────────────────
    val buttonConnect       = Color(0xFF00D4FF)
    val buttonDisconnect    = Color(0xFF3B4A6B)
    val buttonBusy          = Color(0xFF1E293B)
    val buttonError         = Color(0xFFDC2626)

    // ── Shield ring gradient stops ────────────────────────────────────────────
    val ringConnected       = listOf(Color(0xFF00D4FF), Color(0xFF0066FF))
    val ringDisconnected    = listOf(Color(0xFF334155), Color(0xFF1E293B))
    val ringError           = listOf(Color(0xFFEF4444), Color(0xFF7F1D1D))
}
