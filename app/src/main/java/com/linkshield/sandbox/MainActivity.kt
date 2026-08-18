// ========================= PART 1 =========================

package com.linkshield.sandbox

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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

    val context = androidx.compose.ui.platform.LocalContext.current

    val disclaimerManager = remember(context) {
        DisclaimerManager(context)
    }

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

    val lifecycleOwner = LocalLifecycleOwner.current

    /*
     * Android settings se wapas aane ke baad browser role
     * dobara check karna zaroori hai.
     *
     * Is se Enable Shield screen automatically Main Screen
     * par move ho jayegi jab LinkShield default browser set
     * ho chuka ho.
     */
    androidx.compose.runtime.DisposableEffect(
        lifecycleOwner,
        currentScreen
    ) {

        val observer = LifecycleEventObserver { _, event ->

            if (
                event == Lifecycle.Event.ON_RESUME &&
                currentScreen == AppStartScreen.ENABLE_SHIELD
            ) {

                if (checkIsDefaultBrowser(context)) {
                    currentScreen = AppStartScreen.MAIN
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    /*
     * Enable Shield screen ko current Android role status
     * ke saath synchronized rakhne ke liye lightweight polling.
     *
     * Agar user settings se wapas aaye ya default browser
     * kisi aur tareeqe se set ho jaye to screen automatically
     * change ho jayegi.
     */
    LaunchedEffect(currentScreen) {

        if (currentScreen == AppStartScreen.ENABLE_SHIELD) {

            while (true) {

                if (checkIsDefaultBrowser(context)) {

                    currentScreen = AppStartScreen.MAIN
                    break
                }

                delay(1000)
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

                            disclaimerManager.accept()

                            /*
                             * Disclaimer accept hone ke baad
                             * hamesha Enable Shield screen show
                             * karni hai.
                             */
                            currentScreen =
                                AppStartScreen.ENABLE_SHIELD
                        }
                    )
                }


                AppStartScreen.ENABLE_SHIELD -> {

                    EnableShieldScreen(

                        onBrowserSet = {

                            /*
                             * Existing onboarding screen already
                             * browser status verify karti hai.
                             */
                            if (checkIsDefaultBrowser(context)) {

                                disclaimerManager.markBrowserSet()

                                currentScreen =
                                    AppStartScreen.MAIN
                            }
                        },

                        onRequestBrowserRole = {

                            /*
                             * Existing helper Android RoleManager /
                             * default-app settings open karta hai.
                             */
                            openDefaultBrowserSettings(context)
                        }
                    )
                }


                AppStartScreen.MAIN -> {

                    val unblockViewModel:
                        UnblockShieldViewModel = viewModel()

                    /*
                     * Main screen ko outer Column mein rakha gaya hai
                     * taa ke top aur bottom borders/dividers clearly
                     * visible rahen.
                     */
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        HorizontalDivider(
                            color =
                                MaterialTheme.colorScheme.outline
                                    .copy(alpha = 0.35f)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {

                            UnblockShieldScreen(
                                viewModel = unblockViewModel
                            )
                        }

                        HorizontalDivider(
                            color =
                                MaterialTheme.colorScheme.outline
                                    .copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}


// ========================= PART 2 =========================


/*
 * IMPORTANT:
 *
 * Yeh file existing onboarding implementation ko replace nahi
 * karti. DisclaimerScreen aur EnableShieldScreen already
 * OnboardingScreens.kt mein defined hain.
 *
 * MainActivity sirf un dono ke beech correct navigation/state
 * control karti hai.
 *
 * Flow:
 *
 * Fresh install:
 *
 *     Disclaimer
 *         ↓
 *     Accept & Continue
 *         ↓
 *     Enable Shield
 *         ↓
 *     Android Default Browser Settings
 *         ↓
 *     LinkShield selected
 *         ↓
 *     Main Screen
 *
 *
 * Already accepted + browser not enabled:
 *
 *     Enable Shield
 *         ↓
 *     Main Screen
 *
 *
 * Already accepted + browser already enabled:
 *
 *     Main Screen
 *
 *
 * Iska matlab agar app ko close/open kiya jaye to disclaimer
 * dobara nahi aayega, kyun ke DisclaimerManager acceptance
 * SharedPreferences mein save karta hai.
 *
 * Main screen par UnblockShieldScreen ka existing UI intact
 * rahega. Sirf uske outer edge par top aur bottom divider
 * restore kiye gaye hain.
 */


// END OF FILE
