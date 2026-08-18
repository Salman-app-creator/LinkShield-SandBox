package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.linkshield.sandbox.adblock.AdBlockEngine
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.checkIsDefaultBrowser
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen

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
                initialUrl =
                    intent?.dataString
                        ?: intent?.getStringExtra("url")
                        ?: ""
            )
        }
    }
}

@Composable
private fun MainAppContent(initialUrl: String) {

    val context = LocalContext.current

    val disclaimerManager = remember {
        DisclaimerManager(context.applicationContext)
    }

    val themeManager = remember {
        ThemeManager(context.applicationContext)
    }

    var isDarkTheme by remember {
        mutableStateOf(themeManager.isDarkTheme())
    }

    var stage by remember {
        mutableStateOf(
            when {
                !disclaimerManager.hasAccepted() ->
                    SetupStage.DISCLAIMER

                !checkIsDefaultBrowser(context) ->
                    SetupStage.ENABLE_SHIELD

                else ->
                    SetupStage.MAIN
            }
        )
    }

    var adBlockReady by remember {
        mutableStateOf(false)
    }

    /*
     * Android's official Browser Role request.
     *
     * This is the important fix:
     * We request ROLE_BROWSER directly instead of opening
     * the generic Default Apps settings page.
     */
    val browserRoleLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (checkIsDefaultBrowser(context)) {
                disclaimerManager.markBrowserSet()
                stage = SetupStage.MAIN
            }
        }

    fun requestBrowserRole() {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

            val roleManager =
                context.getSystemService(RoleManager::class.java)

            if (
                roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)
            ) {

                val intent =
                    roleManager.createRequestRoleIntent(
                        RoleManager.ROLE_BROWSER
                    )

                browserRoleLauncher.launch(intent)

            } else {

                openBrowserSettingsFallback(context)
            }

        } else {

            openBrowserSettingsFallback(context)
        }
    }

    LaunchedEffect(Unit) {

        AdBlockEngine
            .getInstance()
            .initialize(context.applicationContext)

        adBlockReady = true
    }

    LinkShieldTheme(
        darkTheme = isDarkTheme
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            when (stage) {

                SetupStage.DISCLAIMER -> {

                    DisclaimerScreen(
                        onAccept = {

                            disclaimerManager.accept()

                            stage =
                                if (checkIsDefaultBrowser(context)) {
                                    SetupStage.MAIN
                                } else {
                                    SetupStage.ENABLE_SHIELD
                                }
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
                            requestBrowserRole()
                        }
                    )
                }

                SetupStage.MAIN -> {

                    if (adBlockReady) {

                        UnblockShieldScreen(

                            initialUrl = initialUrl,

                            isDarkTheme = isDarkTheme,

                            onThemeToggle = { dark ->

                                isDarkTheme = dark

                                themeManager.setTheme(
                                    if (dark)
                                        ThemeManager.THEME_DARK
                                    else
                                        ThemeManager.THEME_LIGHT
                                )
                            }
                        )

                    } else {

                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun openBrowserSettingsFallback(
    context: android.content.Context
) {

    try {

        context.startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
            )
        )

    } catch (_: Exception) {

        try {

            context.startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS)
            )

        } catch (_: Exception) {
        }
    }
}
