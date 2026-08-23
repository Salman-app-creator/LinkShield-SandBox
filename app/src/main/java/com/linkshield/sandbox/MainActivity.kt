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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val interceptedUrlFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        interceptedUrlFlow.value = intent?.getStringExtra("url")

        val disclaimerManager = DisclaimerManager(this)
        val licenseManager    = LicenseManager(this)
        val themeManager      = ThemeManager(this)

        setContent {
            val interceptedUrl by interceptedUrlFlow.collectAsState()
            val context        = LocalContext.current

            var isDefaultBrowser by remember { mutableStateOf(context.isDefaultBrowser()) }
            var isDarkTheme      by remember { mutableStateOf(themeManager.isDarkTheme()) }
            val dnsManager       = remember { DnsManager(context) }

            val roleRequestLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { isDefaultBrowser = context.isDefaultBrowser() }

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
                                val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
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

                var showDisclaimer by remember { mutableStateOf(!disclaimerManager.hasAccepted()) }

                if (showDisclaimer) {
                    DisclaimerDialog(
                        onAccept = {
                            disclaimerManager.accept()
                            showDisclaimer = false
                        }
                    )
                    return@LinkShieldTheme
                }

                UnblockShieldScreen(
                    initialUrl    = interceptedUrl ?: "",
                    isDarkTheme   = isDarkTheme,
                    onThemeToggle = { newDark ->
                        val next = if (newDark) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                        themeManager.setTheme(next)
                        isDarkTheme = newDark
                    }
                )
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
private fun DisclaimerDialog(onAccept: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
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
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick  = onAccept,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Accept & Continue")
                }
            }
        }
    }
}

@Composable
fun DefaultBrowserLockScreen(onEnable: () -> Unit) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter            = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier           = Modifier.size(100.dp),
                contentScale       = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Enable Protection",
                style     = MaterialTheme.typography.headlineMedium,
                color     = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "LinkShield needs to be your default browser to intercept and protect links from WhatsApp, Email, Telegram, and other apps.",
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                shape  = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp),
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
