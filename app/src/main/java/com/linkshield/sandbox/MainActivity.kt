package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.adblock.AdBlockEngine
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.checkIsDefaultBrowser
import com.linkshield.sandbox.ui.screens.openDefaultBrowserSettings
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import kotlinx.coroutines.delay

private enum class SetupStage {
    DISCLAIMER,
    ENABLE_SHIELD,
    MAIN
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppContent(
                initialUrl = intent?.dataString
                    ?: intent?.getStringExtra("url")
                    ?: ""
            )
        }
    }
}

@Composable
private fun MainAppContent(initialUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val disclaimerManager = remember { DisclaimerManager(context) }
    val themeManager = remember { ThemeManager(context.applicationContext) }
    var isDarkTheme by remember { mutableStateOf(themeManager.isDarkTheme()) }
    var adBlockReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AdBlockEngine.getInstance().initialize(context.applicationContext)
        adBlockReady = true
    }

    var stage by remember {
        mutableStateOf(
            when {
                !disclaimerManager.hasAccepted() -> SetupStage.DISCLAIMER
                !disclaimerManager.hasBrowserSet() -> SetupStage.ENABLE_SHIELD
                !checkIsDefaultBrowser(context) -> SetupStage.ENABLE_SHIELD
                else -> SetupStage.MAIN
            }
        )
    }

    // Keep the mandatory default-browser requirement enforced after setup too.
    LaunchedEffect(stage) {
        if (stage == SetupStage.MAIN) {
            while (true) {
                delay(1000)
                if (!checkIsDefaultBrowser(context)) {
                    stage = SetupStage.ENABLE_SHIELD
                    break
                }
            }
        }
    }

    LinkShieldTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (stage) {
                SetupStage.DISCLAIMER -> {
                    DisclaimerScreen(
                        onAccept = {
                            disclaimerManager.accept()
                            stage = SetupStage.ENABLE_SHIELD
                        }
                    )
                }

                SetupStage.ENABLE_SHIELD -> {
                    EnableShieldScreen(
                        onBrowserSet = {
                            if (checkIsDefaultBrowser(context)) {
                                disclaimerManager.markBrowserSet()
                                stage = SetupStage.MAIN
                            }
                        },
                        onRequestBrowserRole = {
                            openDefaultBrowserSettings(context)
                        }
                    )
                }

                SetupStage.MAIN -> {
                    if (!adBlockReady) {
                        androidx.compose.material3.CircularProgressIndicator()
                    } else {
                        UnblockShieldScreen(
                        initialUrl = initialUrl,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { dark ->
                            isDarkTheme = dark
                            themeManager.setTheme(if (dark) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT)
                        }
                        )
                    }
                }
            }
        }
    }
}
