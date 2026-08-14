package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.MediaGrabberScreen
import com.linkshield.sandbox.ui.Upgrade.UpgradeScreen
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen

class MainActivity : ComponentActivity() {
    private lateinit var dnsManager: DnsManager

    // Role Manager Launcher for Default Browser prompt
    private val roleManagerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Continue to main screen regardless of user choice
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dnsManager = DnsManager(applicationContext)

        setContent {
            MaterialTheme {
                MainAppFlow(
                    dnsManager = dnsManager,
                    onRequestDefaultBrowser = { requestDefaultBrowser() }
                )
            }
        }
    }

    private fun requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
            ) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                roleManagerLauncher.launch(intent)
            }
        } else {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback if settings page not available
            }
        }
    }
}

@Composable
fun MainAppFlow(
    dnsManager: DnsManager,
    onRequestDefaultBrowser: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("linkshield_prefs", Context.MODE_PRIVATE) }
    
    // Check if disclaimer was accepted previously
    var isDisclaimerAccepted by remember {
        mutableStateOf(sharedPrefs.getBoolean("disclaimer_accepted", false))
    }

    if (!isDisclaimerAccepted) {
        // --- Step 1: Mandatory Disclaimer Screen ---
        DisclaimerDialog(
            onAccept = {
                sharedPrefs.edit().putBoolean("disclaimer_accepted", true).apply()
                isDisclaimerAccepted = true
                onRequestDefaultBrowser() // Trigger Default Browser Request right after accept
            }
        )
    } else {
        // --- Step 2: Main Application Interface ---
        MainAppContainer(dnsManager = dnsManager)
    }
}

@Composable
fun DisclaimerDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismiss without accept */ },
        title = {
            Text(
                text = "Privacy & Usage Terms",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "LinkShield Sandbox uses Custom Secure DNS (DoH) to protect your privacy and unblock restricted networks. " +
                        "By proceeding, you agree that this application is strictly for personal privacy testing and sandbox browsing.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I Accept & Set Default Browser")
            }
        }
    )
}

@Composable
fun MainAppContainer(dnsManager: DnsManager) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var detectedMediaUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Shield") },
                    label = { Text("Shield") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Grabber") },
                    label = { Text("Grabber") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Upgrade") },
                    label = { Text("Upgrade") }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> UnblockShieldScreen(
                    dnsManager = dnsManager,
                    onMediaFound = { url: String ->
                        detectedMediaUrl = url
                    }
                )
                1 -> MediaGrabberScreen(
                    detectedMediaUrl = detectedMediaUrl
                )
                2 -> UpgradeScreen()
            }
        }
    }
}
