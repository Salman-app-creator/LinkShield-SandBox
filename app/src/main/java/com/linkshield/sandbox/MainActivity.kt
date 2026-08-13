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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.ui.theme.LinkShieldTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Trial period initialization (30 Days)
        initTrialPeriod(this)

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

    private fun initTrialPeriod(context: Context) {
        val prefs = context.getSharedPreferences("linkshield_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("first_install_time")) {
            prefs.edit().putLong("first_install_time", System.currentTimeMillis()).apply()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Security & Trial Check Helpers
// ─────────────────────────────────────────────────────────────────────────────
fun isDefaultBrowser(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
    } else {
        false
    }
}

fun isTrialExpired(context: Context): Boolean {
    val prefs = context.getSharedPreferences("linkshield_prefs", Context.MODE_PRIVATE)
    val installTime = prefs.getLong("first_install_time", System.currentTimeMillis())
    val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
    val isProActivated = prefs.getBoolean("is_pro_activated", false)
    
    // Pro users bypass trial expiry check
    if (isProActivated) return false
    
    return (System.currentTimeMillis() - installTime) > thirtyDaysInMillis
}

// ─────────────────────────────────────────────────────────────────────────────
// Root Container Handling Default Check, Active URL & Tab Navigation
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MainAppContent(
    onRequestDefaultBrowser: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var isDefault by remember { mutableStateOf(isDefaultBrowser(context)) }
    var expired by remember { mutableStateOf(isTrialExpired(context)) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    
    // Active Shared State for Auto-Fill Link
    var activeBrowserUrl by rememberSaveable { mutableStateOf("") }
    
    val dnsManager = remember { DnsManager(context) }
    val unblockViewModel: UnblockShieldViewModel = viewModel()
    val mediaList by unblockViewModel.mediaUrls.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefault = isDefaultBrowser(context)
                expired = isTrialExpired(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!isDefault) {
        MandatoryDefaultBrowserScreen(onSetDefaultClick = onRequestDefaultBrowser)
    } else if (expired) {
        TrialExpiredScreen()
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Shield") },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "Shield") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Grabber") },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "Grabber") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> UnblockShieldScreen(
                        dnsManager = dnsManager,
                        viewModel = unblockViewModel,
                        isVisible = selectedTab == 0,
                        onUrlCaptured = { capturedUrl ->
                            activeBrowserUrl = capturedUrl
                        }
                    )
                    1 -> MediaGrabberScreen(
                        dnsManager = dnsManager,
                        activeUrl = activeBrowserUrl,
                        capturedMedia = mediaList,
                        onClearCaptured = { unblockViewModel.clearMedia() }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mandatory Blocking & Expiry Overlay Screens
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MandatoryDefaultBrowserScreen(onSetDefaultClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
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
                text = "To protect your browsing and enable sandbox isolation, LinkShield must be set as your default browser.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onSetDefaultClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Set as Default Browser", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun TrialExpiredScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Free Trial Expired",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your 30-day free trial has ended. Upgrade to Pro version to continue using LinkShield Sandbox features.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { /* Handle Upgrade Action */ },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Upgrade to Pro", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
