package com.linkshield.sandbox.ui.vpn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.vpn.VpnConnectionState
import com.linkshield.sandbox.vpn.isBusy
import com.linkshield.sandbox.vpn.isActive
import com.linkshield.sandbox.vpn.label
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

// ── Screen entry point ────────────────────────────────────────────────────────

@Composable
fun VpnScreen(
    viewModel: VpnViewModel = viewModel()
) {
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()

    // ── VPN permission launcher ────────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // android.app.Activity.RESULT_OK == -1
        if (result.resultCode == -1) {
            viewModel.onPermissionGranted()
        }
        // If user denied: do nothing — state stays Disconnected
    }

    // ── Root container ────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VpnTheme.backgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {

            // ── App header ────────────────────────────────────────────────────
            VpnHeader()

            // ── Animated shield / connect button ──────────────────────────────
            ShieldButton(
                state = vpnState,
                onClick = {
                    viewModel.onToggleVpn(
                        onPermissionRequired = { intent ->
                            permissionLauncher.launch(intent)
                        }
                    )
                }
            )

            // ── Connection status card ────────────────────────────────────────
            StatusCard(state = vpnState)

            // ── Error message ─────────────────────────────────────────────────
            if (vpnState is VpnConnectionState.Error) {
                ErrorBanner(
                    message = (vpnState as VpnConnectionState.Error).message,
                    onDismiss = { viewModel.clearError() }
                )
            }

            // ── Server info row ───────────────────────────────────────────────
            ServerInfoRow()
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun VpnHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = "LinkShield",
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            color      = VpnTheme.textPrimary
        )
        Text(
            text     = "Secure Proxy",
            fontSize = 14.sp,
            color    = VpnTheme.textSecondary
        )
    }
}

// ── Animated Shield Button ────────────────────────────────────────────────────

@Composable
private fun ShieldButton(
    state: VpnConnectionState,
    onClick: () -> Unit
) {
    // Pulsing animation when connected
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = 1.08f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Spinner rotation when busy
    val rotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "spinnerRotation"
    )

    val ringColors = when (state) {
        is VpnConnectionState.Connected     -> VpnTheme.ringConnected
        is VpnConnectionState.Error         -> VpnTheme.ringError
        else                                -> VpnTheme.ringDisconnected
    }

    val buttonBgColor by animateColorAsState(
        targetValue = when (state) {
            is VpnConnectionState.Connected  -> Color(0xFF0A2040)
            is VpnConnectionState.Error      -> Color(0xFF2D0A0A)
            else                             -> Color(0xFF111827)
        },
        label = "buttonBg"
    )

    val scaleModifier = if (state.isActive) {
        Modifier.scale(pulseScale)
    } else {
        Modifier
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(220.dp)
            .then(scaleModifier)
    ) {
        // Outer gradient ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(ringColors)
                )
        )

        // Inner button surface
        Button(
            onClick    = onClick,
            enabled    = !state.isBusy,
            shape      = CircleShape,
            colors     = ButtonDefaults.buttonColors(
                containerColor         = buttonBgColor,
                disabledContainerColor = VpnTheme.buttonBusy
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(200.dp)
        ) {
            Column(
                horizontalAlignment    = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.Center
            ) {
                // Shield / power icon text
                Text(
                    text       = when (state) {
                        is VpnConnectionState.Connected  -> "🔒"
                        is VpnConnectionState.Error      -> "⚠️"
                        else                             -> "🛡️"
                    },
                    fontSize   = 48.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = state.label,
                    label       = "buttonLabel"
                ) { label ->
                    Text(
                        text       = label,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = when (state) {
                            is VpnConnectionState.Connected -> VpnTheme.accentCyan
                            is VpnConnectionState.Error     -> VpnTheme.accentRed
                            else                            -> VpnTheme.textSecondary
                        },
                        textAlign  = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Status Card ───────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(state: VpnConnectionState) {

    // Session duration ticker
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        if (state is VpnConnectionState.Connected) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - state.startTimeMs) / 1000
                delay(1000)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    val dotColor by animateColorAsState(
        targetValue = when (state) {
            is VpnConnectionState.Connected  -> VpnTheme.accentGreen
            is VpnConnectionState.Error      -> VpnTheme.accentRed
            is VpnConnectionState.Connecting,
            is VpnConnectionState.Disconnecting -> VpnTheme.accentOrange
            else                             -> VpnTheme.textMuted
        },
        label = "dotColor"
    )

    val statusLabel = when (state) {
        is VpnConnectionState.Disconnected  -> "Not Protected"
        is VpnConnectionState.Connecting    -> "Establishing tunnel…"
        is VpnConnectionState.Connected     -> "Protected · ${state.serverIp}"
        is VpnConnectionState.Disconnecting -> "Closing tunnel…"
        is VpnConnectionState.Error         -> "Connection failed"
    }

    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = VpnTheme.surfaceCard,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = VpnTheme.surfaceElevated,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pulsing indicator dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text       = statusLabel,
                    fontSize   = 14.sp,
                    color      = VpnTheme.textPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.weight(1f)
                )
            }

            // Session duration (only when connected)
            if (state is VpnConnectionState.Connected) {
                HorizontalDivider(color = VpnTheme.surfaceElevated)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatusMetric(label = "Session", value = formatDuration(elapsedSeconds))
                    StatusMetric(label = "Protocol", value = "Shadowsocks")
                    StatusMetric(label = "Encrypt", value = "ChaCha20")
                }
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = VpnTheme.textMuted)
        Text(text = value, fontSize = 13.sp, color = VpnTheme.accentCyan, fontWeight = FontWeight.SemiBold)
    }
}

// ── Error Banner ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2D1111),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = VpnTheme.accentRed.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "⚠", fontSize = 18.sp)
            Text(
                text     = message,
                fontSize = 13.sp,
                color    = Color(0xFFFCA5A5),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("✕", color = VpnTheme.textMuted)
            }
        }
    }
}

// ── Server Info Row ───────────────────────────────────────────────────────────

@Composable
private fun ServerInfoRow() {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text     = "🌐  Oracle Cloud VPS  ·  ${com.linkshield.sandbox.vpn.ShadowsocksConfig.HOST}",
            fontSize = 12.sp,
            color    = VpnTheme.textMuted,
            textAlign = TextAlign.Center
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatDuration(totalSeconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(totalSeconds)
    val m = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else              "%02d:%02d".format(m, s)
}
