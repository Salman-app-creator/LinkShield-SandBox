package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.ui.screens.GrabberScreen
import com.linkshield.sandbox.ui.screens.UpgradeScreen
import com.linkshield.sandbox.ui.theme.LinkShieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LinkShieldTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Dynamic State (New install defaults to 30 days)
    var trialDaysLeft by remember { mutableIntStateOf(30) }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // Place your ShieldScreen / Browser View here
                    Text("Shield Screen Placeholder")
                }
                1 -> {
                    GrabberScreen()
                }
                2 -> {
                    UpgradeScreen(
                        trialDaysLeft = trialDaysLeft,
                        isTrialActive = trialDaysLeft > 0
                    )
                }
            }
        }
    }
}
