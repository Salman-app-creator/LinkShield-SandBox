package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.checkIsDefaultBrowser
import com.linkshield.sandbox.ui.theme.DarkBackground
import com.linkshield.sandbox.ui.theme.LightBackground
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.MainScreen

private enum class SetupStage { DISCLAIMER, ENABLE_SHIELD, MAIN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {
            MainAppContent(
                initialUrl = intent?.dataString ?: intent?.getStringExtra("url") ?: ""
            )
        }
    }
}

@Composable
private fun MainAppContent(initialUrl: String) {
    val context = LocalContext.current
    val disclaimerManager = remember { DisclaimerManager(context.applicationContext) }
    val themeManager = remember { ThemeManager(context.applicationContext) }
    var isDarkTheme by rememberSaveable { mutableStateOf(themeManager.isDarkTheme()) }
    var stageName by rememberSaveable {
        mutableStateOf(
            when {
                !disclaimerManager.hasAccepted() -> SetupStage.DISCLAIMER.name
                !checkIsDefaultBrowser(context) -> SetupStage.ENABLE_SHIELD.name
                else -> SetupStage.MAIN.name
            }
        )
    }
    val stage = SetupStage.valueOf(stageName)

    val browserRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkIsDefaultBrowser(context)) {
            disclaimerManager.markBrowserSet()
            stageName = SetupStage.MAIN.name
        }
    }

    fun requestBrowserRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_BROWSER) == true) {
                browserRoleLauncher.launch(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                )
                return
            }
        }
        openBrowserSettingsFallback(context)
    }

    LinkShieldTheme(darkTheme = isDarkTheme) {
        SideEffect {
            val activity = context as? android.app.Activity
            activity?.window?.statusBarColor =
                (if (isDarkTheme) DarkBackground else LightBackground).toArgb()
            activity?.window?.navigationBarColor =
                (if (isDarkTheme) DarkBackground else LightBackground).toArgb()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (stage) {
                SetupStage.DISCLAIMER -> DisclaimerScreen {
                    disclaimerManager.accept()
                    stageName = if (checkIsDefaultBrowser(context)) {
                        SetupStage.MAIN.name
                    } else {
                        SetupStage.ENABLE_SHIELD.name
                    }
                }

                SetupStage.ENABLE_SHIELD -> EnableShieldScreen(
                    onBrowserSet = {
                        if (checkIsDefaultBrowser(context)) {
                            disclaimerManager.markBrowserSet()
                            stageName = SetupStage.MAIN.name
                        }
                    },
                    onRequestBrowserRole = ::requestBrowserRole
                )

                SetupStage.MAIN -> MainScreen(
                    initialUrl = initialUrl,
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { dark ->
                        isDarkTheme = dark
                        themeManager.setTheme(
                            if (dark) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                        )
                    }
                )
            }
        }
    }
}

private fun openBrowserSettingsFallback(context: Context) {
    runCatching {
        context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }.onFailure {
        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
    }
}
