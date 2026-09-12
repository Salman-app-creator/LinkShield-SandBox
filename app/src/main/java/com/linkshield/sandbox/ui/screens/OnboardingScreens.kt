package com.linkshield.sandbox.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    val scroll = rememberScrollState()
    val canContinue = scroll.maxValue == 0 || scroll.value >= scroll.maxValue - 24
    BackHandler(enabled = true) {}

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "LinkShield Sandbox",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(128.dp).clip(CircleShape)
            )
            Text("LinkShield Sandbox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            Text("Privacy & Security Disclaimer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            DisclaimerBlock("1. Privacy", "The application is designed not to collect or transmit personal browsing information. Users remain responsible for the data and websites they access.")
            DisclaimerBlock("2. Security", "Security features can reduce risk but cannot guarantee that every website, link, download, or network connection is safe.")
            DisclaimerBlock("3. Default Browser", "LinkShield works best when set as your Android default browser — but you can also use it manually without setting it as default.")
            DisclaimerBlock("4. AdGuard", "Blocks web banner ads, pop-ups, and hidden tracking scripts for a cleaner and safer browsing experience.")
            Spacer(Modifier.height(8.dp))
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Button(
                onClick = onAccept,
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth().padding(18.dp).navigationBarsPadding().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Scroll to Continue", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DisclaimerBlock(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EnableShieldScreen(
    onBrowserSet: () -> Unit,
    onRequestBrowserRole: () -> Unit,
    onSkip: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isDefault by remember { mutableStateOf(checkIsDefaultBrowser(context)) }
    var showSkipDialog by remember { mutableStateOf(false) }

    // Poll every 500ms — button updates when user returns from settings
    LaunchedEffect(Unit) {
        while (true) {
            isDefault = checkIsDefaultBrowser(context)
            if (isDefault) break
            kotlinx.coroutines.delay(500)
        }
    }

    // ── Skip Confirmation Dialog ──
    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            title = { Text("Are you sure?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Agar aap default browser set nahi karenge:\n\n" +
                    "❌ WhatsApp ke links Chrome mein khulenge\n" +
                    "❌ Chrome aapka data save karega\n" +
                    "❌ Phishing links detect nahi honge\n\n" +
                    "Aap baad mein Settings se bhi set kar sakte hain.\n\n" +
                    "Abhi set karein?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSkipDialog = false
                    onRequestBrowserRole()
                }) {
                    Text("Haan, Set karein", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSkipDialog = false
                    onSkip()
                }) {
                    Text("Baad mein")
                }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = "LinkShield Sandbox",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(110.dp).clip(CircleShape)
        )
        Spacer(Modifier.height(14.dp))
        Text(if (isDefault) "Shield Enabled!" else "Why LinkShield?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (isDefault) "LinkShield is now your default browser."
            else "Aapke WhatsApp aur Email par jo links aate hain, woh Chrome mein khulte hain. Chrome woh links, unke cookies, aur aapki browsing history sab save karta hai.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // ── Chrome vs LinkShield Comparison ──
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chrome vs LinkShield", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                ComparisonRow(false, "Chrome: Sab data save karta hai")
                ComparisonRow(true, "LinkShield: Sab data delete karta hai")
                ComparisonRow(false, "Chrome: Trackers follow karte hain")
                ComparisonRow(true, "LinkShield: Zero tracking")
                ComparisonRow(false, "Chrome: Phishing links open ho jate hain")
                ComparisonRow(true, "LinkShield: Har link pehle check hota hai")
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Features Card ──
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Feature("Sandbox Browser")
                Feature("Media Grabber")
                Feature("Reader Mode")
                Feature("QR Code Scanner")
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!isDefault) {
            Button(
                onClick = { onRequestBrowserRole() },
                Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Security, null)
                Spacer(Modifier.width(8.dp))
                Text("Enable Shield Protection", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            // ── Skip for Now Button ──
            TextButton(
                onClick = { showSkipDialog = true },
                Modifier.fillMaxWidth()
            ) {
                Text(
                    "Skip for Now — I'll use it manually",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Aap bina default browser set kiye bhi app use kar sakte hain. Sirf manual URL typing ke saath.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(12.dp))

            // ── Manual Settings Link ──
            Text(
                text = "Button kaam nahi kar raha? Open Settings Manually",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openDefaultBrowserSettings(context) }
                    .padding(8.dp)
            )

        } else {
            Button(
                onClick = onBrowserSet,
                Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Continue to LinkShield", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ComparisonRow(isPositive: Boolean, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (isPositive) "✅" else "❌",
            fontSize = 14.sp
        )
        Text(
            text,
            fontSize = 13.sp,
            color = if (isPositive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Feature(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

fun openDefaultBrowserSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_BROWSER) == true) {
                context.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                return
            }
        }
    }
    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
}

fun checkIsDefaultBrowser(context: Context): Boolean {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        } else {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://"))
            val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }.getOrDefault(false)
}
