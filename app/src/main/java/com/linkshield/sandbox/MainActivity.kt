package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.MediaGrabberScreen
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UpgradeScreen

class MainActivity : ComponentActivity() {
    private lateinit var dnsManager: DnsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dnsManager = DnsManager(applicationContext)

        setContent {
            MaterialTheme {
                MainAppContainer(dnsManager)
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
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> UnblockShieldScreen(
                    dnsManager = dnsManager,
                    onMediaFound = { url ->
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
