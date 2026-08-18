package com.linkshield.sandbox.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkshield.sandbox.R
import kotlinx.coroutines.delay

@Composable
fun DisclaimerScreen(
    onAccept: () -> Unit
) {

    BackHandler(enabled = true) {}

    val scrollState =
        rememberScrollState()

    val reachedBottom =
        scrollState.maxValue == 0 ||
                scrollState.value >= scrollState.maxValue - 40

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Image(
                    painter = painterResource(
                        R.drawable.ic_app_logo
                    ),
                    contentDescription = "LinkShield",
                    modifier = Modifier.size(82.dp)
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                Text(
                    "LinkShield Sandbox",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    "Privacy & Security Disclaimer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {

            DisclaimerSection(
                "1. Intended Use",
                "LinkShield Sandbox is a privacy and security tool designed to open links inside an isolated browser environment."
            )

            DisclaimerSection(
                "2. Privacy",
                "The application is designed not to collect or transmit personal browsing information. Users remain responsible for the data and websites they access."
            )

            DisclaimerSection(
                "3. Security",
                "Security features can reduce risk but cannot guarantee that every website, link, download, or network connection is safe."
            )

            DisclaimerSection(
                "4. Default Browser",
                "LinkShield must be selected as the Android default browser so links opened from other applications can be routed through LinkShield."
            )

            DisclaimerSection(
                "5. User Responsibility",
                "Do not use LinkShield for illegal, harmful, fraudulent, or unauthorized activities. Always respect applicable laws and website terms."
            )

            DisclaimerSection(
                "6. No Warranty",
                "The software is provided as-is. No guarantee is made that every threat, malicious website, phishing attempt, or unsafe download will be detected."
            )

            Spacer(
                Modifier.height(20.dp)
            )

            Text(
                if (reachedBottom)
                    "You have reached the end of the disclaimer."
                else
                    "Please scroll down to read the complete disclaimer.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(
                Modifier.height(20.dp)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp
        ) {

            Button(
                onClick = onAccept,
                enabled = reachedBottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {

                Text(
                    if (reachedBottom)
                        "Accept & Continue"
                    else
                        "Scroll to Continue",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DisclaimerSection(
    title: String,
    body: String
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {

        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(
            Modifier.height(7.dp)
        )

        Text(
            body,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun EnableShieldScreen(
    onBrowserSet: () -> Unit,
    onRequestBrowserRole: () -> Unit
) {

    val context =
        LocalContext.current

    var isDefault by remember {
        mutableStateOf(
            checkIsDefaultBrowser(context)
        )
    }

    LaunchedEffect(Unit) {

        while (true) {

            val current =
                checkIsDefaultBrowser(context)

            if (current != isDefault) {
                isDefault = current
            }

            if (current) {
                break
            }

            delay(500)
        }
    }

    BackHandler(enabled = true) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
            .verticalScroll(
                rememberScrollState()
            )
            .padding(24.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Spacer(
            Modifier.height(35.dp)
        )

        Image(
            painter = painterResource(
                R.drawable.ic_app_logo
            ),
            contentDescription = "LinkShield",
            modifier = Modifier.size(120.dp)
        )

        Spacer(
            Modifier.height(22.dp)
        )

        if (isDefault) {

            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Enabled",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(50.dp)
            )

            Spacer(
                Modifier.height(12.dp)
            )
        }

        Text(
            if (isDefault)
                "Shield Enabled"
            else
                "Enable Shield Protection",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            Modifier.height(14.dp)
        )

        Text(
            if (isDefault)
                "LinkShield is now your default browser."
            else
                "LinkShield must be selected as your Android default browser to protect links opened from other applications.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(
            Modifier.height(30.dp)
        )

        if (!isDefault) {

            Button(
                onClick = {
                    onRequestBrowserRole()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp)
            ) {

                Icon(
                    Icons.Default.Shield,
                    contentDescription = null
                )

                Spacer(
                    Modifier.width(10.dp)
                )

                Text(
                    "Enable Shield",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(
                Modifier.height(14.dp)
            )

            Text(
                "Android Settings will open. Select LinkShield under the Default Browser option.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        } else {

            Button(
                onClick = onBrowserSet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp)
            ) {

                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null
                )

                Spacer(
                    Modifier.width(10.dp)
                )

                Text(
                    "Continue to LinkShield",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(
            Modifier.height(30.dp)
        )
    }
}

fun openDefaultBrowserSettings(
    context: Context
) {

    try {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val roleManager =
                context.getSystemService(
                    RoleManager::class.java
                )

            if (
                roleManager != null &&
                roleManager.isRoleAvailable(
                    RoleManager.ROLE_BROWSER
                )
            ) {

                val intent =
                    roleManager.createRequestRoleIntent(
                        RoleManager.ROLE_BROWSER
                    )

                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                context.startActivity(intent)

                return
            }
        }

    } catch (_: Exception) {
    }

    try {

        val intent =
            Intent(
                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        context.startActivity(intent)

        return

    } catch (_: Exception) {
    }

    try {

        val intent =
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse(
                    "package:${context.packageName}"
                )
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        context.startActivity(intent)

    } catch (_: Exception) {

        val intent =
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        context.startActivity(intent)
    }
}

fun checkIsDefaultBrowser(
    context: Context
): Boolean {

    return try {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val roleManager =
                context.getSystemService(
                    RoleManager::class.java
                )

            roleManager?.isRoleHeld(
                RoleManager.ROLE_BROWSER
            ) == true

        } else {

            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://example.com")
                )

            val resolveInfo =
                context.packageManager.resolveActivity(
                    intent,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                )

            resolveInfo
                ?.activityInfo
                ?.packageName ==
                    context.packageName
        }

    } catch (_: Exception) {
        false
    }
}
