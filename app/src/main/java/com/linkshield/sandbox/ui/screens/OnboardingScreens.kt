package com.linkshield.sandbox.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linkshield.sandbox.R

@Composable
fun DisclaimerScreen(
    onAccept: () -> Unit
) {
    val scroll = rememberScrollState()

    /*
     * The disclaimer must be accepted.
     *
     * Back is disabled so the user cannot leave the onboarding
     * flow through the Android back button.
     */
    BackHandler(enabled = true) {}

    val canContinue =
        scroll.maxValue == 0 ||
        scroll.value >= scroll.maxValue - 24

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {

        /*
         * Scrollable disclaimer content.
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(
                    horizontal = 20.dp,
                    vertical = 12.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Image(
                painter = painterResource(
                    R.drawable.ic_app_logo
                ),
                contentDescription = "LinkShield Sandbox",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )

            Text(
                text = "LinkShield Sandbox",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Privacy & Security Disclaimer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            DisclaimerBlock(
                title = "1. Privacy",
                body = "The application is designed not to collect or transmit personal browsing information. Users remain responsible for the data and websites they access."
            )

            DisclaimerBlock(
                title = "2. Security",
                body = "Security features can reduce risk but cannot guarantee that every website, link, download, or network connection is safe."
            )

            DisclaimerBlock(
                title = "3. Default Browser",
                body = "LinkShield must be selected as the Android default browser so links opened from other applications can enter the sandbox browser flow."
            )

            DisclaimerBlock(
                title = "4. AdGuard",
                body = "Blocks web banner ads, pop-ups, and hidden tracking scripts for a cleaner and safer browsing experience."
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )
        }

        /*
         * Bottom action area.
         *
         * navigationBarsPadding() is applied here so the button
         * stays ABOVE Android's navigation buttons / gesture area.
         */
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {

            Button(
                onClick = onAccept,
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 10.dp
                    )
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Accept & Continue",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DisclaimerBlock(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun EnableShieldScreen(
    onRequestBrowserRole: () -> Unit
) {
    /*
     * Back is disabled here as well.
     *
     * The user must complete the default-browser step.
     */
    BackHandler(enabled = true) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                horizontal = 22.dp,
                vertical = 18.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Image(
            painter = painterResource(
                R.drawable.ic_app_logo
            ),
            contentDescription = "LinkShield Sandbox",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(118.dp)
                .clip(CircleShape)
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Enable Shield Protection",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Text(
            text = "Set LinkShield as your Android default browser to continue.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Feature("Sandbox browser")
                Feature("Media Grabber")
                Feature("Secure Network entry point")
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * This is the ONLY action button on this screen.
         *
         * It opens Android's Default Browser role selection.
         *
         * MainActivity verifies the result after the user returns.
         */
        Button(
            onClick = onRequestBrowserRole,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {

            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text(
                text = "Enable Shield Protection",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Feature(
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Text(text)
    }
}

fun openDefaultBrowserSettings(
    context: Context
) {
    /*
     * Android 10+:
     * Use the official browser RoleManager flow.
     */
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

        runCatching {

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

                context.startActivity(intent)

                return
            }
        }
    }

    /*
     * Fallback for devices where RoleManager is unavailable.
     */
    runCatching {

        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
            )
        )

    }.onFailure {

        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS)
            )
        }
    }
}

fun checkIsDefaultBrowser(
    context: Context
): Boolean {

    return runCatching {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            /*
             * This is the authoritative Android role check.
             */
            context
                .getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_BROWSER) == true

        } else {

            /*
             * Pre-Android 10 fallback.
             */
            val intent = Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse("http://")
            )

            val resolveInfo =
                context.packageManager.resolveActivity(
                    intent,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                )

            resolveInfo
                ?.activityInfo
                ?.packageName == context.packageName
        }

    }.getOrDefault(false)
}
