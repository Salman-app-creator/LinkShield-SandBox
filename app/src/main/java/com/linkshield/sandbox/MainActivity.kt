package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.unit.dp
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.MediaGrabberScreen
import com.linkshield.sandbox.ui.Upgrade.UpgradeScreen
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen

class MainActivity : ComponentActivity() {
    private lateinit var dnsManager: DnsManager

    private val roleManagerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Proceed after default browser prompt
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
            } catch (_: Exception) {}
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

    var isDisclaimerAccepted by remember {
        mutableStateOf(sharedPrefs.getBoolean("disclaimer_accepted", false))
    }
    var isDefaultBrowserHandled by remember {
        mutableStateOf(sharedPrefs.getBoolean("default_browser_handled", false))
    }

    when {
        !isDisclaimerAccepted -> {
            DisclaimerScreen(
                onAccept = {
                    sharedPrefs.edit().putBoolean("disclaimer_accepted", true).apply()
                    isDisclaimerAccepted = true
                }
            )
        }
        !isDefaultBrowserHandled -> {
            DefaultBrowserPromptScreen(
                onSetDefault = {
                    onRequestDefaultBrowser()
                    sharedPrefs.edit().putBoolean("default_browser_handled", true).apply()
                    isDefaultBrowserHandled = true
                },
                onSkip = {
                    sharedPrefs.edit().putBoolean("default_browser_handled", true).apply()
                    isDefaultBrowserHandled = true
                }
            )
        }
        else -> {
            MainAppContainer(dnsManager = dnsManager)
        }
    }
}

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Shield",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Privacy & Sandbox Disclaimer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "LinkShield Sandbox utilizes Encrypted DNS over HTTPS (DoH) to bypass restrictive network censorship and protect online privacy. By proceeding, you agree to use this tool responsibly for personal testing and secure browsing.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I Accept Terms")
            }
        }
    }
}

@Composable
fun DefaultBrowserPromptScreen(
    onSetDefault: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Default Browser",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Set as Default Browser",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Set LinkShield Sandbox as your default browser to open all incoming links with automatic encrypted DNS protection and media detection.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSetDefault,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set as Default Browser")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for Now")
            }
        }
    }
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
                    initialUrl = detectedMediaUrl
                )
                2 -> UpgradeScreen()
            }
        }
    }
}
