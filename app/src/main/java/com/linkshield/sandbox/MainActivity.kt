package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import com.linkshield.sandbox.ui.Upgrade.UpgradeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dnsManager = DnsManager(this)
        val licenseManager = LicenseManager(this)

        setContent {
            val systemTheme = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemTheme) }

            LinkShieldTheme(darkTheme = isDarkMode) {
                var selectedTab by remember { mutableIntStateOf(0) }
                var detectedMediaUrl by remember { mutableStateOf("") }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Shield, contentDescription = "Shield") },
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
                                },
                                isDarkMode = isDarkMode,
                                onToggleTheme = { isDarkMode = !isDarkMode }
                            )
                            1 -> MediaGrabberScreen(
                                detectedMediaUrl = detectedMediaUrl,
                                licenseManager = licenseManager,
                                onDownloadTriggered = { }
                            )
                            2 -> UpgradeScreen()
                        }
                    }
                }
            }
        }
    }
}
