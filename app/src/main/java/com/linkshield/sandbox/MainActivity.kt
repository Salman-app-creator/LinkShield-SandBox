package com.linkshield.sandbox

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.lifecycle.viewmodel.compose.viewModel

import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.ui.screens.DisclaimerScreen
import com.linkshield.sandbox.ui.screens.EnableShieldScreen
import com.linkshield.sandbox.ui.screens.checkIsDefaultBrowser
import com.linkshield.sandbox.ui.screens.openDefaultBrowserSettings
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.unblock.UnblockShieldScreen
import com.linkshield.sandbox.ui.unblock.UnblockShieldViewModel

import kotlinx.coroutines.delay


private enum class AppStartScreen {
    DISCLAIMER,
    ENABLE_SHIELD,
    MAIN
}


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainAppContent()
        }
    }
}


@Composable
fun MainAppContent() {

    val context =
        androidx.compose.ui.platform.LocalContext.current

    val disclaimerManager = remember(context) {
        DisclaimerManager(context)
    }

    /*
     * Startup flow:
     *
     * 1. Disclaimer not accepted
     *       -> DISCLAIMER
     *
     * 2. Disclaimer accepted but LinkShield
     *    is not default browser
     *       -> ENABLE_SHIELD
     *
     * 3. Both conditions satisfied
     *       -> MAIN
     */
    val initialScreen = remember(context) {

        when {

            !disclaimerManager.hasAccepted() ->
                AppStartScreen.DISCLAIMER

            !checkIsDefaultBrowser(context) ->
                AppStartScreen.ENABLE_SHIELD

            else ->
                AppStartScreen.MAIN
        }
    }

    var currentScreen by remember {
        mutableStateOf(initialScreen)
    }


    /*
     * Android settings se wapas aane ke baad
     * default-browser status check hota rahe.
     *
     * LocalLifecycleOwner ki zarurat nahi hai.
     * Isse lifecycle-compose dependency/import ka
     * compile issue bhi eliminate ho jata hai.
     */
    LaunchedEffect(currentScreen) {

        if (currentScreen == AppStartScreen.ENABLE_SHIELD) {

            while (true) {

                if (checkIsDefaultBrowser(context)) {

                    disclaimerManager.markBrowserSet()

                    currentScreen =
                        AppStartScreen.MAIN

                    break
                }

                delay(750)
            }
        }
    }


    LinkShieldTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            when (currentScreen) {

                AppStartScreen.DISCLAIMER -> {

                    DisclaimerScreen(

                        onAccept = {

                            /*
                             * Acceptance permanently save hoti hai.
                             */
                            disclaimerManager.accept()

                            /*
                             * Disclaimer ke baad direct
                             * Enable Shield screen.
                             */
                            currentScreen =
                                AppStartScreen.ENABLE_SHIELD
                        }
                    )
                }


                AppStartScreen.ENABLE_SHIELD -> {

                    EnableShieldScreen(

                        onBrowserSet = {

                            if (
                                checkIsDefaultBrowser(
                                    context
                                )
                            ) {

                                disclaimerManager
                                    .markBrowserSet()

                                currentScreen =
                                    AppStartScreen.MAIN
                            }
                        },

                        onRequestBrowserRole = {

                            openDefaultBrowserSettings(
                                context
                            )
                        }
                    )
                }


                AppStartScreen.MAIN -> {

                    val unblockViewModel:
                        UnblockShieldViewModel = viewModel()

                    /*
                     * Main UI full screen mein rahega.
                     *
                     * Top aur bottom borders ko overlay kiya
                     * gaya hai, isliye Modifier.weight() ki
                     * zarurat nahi.
                     */
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        UnblockShieldScreen(
                            viewModel = unblockViewModel
                        )

                        /*
                         * TOP BORDER
                         */
                        HorizontalDivider(
                            modifier = Modifier
                                .align(Alignment.TopCenter),
                            color = MaterialTheme
                                .colorScheme
                                .outline
                                .copy(alpha = 0.35f)
                        )

                        /*
                         * BOTTOM BORDER
                         */
                        HorizontalDivider(
                            modifier = Modifier
                                .align(Alignment.BottomCenter),
                            color = MaterialTheme
                                .colorScheme
                                .outline
                                .copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}
/*
 * MainActivity.kt
 *
 * Startup navigation:
 *
 * Fresh installation:
 *
 *     DISCLAIMER
 *          ↓
 *     Accept & Continue
 *          ↓
 *     ENABLE_SHIELD
 *          ↓
 *     Android Default Browser Settings
 *          ↓
 *     LinkShield selected
 *          ↓
 *     MAIN
 *
 *
 * Returning user:
 *
 *     Disclaimer accepted
 *          ↓
 *     Browser enabled?
 *       ↙       ↘
 *     YES        NO
 *      ↓          ↓
 *     MAIN    ENABLE_SHIELD
 *
 *
 * Important:
 *
 * - DisclaimerManager handles the saved acceptance state.
 * - checkIsDefaultBrowser() handles browser-role detection.
 * - EnableShieldScreen remains responsible for its own UI.
 * - UnblockShieldScreen remains responsible for the main UI.
 * - This file only controls startup navigation.
 *
 *
 * UI border fix:
 *
 * The previous implementation used:
 *
 *     Column
 *       ├── HorizontalDivider
 *       ├── Box(weight = 1f)
 *       ├── HorizontalDivider
 *
 * The direct `weight` import caused:
 *
 *     Cannot access 'weight':
 *     it is internal in
 *     androidx.compose.foundation.layout
 *
 * Therefore the main screen now uses:
 *
 *     Box(fillMaxSize())
 *
 * with top/bottom HorizontalDivider overlays.
 *
 * This preserves the visual borders without depending
 * on the problematic weight import.
 *
 *
 * Lifecycle fix:
 *
 * LocalLifecycleOwner was removed because the build reported:
 *
 *     Unresolved reference: LocalLifecycleOwner
 *
 * The default-browser state is instead checked by a
 * lightweight coroutine while ENABLE_SHIELD is visible.
 *
 *
 * Do not add:
 *
 *     import androidx.compose.foundation.layout.weight
 *
 * or:
 *
 *     import androidx.lifecycle.compose.LocalLifecycleOwner
 *
 * to this file.
 */
