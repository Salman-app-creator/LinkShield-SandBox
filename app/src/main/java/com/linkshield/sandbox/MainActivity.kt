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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.disclaimer.FirstLaunchDisclaimerDialog
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.ui.license.ProUpgradeDialog
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val disclaimerManager = DisclaimerManager(this)
        val licenseManager = LicenseManager(this)

        // Capture intercepted URL from intent (if launched via LinkInterceptorActivity)
        val interceptedUrl = intent?.getStringExtra("intercepted_url")

        setContent {
            LinkShieldTheme {
                val context = LocalContext.current
                val navController = rememberNavController()

                var showDisclaimer by remember {
                    mutableStateOf(!disclaimerManager.hasAccepted())
                }
                var showProDialog by remember { mutableStateOf(false) }

                if (showDisclaimer) {
                    FirstLaunchDisclaimerDialog(
                        onAccept = {
                            disclaimerManager.accept()
                            showDisclaimer = false
                        }
                    )
                }

                if (showProDialog) {
                    ProUpgradeDialog(
                        licenseManager = licenseManager,
                        onDismiss = { showProDialog = false },
                        onUnlocked = { showProDialog = false }
                    )
                }

                Scaffold(
                    bottomBar = {
                        if (!showDisclaimer) {
                            BottomNavBar(
                                navController = navController,
                                onProClick = { showProDialog = true },
                                licenseManager = licenseManager
                            )
                        }
                    }
                ) { padding ->
                    if (!showDisclaimer) {
                        MainNavHost(
                            navController = navController,
                            modifier = Modifier.padding(padding),
                            licenseManager = licenseManager,
                            onProRequired = { showProDialog = true },
                            interceptedUrl = interceptedUrl
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    navController: NavHostController,
    onProClick: () -> Unit,
    licenseManager: LicenseManager
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        Screen.Unblock to Icons.Default.Shield,
        Screen.Grabber to Icons.Default.Download
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 0.dp
    ) {
        items.forEach { (screen, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        val isPro = licenseManager.isProUser()
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Pro",
                    tint = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = {
                Text(
                    if (isPro) "PRO" else "UPGRADE",
                    color = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            selected = false,
            onClick = onProClick
        )
    }
}

@Composable
fun MainNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    licenseManager: LicenseManager,
    onProRequired: () -> Unit,
    interceptedUrl: String? = null
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Unblock.route,
        modifier = modifier
    ) {
        composable(Screen.Unblock.route) {
            UnblockShieldScreen(initialUrl = interceptedUrl)
        }
        composable(Screen.Grabber.route) {
            MediaGrabberScreen(
                licenseManager = licenseManager,
                onProRequired = onProRequired
            )
        }
    }
}

sealed class Screen(val route: String, val title: String) {
    object Unblock : Screen("unblock", "Shield")
    object Grabber : Screen("grabber", "Grabber")
}

fun Context.openDefaultBrowserSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            startActivity(intent)
        }
    } else {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        startActivity(intent)
    }
}

fun Context.isDefaultBrowser(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    } else {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        resolveInfo?.activityInfo?.packageName == packageName
    }
}
