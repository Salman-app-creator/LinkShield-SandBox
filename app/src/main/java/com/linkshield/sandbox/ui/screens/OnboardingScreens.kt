package com.linkshield.sandbox.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R
import kotlinx.coroutines.delay

// ── SCREEN 1: DisclaimerScreen ──
@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    val scrollState = rememberScrollState()
    val hasScrolledToBottom = scrollState.value >= (scrollState.maxValue - 100).coerceAtLeast(0)

    BackHandler(enabled = true) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // FIX: App logo instead of generic shield
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "LinkShield Setup",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Please read and accept the terms to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            DisclaimerSection("1", "DMCA Compliance",
                "LinkShield Sandbox does NOT host, cache, or redistribute any copyrighted content. " +
                "All media downloads are performed directly from original source servers. " +
                "Users are solely responsible for ensuring they have the right to download " +
                "any content they access through this app.")
            DisclaimerSection("2", "Intended Use",
                "LinkShield is a privacy and security tool intended for personal use. " +
                "It provides an isolated sandbox browser, DNS-over-HTTPS protection, " +
                "and a media grabber for legitimate personal backup purposes only. " +
                "Use for any illegal, harmful, or unauthorized activity is strictly prohibited.")
            DisclaimerSection("3", "Privacy Policy",
                "This app does NOT collect, store, or transmit any personally identifiable " +
                "information. All browsing data (cookies, history, cache) is stored only " +
                "in RAM and automatically wiped when the app is closed. No analytics or " +
                "telemetry is collected.")
            DisclaimerSection("4", "DNS Shield",
                "The DNS Shield feature routes DNS queries over HTTPS (DoH) using " +
                "third-party resolvers (Cloudflare, AdGuard, etc.) for privacy. " +
                "It does NOT act as a VPN, does NOT inspect traffic content, and " +
                "does NOT modify, log, or share your browsing data.")
            DisclaimerSection("5", "No Warranty",
                "This software is provided 'as is' without warranty of any kind. " +
                "The developers are not liable for any damages arising from the " +
                "use or inability to use this software.")
            DisclaimerSection("6", "Default Browser",
                "To intercept and protect links you open from other apps (WhatsApp, " +
                "Gmail, etc.), LinkShield must be set as your default browser. " +
                "This is required for the sandbox protection to work. You can " +
                "change your default browser at any time in Android settings.")

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasScrolledToBottom) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "↓  Scroll down to read all terms",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                AnimatedVisibility(
                    visible = hasScrolledToBottom,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    Column {
                        Text(
                            text = "By tapping Accept, you confirm that you have read, understood, " +
                                    "and agree to all the terms above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = hasScrolledToBottom,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = if (hasScrolledToBottom) "Accept & Continue" else "Scroll to read all terms",
                        style = MaterialTheme.typography.labelLarge,
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
            text = "$number. $title",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}
// ── SCREEN 2: EnableShieldScreen ──
@Composable
fun EnableShieldScreen(onBrowserSet: () -> Unit) {
    val context = LocalContext.current

    var isDefault: Boolean by remember { mutableStateOf(context.isDefaultBrowser()) }

    LaunchedEffect(Unit) {
        while (!isDefault) {
            delay(1000)
            isDefault = context.isDefaultBrowser()
        }
    }

    BackHandler(enabled = true) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // FIX: App logo instead of generic shield icon
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isDefault) listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            ) else listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDefault) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Shield Enabled",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(90.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "LinkShield Logo",
                        modifier = Modifier.size(90.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isDefault) "" else "(TAP BELOW TO ENABLE)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isDefault) "Shield Enabled!" else "Enable Shield Protection",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDefault)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isDefault)
                    "LinkShield is now your default browser.\n" +
                    "Every link you open from WhatsApp, Gmail, and other apps will be protected."
                else
                    "LinkShield must be set as your default browser to protect every link you open.\n\n" +
                    "Tap the button below, then select LinkShield as your default browser.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (!isDefault) {
                Button(
                    onClick = { openDefaultBrowserSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Shield, null, Modifier.size(22.dp))
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = "Enable Shield Protection",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "⚠ This step is mandatory and cannot be skipped.\n" +
                                "LinkShield cannot protect your links without being set as default browser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                AnimatedVisibility(
                    visible = isDefault,
                    enter = fadeIn() + slideInVertically { it / 3 }
                ) {
                    Button(
                        onClick = onBrowserSet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp))
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "Continue to LinkShield",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Helper functions ──
fun openDefaultBrowserSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
        ) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            // FIX: Removed FLAG_ACTIVITY_NEW_TASK — was breaking the intent
            context.startActivity(intent)
            return
        }
    }
    // Fallback for older Android
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    )
}

fun Context.isDefaultBrowser(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = getSystemService(RoleManager::class.java)
        rm?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
    } else {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://example.com"))
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        resolveInfo?.activityInfo?.packageName == packageName
    }
}
