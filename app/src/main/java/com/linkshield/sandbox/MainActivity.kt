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

    private val interceptedUrlFlow = MutableStateFlow<String?>(null)
    private val sharedUrlFlow      = MutableStateFlow<String?>(null)
    private val resumeTickFlow     = MutableStateFlow(0)

    private lateinit var browserRoleLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        interceptedUrlFlow.value = intent?.getStringExtra("url")
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
                            }
                        )

                        else -> UnblockShieldScreen(
                            initialUrl    = interceptedUrl ?: "",
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

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND &&
            intent.type?.startsWith("text") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            // URL extract karo shared text se
            val urlRegex = Regex("https?://[^\\s]+")
            val url = urlRegex.find(sharedText)?.value ?: sharedText.trim()
            if (url.isNotBlank()) {
                sharedUrlFlow.value = url
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra("url")?.let { interceptedUrlFlow.value = it }
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
