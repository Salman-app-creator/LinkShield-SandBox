package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linkshield.sandbox.ui.theme.LinkShieldTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LinkShieldTheme {
                MainAppContent(
                    onRequestDefaultBrowser = { requestDefaultBrowser(this) }
                )
            }
        }
    }

    private fun requestDefaultBrowser(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    startActivityForResult(intent, 1001)
                }
            }
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility Function to Check Default Browser Status
// ─────────────────────────────────────────────────────────────────────────────
fun isDefaultBrowser(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
    } else {
        false
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root Container Handling Mandatory Default Check & Tab State
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MainAppContent(
    onRequestDefaultBrowser: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isDefault by remember { mutableStateOf(isDefaultBrowser(context)) }

    // Active URL shared state for Grabber Tab Auto-Fill
    var currentActiveUrl by remember { mutableStateOf("") }

    // Re-check default status when user resumes app after system dialog
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefault = isDefaultBrowser(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!isDefault) {
        // 🔒 Mandatory Blocking Overlay
        MandatoryDefaultBrowserScreen(
            onSetDefaultClick = onRequestDefaultBrowser
        )
    } else {
        // ✅ Unlocked App Navigation
        // Pass currentActiveUrl & onUrlChange callback to your Navigation/Tabs setup:
        // AppNavigation(
        //     currentActiveUrl = currentActiveUrl,
        //     onUrlChange = { newUrl -> currentActiveUrl = newUrl }
        // )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mandatory Blocking Screen Component
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MandatoryDefaultBrowserScreen(onSetDefaultClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Default Browser Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "To protect your browsing and enable full sandbox isolation, LinkShield must be set as your default browser.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onSetDefaultClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Set as Default Browser",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
