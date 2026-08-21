package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.license.ProUpgradeDialog
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    // FIX: MutableStateFlow use kiya taake class level pe safe rahe
    private val interceptedUrlFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        interceptedUrlFlow.value = intent?.getStringExtra("url")

        val disclaimerManager = DisclaimerManager(this)
        val licenseManager    = LicenseManager(this)
        val themeManager      = ThemeManager(this)

        setContent {
            // FIX: StateFlow ko Compose state mein convert kiya
            val interceptedUrl by interceptedUrlFlow.collectAsState()

            val context = LocalContext.current
            var isDefaultBrowser by remember { mutableStateOf(context.isDefaultBrowser()) }
            var isDarkTheme      by remember { mutableStateOf(themeManager.isDarkTheme()) }

            val dnsManager      = remember { DnsManager(context) }
            var isShieldActive  by remember { mutableStateOf(dnsManager.isDohEnabled()) }

            val roleRequestLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                isDefaultBrowser = context.isDefaultBrowser()
            }

            LaunchedEffect(Unit) {
                while (true) {
                    isDefaultBrowser = context.isDefaultBrowser()
                    delay(2000)
                }
            }

            LinkShieldTheme(darkTheme = isDarkTheme) {
                if (!isDefaultBrowser) {
                    DefaultBrowserLockScreen(
                        onEnable = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val roleManager =
                                    context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                                if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                                    !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
                                ) {
                                    roleRequestLauncher.launch(
                                        roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                                    )
                                }
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                )
                            }
                        }
                    )
                    return@LinkShieldTheme
                }

                val navController   = rememberNavController()
                var showDisclaimer  by remember { mutableStateOf(!disclaimerManager.hasAccepted()) }
                var showProDialog   by remember { mutableStateOf(false) }

                if (showDisclaimer) {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false
                        )
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Welcome to LinkShield Sandbox!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "LinkShield Sandbox respects privacy and copyright laws. Please ensure you have the necessary permissions or rights from the content creator before downloading any media. This tool is intended for personal and backup use only.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        disclaimerManager.accept()
                                        showDisclaimer = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Accept & Continue")
                                }
                            }
                        }
                    }
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
                                navController   = navController,
                                onProClick      = { showProDialog = true },
                                licenseManager  = licenseManager,
                                isShieldActive  = isShieldActive
                            )
                        }
                    }
                ) { innerPadding ->
                    if (!showDisclaimer) {
                        MainNavHost(
                            navController   = navController,
                            modifier        = Modifier.padding(innerPadding),
                            interceptedUrl  = interceptedUrl,
                            isDarkTheme     = isDarkTheme,
                            onThemeToggle   = { newDarkTheme ->
                                val next = if (newDarkTheme)
                                    ThemeManager.THEME_DARK
                                else
                                    ThemeManager.THEME_LIGHT
                                themeManager.setTheme(next)
                                isDarkTheme = newDarkTheme
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra("url")?.let { url ->
            interceptedUrlFlow.value = url
        }
    }
}
@Composable
fun BottomNavBar(
    navController:  NavHostController,
    onProClick:     () -> Unit,
    licenseManager: LicenseManager,
    isShieldActive: Boolean
) {
    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination  = navBackStackEntry?.destination
    val isPro               = licenseManager.isProUser()

    val shieldActiveColor = Color(0xFF00F0FF)

    NavigationBar(
        modifier         = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        containerColor   = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation   = 0.dp
    ) {
        val shieldSelected =
            currentDestination?.hierarchy?.any { it.route == Screen.Unblock.route } == true
        val shieldTint = when {
            isShieldActive  -> shieldActiveColor
            shieldSelected  -> MaterialTheme.colorScheme.onSurface
            else            -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        NavigationBarItem(
            selected      = shieldSelected,
            onClick       = {
                navController.navigate(Screen.Unblock.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon          = {
                Icon(
                    imageVector     = Icons.Default.Shield,
                    contentDescription = "Unblock Shield",
                    tint            = shieldTint,
                    modifier        = Modifier.size(22.dp)
                )
            },
            label         = {
                Text(
                    text      = "Shield",
                    color     = shieldTint,
                    fontSize  = 10.sp,
                    fontWeight = if (isShieldActive) FontWeight.Bold else FontWeight.Normal
                )
            },
            alwaysShowLabel = true,
            colors          = NavigationBarItemDefaults.colors(
                indicatorColor = if (isShieldActive)
                    shieldActiveColor.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        )

        val grabberSelected =
            currentDestination?.hierarchy?.any { it.route == Screen.Grabber.route } == true
        val grabberTint = if (grabberSelected)
            MaterialTheme.colorScheme.onSurface
        else
            MaterialTheme.colorScheme.onSurfaceVariant

        NavigationBarItem(
            selected      = grabberSelected,
            onClick       = {
                navController.navigate(Screen.Grabber.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon          = {
                Icon(
                    imageVector        = Icons.Default.Download,
                    contentDescription = "Media Grabber",
                    tint               = grabberTint,
                    modifier           = Modifier.size(22.dp)
                )
            },
            label         = {
                Text(
                    text     = "Grabber",
                    color    = grabberTint,
                    fontSize = 10.sp
                )
            },
            alwaysShowLabel = true
        )

        val proTint = if (isPro)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant

        NavigationBarItem(
            selected        = false,
            onClick         = onProClick,
            icon            = {
                Icon(
                    imageVector        = Icons.Default.Star,
                    contentDescription = if (isPro) "PRO Active" else "Upgrade Pro",
                    tint               = proTint,
                    modifier           = Modifier.size(22.dp)
                )
            },
            label           = {
                Text(
                    text       = if (isPro) "PRO" else "Upgrade",
                    color      = proTint,
                    fontSize   = 10.sp,
                    fontWeight = if (isPro) FontWeight.Bold else FontWeight.Normal
                )
            },
            alwaysShowLabel = true,
            colors          = NavigationBarItemDefaults.colors(
                indicatorColor = if (isPro)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun MainNavHost(
    navController:   NavHostController,
    modifier:        Modifier = Modifier,
    interceptedUrl:  String? = null,
    isDarkTheme:     Boolean = true,
    onThemeToggle:   (Boolean) -> Unit = {}
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Unblock.route,
        modifier         = modifier
    ) {
        composable(Screen.Unblock.route) {
            key(interceptedUrl ?: "") {
                UnblockShieldScreen(
                    initialUrl    = interceptedUrl ?: "",
                    isDarkTheme   = isDarkTheme,
                    onThemeToggle = onThemeToggle
                )
            }
        }
        composable(Screen.Grabber.route) {
            MediaGrabberScreen()
        }
    }
}

sealed class Screen(val route: String, val title: String) {
    object Unblock : Screen("unblock", "Shield")
    object Grabber : Screen("grabber", "Grabber")
}

@Composable
fun DefaultBrowserLockScreen(onEnable: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter          = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = null,
                modifier         = Modifier.size(100.dp),
                contentScale     = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Enable Protection",
                style    = MaterialTheme.typography.headlineMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "LinkShield needs to be your default browser to intercept and protect links from WhatsApp, Email, Telegram, and other apps.",
                style    = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Warning,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Not set as default browser. Links cannot be intercepted until enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick  = onEnable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set as Default Browser", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

fun Context.openDefaultBrowserSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
            startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
        }
    } else {
        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }
}

fun Context.isDefaultBrowser(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    } else {
        val intent      = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        resolveInfo?.activityInfo?.packageName == packageName
    }
}
