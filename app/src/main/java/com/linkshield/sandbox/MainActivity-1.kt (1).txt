package com.linkshield.sandbox

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.grabber.MediaGrabberScreen
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Constants shared with DnsManager / MediaGrabberScreen via SharedPreferences.
// All three files read from the SAME "shield_prefs" file — single source of truth.
// ─────────────────────────────────────────────────────────────────────────────
private const val TAG               = "MainActivity"
private const val PREFS_NAME        = "shield_prefs"
private const val KEY_IS_PRO        = "is_pro"
private const val KEY_DOWNLOAD_COUNT = "download_count"
private const val KEY_THEME_DARK    = "theme_dark"
private const val KEY_DISCLAIMER    = "disclaimer_accepted"
private const val KEY_INITIALIZED   = "initialized"
private const val FREE_LIMIT        = 20

// Payment details — edit only here, reflects everywhere in upgrade UI
private const val WHATSAPP_NUMBER   = "923136176616"
private const val EASYPAISA_NUMBER  = "03136176616"
private const val EASYPAISA_TITLE   = "Your Name"
private const val JAZZCASH_NUMBER   = "03061934345"
private const val JAZZCASH_TITLE    = "Your Name"
private const val USDT_ADDRESS      = "TQhUtaU9sg2hKfEM5FdeB3VGpzotKtwVub"
private const val PRICE_PKR         = "Rs. 350"
private const val PRICE_USD         = "\$1.25"
private const val LICENSE_PREFIX    = "LSHD"

// ─────────────────────────────────────────────────────────────────────────────
// Navigation route sealed class
// ─────────────────────────────────────────────────────────────────────────────
sealed class Screen(val route: String, val label: String) {
    object Shield  : Screen("shield",  "Shield")
    object Grabber : Screen("grabber", "Grabber")
    object Upgrade : Screen("upgrade", "Pro")
}

