package com.linkshield.sandbox.ui.vpn

// REPO PATH: app/src/main/java/com/linkshield/sandbox/ui/vpn/VpnScreen.kt

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

@Composable
fun VpnScreen(
    viewModel: VpnViewModel = viewModel()
) {
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == -1) {
            viewModel.onPermissionGranted()
        }
    }

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
            VpnHeader()

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

            StatusCard(state = vpnState)

            if (vpnState is VpnConnectionState.Error) {
                ErrorBanner(
                    message = (vpnState as VpnConnectionState.Error).message,
                    onDismiss = { viewModel.clearError() }
                )
            }

            ServerInfoRow()
        }
    }
}

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
            text     = "Secure VPN",
            fontSize = 14.sp,
            color    = VpnTheme.textSecondary
        )
    }
}

@Composable
private fun ShieldButton(
    state: VpnConnectionState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val ringColors = when (state) {
        is VpnConnectionState.Connected -> VpnTheme.ringConnected
        is VpnConnectionState.Error     -> VpnTheme.ringError
        else                            -> VpnTheme.ringDisconnected
    }

    val buttonBgColor by animateColorAsState(
        targetValue = when (state) {
            is VpnConnectionState.Connected -> Color(0xFF0A2040)
            is VpnConnectionState.Error     -> Color(0xFF2D0A0A)
            else                            -> Color(0xFF111827)
        },
        label = "buttonBg"
    )

    val scaleModifier = if (state.isActive) Modifier.scale(pulseScale) else Modifier

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(220.dp).then(scaleModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.sweepGradient(ringColors))
        )

        Button(
            onClick    = onClick,
            enabled    = !state.isBusy,
            shape      = CircleShape,
            colors     = ButtonDefaults.buttonColors(
                containerColor         = buttonBgColor,
                disabledContainerColor = VpnTheme.buttonBusy
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(200.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text     = when (state) {
                        is VpnConnectionState.Connected -> "🔒"
                        is VpnConnectionState.Error     -> "⚠️"
                        else                            -> "🛡️"
                    },
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedContent(targetState = state.label, label = "buttonLabel") { lbl ->
                    Text(
                        text       = lbl,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = when (state) {
                            is VpnConnectionState.Connected -> VpnTheme.accentCyan
                            is VpnConnectionState.Error     -> VpnTheme.accentRed
                            else                            -> VpnTheme.textSecondary
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: VpnConnectionState) {
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
            is VpnConnectionState.Connected                  -> VpnTheme.accentGreen
            is VpnConnectionState.Error                      -> VpnTheme.accentRed
            is VpnConnectionState.Connecting,
            is VpnConnectionState.Disconnecting              -> VpnTheme.accentOrange
            else                                             -> VpnTheme.textMuted
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
        shape    = RoundedCornerShape(16.dp),
        color    = VpnTheme.surfaceCard,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VpnTheme.surfaceElevated, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                Text(
                    text       = statusLabel,
                    fontSize   = 14.sp,
                    color      = VpnTheme.textPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.weight(1f)
                )
            }

            if (state is VpnConnectionState.Connected) {
                HorizontalDivider(color = VpnTheme.surfaceElevated)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatusMetric(label = "Session", value = formatDuration(elapsedSeconds))
                    StatusMetric(label = "Protocol", value = "Psiphon")
                    StatusMetric(label = "Encrypt",  value = "TLS")
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

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = Color(0xFF2D1111),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VpnTheme.accentRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "⚠", fontSize = 18.sp)
            Text(text = message, fontSize = 13.sp, color = Color(0xFFFCA5A5), modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("✕", color = VpnTheme.textMuted) }
        }
    }
}

@Composable
private fun ServerInfoRow() {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
        modifier              = Modifier.fillMaxWidth()
    ) {
        Text(
            text      = "🌐  Psiphon Network  ·  Auto Server",
            fontSize  = 12.sp,
            color     = VpnTheme.textMuted,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(totalSeconds)
    val m = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else              "%02d:%02d".format(m, s)
}
