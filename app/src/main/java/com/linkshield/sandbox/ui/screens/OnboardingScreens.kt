package com.linkshield.sandbox.ui.screens

// ─────────────────────────────────────────────────────────────────────────────
// OnboardingScreens.kt
//
// Contains TWO full-screen composables shown before MainScreen:
//
//   SCREEN 1 — DisclaimerScreen
//     • Full-screen (NOT a dialog) with scrollable T&C text
//     • "Accept & Continue" button only appears after scrolling to bottom
//     • Back press is disabled — user MUST accept
//
//   SCREEN 2 — EnableShieldScreen
//     • Full-screen with large shield icon + "Enable Shield" button
//     • Button opens system Default Browser settings
//     • "Continue" button only activates when app IS confirmed default browser
//     • User CANNOT skip this step — there is no "Skip" or "Later" option
//     • The screen polls isDefaultBrowser() every time it recomposes via
//       a SideEffect driven by a LaunchedEffect timer
// ─────────────────────────────────────────────────────────────────────────────

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN 1: DisclaimerScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    val scrollState = rememberScrollState()
    // Accept button only appears once user reaches within 100px of the bottom
    val hasScrolledToBottom = scrollState.value >= (scrollState.maxValue - 100).coerceAtLeast(0)

    // Disable hardware back — this screen cannot be dismissed
    BackHandler(enabled = true) { /* intentionally blocked */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Fixed header ──────────────────────────────────────────────────────
        Surface(
            modifier        = Modifier.fillMaxWidth(),
            color           = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector        = Icons.Default.Shield,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text       = "LinkShield Setup",
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    textAlign  = TextAlign.Center
                )
                Text(
                    text  = "Please read and accept the terms to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Scrollable T&C content ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            DisclaimerSection(
                number = "1",
                title  = "DMCA Compliance",
                body   = "LinkShield Sandbox does NOT host, cache, or redistribute any copyrighted content. " +
                         "All media downloads are performed directly from original source servers. " +
                         "Users are solely responsible for ensuring they have the right to download " +
                         "any content they access through this app."
            )
            DisclaimerSection(
                number = "2",
                title  = "Intended Use",
                body   = "LinkShield is a privacy and security tool intended for personal use. " +
                         "It provides an isolated sandbox browser, DNS-over-HTTPS protection, " +
                         "and a media grabber for legitimate personal backup purposes only. " +
                         "Use for any illegal, harmful, or unauthorized activity is strictly prohibited."
            )
            DisclaimerSection(
                number = "3",
                title  = "Privacy Policy",
                body   = "This app does NOT collect, store, or transmit any personally identifiable " +
                         "information. All browsing data (cookies, history, cache) is stored only " +
                         "in RAM and automatically wiped when the app is closed. No analytics or " +
                         "telemetry is collected."
            )
            DisclaimerSection(
                number = "4",
                title  = "DNS Shield",
                body   = "The DNS Shield feature routes DNS queries over HTTPS (DoH) using " +
                         "third-party resolvers (Cloudflare, AdGuard, etc.) for privacy. " +
                         "It does NOT act as a VPN, does NOT inspect traffic content, and " +
                         "does NOT modify, log, or share your browsing data."
            )
            DisclaimerSection(
                number = "5",
                title  = "No Warranty",
                body   = "This software is provided 'as is' without warranty of any kind. " +
                         "The developers are not liable for any damages arising from the " +
                         "use or inability to use this software."
            )
            DisclaimerSection(
                number = "6",
                title  = "Default Browser",
                body   = "To intercept and protect links you open from other apps (WhatsApp, " +
                         "Gmail, etc.), LinkShield must be set as your default browser. " +
                         "This is required for the sandbox protection to work. You can " +
                         "change your default browser at any time in Android settings."
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Visual indicator — scroll hint
            if (!hasScrolledToBottom) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text      = "↓  Scroll down to read all terms",
                        style     = MaterialTheme.typography.labelMedium,
                        color     = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Fixed bottom accept button ────────────────────────────────────────
        Surface(
            modifier        = Modifier.fillMaxWidth(),
            color           = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                AnimatedVisibility(
                    visible = hasScrolledToBottom,
                    enter   = fadeIn() + slideInVertically { it / 2 }
                ) {
                    Column {
                        Text(
                            text      = "By tapping Accept, you confirm that you have read, understood, " +
                                        "and agree to all the terms above.",
                            style     = MaterialTheme.typography.bodySmall,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                Button(
                    onClick  = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    enabled  = hasScrolledToBottom,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text       = if (hasScrolledToBottom) "Accept & Continue" else "Scroll to read all terms",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclaimerSection(number: String, title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text       = "$number. $title",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text  = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN 2: EnableShieldScreen
//
// MANDATORY — the user cannot move past this screen until the app is
// confirmed as the system default browser. There is NO skip option.
//
// Flow:
//  1. Show large shield icon + explanation text
//  2. "Enable Shield" button → opens system Default Browser selector
//  3. App polls isDefaultBrowser() every 1 second via LaunchedEffect
//  4. Once confirmed → "Continue" button appears in green
//  5. Tapping "Continue" calls onBrowserSet() which updates DisclaimerManager
//     and triggers navigation to MainScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EnableShieldScreen(onBrowserSet: () -> Unit) {
    val context = LocalContext.current

    // Poll whether the app is currently the default browser
    var isDefault by remember { mutableStateOf(context.isDefaultBrowser()) }

    LaunchedEffect(Unit) {
        // Poll every second — user may switch to settings and come back
        while (!isDefault) {
            delay(1000)
            isDefault = context.isDefaultBrowser()
        }
    }

    // Disable hardware back — this step cannot be skipped
    BackHandler(enabled = true) { /* intentionally blocked */ }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Animated shield icon ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isDefault) listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            ) else listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (isDefault) Icons.Default.CheckCircle else Icons.Default.Shield,
                    contentDescription = "Shield Status",
                    tint               = if (isDefault)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text       = if (isDefault) "Shield Enabled!" else "Enable Shield Protection",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = if (isDefault)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onBackground,
                textAlign  = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text      = if (isDefault)
                    "LinkShield is now your default browser.\n" +
                    "Every link you open from WhatsApp, Gmail, and other apps will be protected."
                else
                    "LinkShield must be set as your default browser to intercept and protect " +
                    "every link you open. This is required for the sandbox to work.\n\n" +
                    "Tap \"Enable Shield\" below, then select LinkShield as your default browser.",
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (!isDefault) {
                // ── Enable Shield button — opens system settings ───────────────
                Button(
                    onClick  = { openDefaultBrowserSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape    = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Shield, null, Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text       = "Enable Shield",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status hint — no skip button here, intentionally
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text      = "⚠ This step is mandatory and cannot be skipped.\n" +
                                    "LinkShield cannot protect your links without being set as default browser.",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(12.dp)
                    )
                }

            } else {
                // ── Continue button — only shows when confirmed default ─────────
                AnimatedVisibility(
                    visible = isDefault,
                    enter   = fadeIn() + slideInVertically { it / 3 }
                ) {
                    Button(
                        onClick  = onBrowserSet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text       = "Continue to LinkShield",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Opens th