// ─────────────────────────────────────────────────────────────────────────────
// MainActivity
// ─────────────────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Intercept URLs opened from external apps (WhatsApp, Telegram, etc.)
        val interceptedUrl: String? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent?.data?.toString()
            else               -> intent?.getStringExtra("url")
        }
        Log.d(TAG, "Intercepted URL: $interceptedUrl")

        setContent {
            val context = LocalContext.current
            val prefs   = remember {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }

            // Ensure fresh install starts at 0/20 — guard against stale prefs
            LaunchedEffect(Unit) {
                if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
                    prefs.edit()
                        .putInt(KEY_DOWNLOAD_COUNT, 0)
                        .putBoolean(KEY_INITIALIZED, true)
                        .apply()
                    Log.d(TAG, "Fresh install — download counter reset to 0")
                }
            }

            // ── Theme state ───────────────────────────────────────────────────
            var isDarkTheme by rememberSaveable {
                mutableStateOf(prefs.getBoolean(KEY_THEME_DARK, true))
            }

            // ── DnsManager — single instance for the entire app ───────────────
            val dnsManager = remember { DnsManager(context) }

            // ── Shared state: URL from WebView → Grabber ──────────────────────
            var sharedWebUrl      by remember { mutableStateOf<String?>(null) }
            var extractedVideoUrl by remember { mutableStateOf<String?>(null) }

            // ── Shield active state — bubbles up from UnblockShieldScreen ──────
            var isShieldActive by remember { mutableStateOf(dnsManager.isDohEnabled()) }

            // ── Default browser check ─────────────────────────────────────────
            var isDefaultBrowser by remember { mutableStateOf(context.isDefaultBrowser()) }

            val roleRequestLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { isDefaultBrowser = context.isDefaultBrowser() }

            // Poll every 2s while user might be in system settings
            LaunchedEffect(Unit) {
                while (true) {
                    isDefaultBrowser = context.isDefaultBrowser()
                    delay(2000)
                }
            }

            // ── WRITE_EXTERNAL_STORAGE permission (API < 29) ──────────────────
            val storagePermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (!granted) {
                    Toast.makeText(
                        context,
                        "Storage permission needed for downloads",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val status = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    if (status != PackageManager.PERMISSION_GRANTED) {
                        storagePermissionLauncher.launch(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    }
                }
            }

            LinkShieldTheme(darkTheme = isDarkTheme) {

                // ── Disclaimer gate ───────────────────────────────────────────
                var disclaimerAccepted by rememberSaveable {
                    mutableStateOf(prefs.getBoolean(KEY_DISCLAIMER, false))
                }

                if (!disclaimerAccepted) {
                    DisclaimerDialog(
                        onAccept = {
                            prefs.edit().putBoolean(KEY_DISCLAIMER, true).apply()
                            disclaimerAccepted = true
                        }
                    )
                    return@LinkShieldTheme
                }

                // ── Default browser gate ──────────────────────────────────────
                if (!isDefaultBrowser) {
                    DefaultBrowserLockScreen(
                        onEnable = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val rm = context.getSystemService(
                                    Context.ROLE_SERVICE
                                ) as RoleManager
                                if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                                    !rm.isRoleHeld(RoleManager.ROLE_BROWSER)
                                ) {
                                    roleRequestLauncher.launch(
                                        rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
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

                // ── Main app navigation ───────────────────────────────────────
                val navController   = rememberNavController()
                var showProDialog   by remember { mutableStateOf(false) }

                // Pro upgrade dialog — shown from any tab
                if (showProDialog) {
                    ProUpgradeDialog(
                        prefs      = prefs,
                        onDismiss  = { showProDialog = false },
                        onUnlocked = {
                            showProDialog  = false
                            isShieldActive = dnsManager.isDohEnabled()
                        }
                    )
                }

                Scaffold(
                    bottomBar = {
                        LinkShieldBottomBar(
                            navController  = navController,
                            isShieldActive = isShieldActive,
                            prefs          = prefs,
                            onUpgradeClick = { showProDialog = true }
                        )
                    }
                ) { innerPadding ->
                    MainNavHost(
                        navController        = navController,
                        modifier             = Modifier.padding(innerPadding),
                        dnsManager           = dnsManager,
                        interceptedUrl       = interceptedUrl,
                        sharedWebUrl         = sharedWebUrl,
                        extractedVideoUrl    = extractedVideoUrl,
                        isDarkTheme          = isDarkTheme,
                        onToggleTheme        = {
                            isDarkTheme = !isDarkTheme
                            prefs.edit()
                                .putBoolean(KEY_THEME_DARK, isDarkTheme)
                                .apply()
                        },
                        onUrlChanged         = { url -> sharedWebUrl = url },
                        onVideoExtracted     = { url -> extractedVideoUrl = url },
                        onShieldStateChanged = { active -> isShieldActive = active },
                        onProRequired        = { showProDialog = true }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MainNavHost
//
// WEBVIEW STATE PERSISTENCE:
//   The NavHost uses standard Compose navigation. Each composable destination
//   is kept in the back stack with saveState = true / restoreState = true.
//   The WebView instance itself lives inside WebViewState which is held in
//   remember{} inside UnblockShieldScreen — NOT recreated on tab switch.
//   Switching to Grabber and back NEVER destroys the WebView object.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MainNavHost(
    navController:        NavHostController,
    modifier:             Modifier = Modifier,
    dnsManager:           DnsManager,
    interceptedUrl:       String?,
    sharedWebUrl:         String?,
    extractedVideoUrl:    String?,
    isDarkTheme:          Boolean,
    onToggleTheme:        () -> Unit,
    onUrlChanged:         (String) -> Unit,
    onVideoExtracted:     (String) -> Unit,
    onShieldStateChanged: (Boolean) -> Unit,
    onProRequired:        () -> Unit
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Shield.route,
        modifier         = modifier
    ) {
        composable(Screen.Shield.route) {
            UnblockShieldScreen(
                initialUrl           = interceptedUrl,
                dnsManager           = dnsManager,
                onUrlChanged         = onUrlChanged,
                onVideoExtracted     = onVideoExtracted,
                isDarkTheme          = isDarkTheme,
                onToggleTheme        = onToggleTheme,
                onShieldStateChanged = onShieldStateChanged
            )
        }

        composable(Screen.Grabber.route) {
            MediaGrabberScreen(
                dnsManager        = dnsManager,
                onProRequired     = onProRequired,
                sharedUrl         = sharedWebUrl,
                extractedVideoUrl = extractedVideoUrl
            )
        }

        // Upgrade tab — navigating here shows the paywall within the tab body
        composable(Screen.Upgrade.route) {
            UpgradeTabScreen(onProRequired = onProRequired)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UpgradeTabScreen — shown when user taps the "Pro" tab directly
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun UpgradeTabScreen(onProRequired: () -> Unit) {
    LaunchedEffect(Unit) { onProRequired() }

    Box(
        modifier          = Modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Star,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(56.dp)
            )
            Text(
                "LinkShield Pro",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
                textAlign  = TextAlign.Center
            )
            Text(
                "Unlimited downloads, full DNS Shield bypass, and all future features.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick  = onProRequired,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Star, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activate Pro License", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LinkShieldBottomBar — 3 tabs with navigationBarsPadding
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LinkShieldBottomBar(
    navController:  NavHostController,
    isShieldActive: Boolean,
    prefs:          SharedPreferences,
    onUpgradeClick: () -> Unit
) {
    val navBackStack       by navController.currentBackStackEntryAsState()
    val currentDestination  = navBackStack?.destination
    val isPro               = prefs.getBoolean(KEY_IS_PRO, false)

    val shieldActiveColor   = Color(0xFF00F0FF)
    val shieldInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Animated shield tab tint
    val shieldTint by animateColorAsState(
        targetValue   = if (isShieldActive) shieldActiveColor else shieldInactiveColor,
        animationSpec = tween(300),
        label         = "shieldTabTint"
    )

    NavigationBar(
        modifier       = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),           // labels never clipped by gesture bar
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 0.dp
    ) {
        // ── Tab 1: Shield ─────────────────────────────────────────────────────
        val shieldSelected =
            currentDestination?.hierarchy?.any { it.route == Screen.Shield.route } == true

        NavigationBarItem(
            selected    = shieldSelected,
            onClick     = {
                navController.navigate(Screen.Shield.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon        = {
                Icon(
                    imageVector        = Icons.Default.Shield,
                    contentDescription = "Shield",
                    tint               = shieldTint,
                    modifier           = Modifier.size(22.dp)
                )
            },
            label       = {
                Text(
                    text       = "Shield",
                    color      = shieldTint,
                    fontSize   = 10.sp,
                    fontWeight = if (isShieldActive) FontWeight.Bold else FontWeight.Normal
                )
            },
            alwaysShowLabel = true,
            colors      = NavigationBarItemDefaults.colors(
                indicatorColor = if (isShieldActive)
                    shieldActiveColor.copy(alpha = 0.14f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // ── Tab 2: Grabber ────────────────────────────────────────────────────
        val grabberSelected =
            currentDestination?.hierarchy?.any { it.route == Screen.Grabber.route } == true
        val grabberTint = if (grabberSelected)
            MaterialTheme.colorScheme.onSurface
        else
            MaterialTheme.colorScheme.onSurfaceVariant

        NavigationBarItem(
            selected    = grabberSelected,
            onClick     = {
                navController.navigate(Screen.Grabber.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon        = {
                Icon(
                    imageVector        = Icons.Default.Download,
                    contentDescription = "Media Grabber",
                    tint               = grabberTint,
                    modifier           = Modifier.size(22.dp)
                )
            },
            label       = {
                Text(
                    text     = "Grabber",
                    color    = grabberTint,
                    fontSize = 10.sp
                )
            },
            alwaysShowLabel = true
        )

        // ── Tab 3: Pro Upgrade ────────────────────────────────────────────────
        val proTint = if (isPro)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant

        NavigationBarItem(
            selected    = false,
            onClick     = onUpgradeClick,
            icon        = {
                Icon(
                    imageVector        = Icons.Default.Star,
                    contentDescription = if (isPro) "PRO Active" else "Upgrade Pro",
                    tint               = proTint,
                    modifier           = Modifier.size(22.dp)
                )
            },
            label       = {
                Text(
                    text       = if (isPro) "PRO ✓" else "Upgrade",
                    color      = proTint,
                    fontSize   = 10.sp,
                    fontWeight = if (isPro) FontWeight.Bold else FontWeight.Normal
                )
            },
            alwaysShowLabel = true,
            colors      = NavigationBarItemDefaults.colors(
                indicatorColor = if (isPro)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProUpgradeDialog — full-screen dialog with:
//   • Payment details (Easypaisa, JazzCash, USDT) with 1-tap copy
//   • WhatsApp contact button
//   • License key input + validation
//   • Single-device binding via ANDROID_ID
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProUpgradeDialog(
    prefs:     SharedPreferences,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context         = LocalContext.current
    val clipboard       = LocalClipboardManager.current
    var keyInput        by rememberSaveable { mutableStateOf("") }
    var isValidating    by remember { mutableStateOf(false) }
    var keyError        by remember { mutableStateOf<String?>(null) }
    var activatedSuccessfully by remember { mutableStateOf(false) }
    val scope           = rememberCoroutineScope()

    // Device ID for single-device binding
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress    = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape    = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .wrapContentHeight(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (activatedSuccessfully) {
                    // ── Success state ─────────────────────────────────────────
                    Icon(
                        imageVector        = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(60.dp)
                    )
                    Text(
                        "Pro Activated! 🎉",
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        textAlign  = TextAlign.Center
                    )
                    Text(
                        "Unlimited downloads and full Shield access are now active.",
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick  = onUnlocked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Text("Continue to App", style = MaterialTheme.typography.labelLarge)
                    }

                } else {
                    // ── Upgrade state ─────────────────────────────────────────
                    Icon(
                        imageVector        = Icons.Default.Star,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(48.dp)
                    )
                    Text(
                        "Upgrade to LinkShield Pro",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center
                    )
                    Text(
                        "One-time payment. Unlimited downloads, full Shield DoH bypass, and all future features.",
                        style     = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    // ── Payment card ──────────────────────────────────────────
                    Card(
                        shape  = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Price pill
                            Surface(
                                shape  = RoundedCornerShape(10.dp),
                                color  = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text       = "Price: $PRICE_PKR  /  $PRICE_USD (one-time)",
                                    style      = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.primary,
                                    textAlign  = TextAlign.Center,
                                    modifier   = Modifier.padding(
                                        vertical = 8.dp, horizontal = 12.dp
                                    )
                                )
                            }

                            // Easypaisa
                            PaymentRow(
                                label    = "Easypaisa",
                                value    = EASYPAISA_NUMBER,
                                subtitle = "Account: $EASYPAISA_TITLE",
                                onCopy   = {
                                    clipboard.setText(AnnotatedString(EASYPAISA_NUMBER))
                                    Toast.makeText(context, "Easypaisa number copied!", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(thickness = 0.5.dp)

                            // JazzCash
                            PaymentRow(
                                label    = "JazzCash",
                                value    = JAZZCASH_NUMBER,
                                subtitle = "Account: $JAZZCASH_TITLE",
                                onCopy   = {
                                    clipboard.setText(AnnotatedString(JAZZCASH_NUMBER))
                                    Toast.makeText(context, "JazzCash number copied!", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(thickness = 0.5.dp)

                            // USDT TRC20
                            Column {
                                Text(
                                    "Crypto / USDT (TRC20)",
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier          = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text     = USDT_ADDRESS,
                                        style    = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        color    = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 10.sp
                                    )
                                    IconButton(
                                        onClick  = {
                                            clipboard.setText(AnnotatedString(USDT_ADDRESS))
                                            Toast.makeText(context, "USDT address copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy USDT address",
                                            tint     = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    "⚠ Send USDT on TRC20 network only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    // WhatsApp button
                    Button(
                        onClick  = {
                            val msg = "Hi, I want to buy LinkShield Pro. I have made the payment."
                            val url = "https://wa.me/$WHATSAPP_NUMBER?text=${
                                Uri.encode(msg)
                            }"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366)
                        )
                    ) {
                        Text(
                            "💬  Chat on WhatsApp",
                            style      = MaterialTheme.typography.labelLarge,
                            color      = Color.White
                        )
                    }

                    HorizontalDivider()

                    // License key input
                    Text(
                        "Enter your Pro License Key after payment:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value         = keyInput,
                        onValueChange = {
                            keyInput  = it.uppercase().take(20)
                            keyError  = null
                        },
                        modifier      = Modifier.fillMaxWidth(),
                        label         = { Text("Pro License Key") },
                        placeholder   = { Text("LSHD-XXXX-XXXX-CCCC") },
                        singleLine    = true,
                        isError       = keyError != null,
                        supportingText = {
                            keyError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType   = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        shape  = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    // Activate button
                    Button(
                        onClick  = {
                            when {
                                keyInput.isBlank() -> keyError = "Please enter your license key"
                                else -> {
                                    isValidating = true
                                    keyError     = null
                                    scope.launch {
                                        val valid = validateLicenseKey(
                                            key      = keyInput.trim(),
                                            deviceId = deviceId,
                                            prefs    = prefs
                                        )
                                        isValidating = false
                                        if (valid) {
                                            activatedSuccessfully = true
                                        } else {
                                            keyError = "Invalid key or already used on another device."
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        enabled  = !isValidating
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Validating…")
                        } else {
                            Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Activate Pro", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    TextButton(
                        onClick  = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Maybe Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PaymentRow — reusable row with copy icon
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PaymentRow(
    label:    String,
    value:    String,
    subtitle: String,
    onCopy:   () -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
            Text(
                text  = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick  = onCopy,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// validateLicenseKey
//
// Anti-piracy single-device binding:
//   1. Key must start with LICENSE_PREFIX and be 16 chars after stripping hyphens.
//   2. Checksum of first 12 chars must match last 4 chars.
//   3. Key stored with bound ANDROID_ID — second device returns false.
// ─────────────────────────────────────────────────────────────────────────────
private suspend fun validateLicenseKey(
    key:      String,
    deviceId: String,
    prefs:    SharedPreferences
): Boolean {
    // Strip hyphens, force uppercase
    val clean = key.replace("-", "").uppercase().trim()
    if (clean.length != 16)               return false
    if (!clean.startsWith(LICENSE_PREFIX)) return false

    val body             = clean.substring(0, 12)
    val providedChecksum = clean.substring(12, 16)
    val expectedChecksum = computeChecksum(body)
    if (providedChecksum != expectedChecksum) return false

    // Single-device check
    val bindingKey  = "bound_device_$clean"
    val boundDevice = prefs.getString(bindingKey, null)

    return when {
        boundDevice == null -> {
            // First activation — bind to this device and activate Pro
            prefs.edit()
                .putBoolean(KEY_IS_PRO, true)
                .putString(bindingKey, deviceId)
                .apply()
            Log.d(TAG, "Pro activated for device: $deviceId")
            true
        }
        boundDevice == deviceId -> {
            // Same device — re-activating, allow
            prefs.edit().putBoolean(KEY_IS_PRO, true).apply()
            true
        }
        else -> {
            // Different device — reject
            Log.w(TAG, "Key already bound to different device: $boundDevice")
            false
        }
    }
}

private fun computeChecksum(body: String): String {
    var sum = 0
    body.forEachIndexed { index, ch -> sum += ch.code * (index + 1) }
    return (sum % 10000).toString().padStart(4, '0')
}

// ─────────────────────────────────────────────────────────────────────────────
// DisclaimerDialog — shown once on first launch
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DisclaimerDialog(onAccept: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress    = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            shape    = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.Shield,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(48.dp)
                )
                Text(
                    "Welcome to LinkShield Sandbox",
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    textAlign  = TextAlign.Center
                )
                Text(
                    "LinkShield respects privacy and copyright laws. Please ensure you have " +
                    "the necessary rights from content creators before downloading any media. " +
                    "This tool is intended for personal and backup use only.",
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick  = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Text("Accept & Continue", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DefaultBrowserLockScreen — shown when app is not set as default browser
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DefaultBrowserLockScreen(onEnable: () -> Unit) {
    Box(
        modifier         = Modifier
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
            Icon(
                imageVector        = Icons.Default.Shield,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Set LinkShield as Default Browser",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "LinkShield needs to be your default browser to intercept and protect " +
                "links from WhatsApp, Telegram, Email, and other apps.",
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                )
            ) {
                Row(
                    modifier              = Modifier.padding(14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Not set as default browser — links cannot be intercepted.",
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
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Shield, null, Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Enable as Default Browser", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension functions
// ─────────────────────────────────────────────────────────────────────────────

fun Context.isDefaultBrowser(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
        rm.isRoleHeld(RoleManager.ROLE_BROWSER)
    } else {
        val intent      = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        resolveInfo?.activityInfo?.packageName == packageName
    }
}
