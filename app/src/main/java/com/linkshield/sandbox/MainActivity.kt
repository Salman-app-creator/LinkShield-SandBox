package com.linkshield.sandbox

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.checkIsDefaultBrowser
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import com.linkshield.sandbox.update.UpdateChecker
import com.linkshield.sandbox.update.UpdateDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val interceptedUrlFlow    = MutableStateFlow<String?>(null)
    /*
     * ── FIX: URL Trigger Counter ──
     *
     * MutableStateFlow same value dobara emit nahi karta. Isliye jab WhatsApp
     * se same URL dobara tap hota hai (app already open), LaunchedEffect
     * fire nahi hota. Yeh counter har naye intent par badhta hai — isse
     * LaunchedEffect hamesha fire hoga, chahe URL same ho ya different.
     */
    private val interceptedUrlTrigger = MutableStateFlow(0L)
    private val sharedUrlFlow         = MutableStateFlow<String?>(null)
    private val resumeTickFlow        = MutableStateFlow(0)

    private lateinit var browserRoleLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initial URL — cold start
        incomingBrowserUrl(intent)?.let { url ->
            interceptedUrlFlow.value = url
            interceptedUrlTrigger.value = System.currentTimeMillis()
        }
        handleShareIntent(intent)

        browserRoleLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            resumeTickFlow.value++
        }

        val disclaimerManager = DisclaimerManager(this)
        val themeManager      = ThemeManager(this)

        setContent {
            val interceptedUrl by interceptedUrlFlow.collectAsState()
            val urlTrigger     by interceptedUrlTrigger.collectAsState()
            val sharedUrl      by sharedUrlFlow.collectAsState()
            val resumeTick     by resumeTickFlow.collectAsState()
            val context        = LocalContext.current

            var isDarkTheme   by remember { mutableStateOf(themeManager.isDarkTheme()) }
            var hasAccepted   by remember { mutableStateOf(disclaimerManager.hasAccepted()) }
            var hasBrowserSet by remember { mutableStateOf(disclaimerManager.hasBrowserSet()) }

            LaunchedEffect(resumeTick) {
                if (hasAccepted && !hasBrowserSet) {
                    if (checkIsDefaultBrowser(context)) {
                        disclaimerManager.markBrowserSet()
                        hasBrowserSet = true
                    }
                }
            }

            var showUpdateDialog by remember { mutableStateOf(false) }
            var updateInfo by remember {
                mutableStateOf<com.linkshield.sandbox.update.UpdateInfo?>(null)
            }
            LaunchedEffect(Unit) {
                runCatching {
                    UpdateChecker(context).checkForUpdate().getOrNull()?.let {
                        if (it.updateAvailable) { updateInfo = it; showUpdateDialog = true }
                    }
                }
            }

            LinkShieldTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showUpdateDialog && updateInfo != null) {
                        UpdateDialog(
                            updateInfo = updateInfo!!,
                            onDismiss  = { showUpdateDialog = false }
                        )
                    }

                    when {
                        !hasAccepted -> DisclaimerScreen(
                            onAccept = {
                                disclaimerManager.accept()
                                hasAccepted = true
                            }
                        )

                      !hasBrowserSet -> EnableShieldScreen(
                            onBrowserSet = {
                                disclaimerManager.markBrowserSet()
                                hasBrowserSet = true
                            },
                            onRequestBrowserRole = {
                                launchBrowserRolePicker()
                            },
                            onSkip = {
                                disclaimerManager.markBrowserSet()
                                hasBrowserSet = true
                            }
                        )

                        else -> UnblockShieldScreen(
                            initialUrl    = interceptedUrl ?: "",
                            urlTrigger    = urlTrigger,
                            sharedGrabUrl = sharedUrl,
                            onSharedUrlConsumed = { sharedUrlFlow.value = null },
                            isDarkTheme   = isDarkTheme,
                            onThemeToggle = { newDark ->
                                themeManager.setTheme(
                                    if (newDark) ThemeManager.THEME_DARK
                                    else         ThemeManager.THEME_LIGHT
                                )
                                isDarkTheme = newDark
                            }
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                resumeTickFlow.value++
            }
        }
    }

    /**
     * Intent se URL extract karein. Multiple sources check karta hai:
     *   1. intent.dataString  (ACTION_VIEW — WhatsApp, Telegram, Gmail)
     *   2. intent "url" extra (LinkInterceptorActivity legacy)
     *   3. intent EXTRA_TEXT  (ACTION_SEND — Share sheet)
     */
    private fun incomingBrowserUrl(intent: Intent?): String? {
        intent ?: return null

        // 1. Direct data URI — most common for link taps
        intent.dataString?.takeIf { it.isNotBlank() }?.let { return it }

        // 2. "url" extra — legacy from LinkInterceptorActivity
        intent.getStringExtra("url")?.takeIf { it.isNotBlank() }?.let { return it }

        // 3. EXTRA_TEXT — ACTION_SEND from WhatsApp/Telegram share
        if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                val urlRegex = Regex("https?://[^\\s]+")
                urlRegex.find(text)?.value?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        return null
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND &&
            intent.type?.startsWith("text") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val urlRegex = Regex("https?://[^\\s]+")
            val url = urlRegex.find(sharedText)?.value ?: sharedText.trim()
            if (url.isNotBlank()) {
                sharedUrlFlow.value = url
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Naya URL aaye toh flow + trigger dono update karein
        incomingBrowserUrl(intent)?.let { url ->
            interceptedUrlFlow.value = url
            interceptedUrlTrigger.value = System.currentTimeMillis()
        }
        handleShareIntent(intent)
    }

    private fun launchBrowserRolePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager?.isRoleAvailable(RoleManager.ROLE_BROWSER) == true &&
                    roleManager.isRoleHeld(RoleManager.ROLE_BROWSER).not()
                ) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    browserRoleLauncher.launch(intent)
                    return
                }
            }
        }
        runCatching {
            browserRoleLauncher.launch(
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            )
        }.onFailure {
            runCatching {
                browserRoleLauncher.launch(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
}
