package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.disclaimer.DisclaimerManager
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.license.LicenseManager
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel
import com.linkshield.sandbox.ui.theme.LinkShieldTheme
import com.linkshield.sandbox.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {

    private val viewModel: UnblockShieldViewModel by viewModels()
    private lateinit var dnsManager: DnsManager
    private lateinit var licenseManager: LicenseManager
    private lateinit var disclaimerManager: DisclaimerManager
    private lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dnsManager = DnsManager(applicationContext)
        licenseManager = LicenseManager(applicationContext)
        disclaimerManager = DisclaimerManager(applicationContext)
        themeManager = ThemeManager(applicationContext)

        val interceptedUrl = intent.getStringExtra("url")
        if (!interceptedUrl.isNullOrBlank()) {
            viewModel.updateUrl(interceptedUrl)
            viewModel.loadUrl(interceptedUrl)
        }

        setContent {
            var isDarkTheme by remember { mutableStateOf(themeManager.isDarkTheme()) }

            LinkShieldTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UnblockShieldScreen(
                        dnsManager = dnsManager,
                        viewModel = viewModel,
                        licenseManager = licenseManager,
                        disclaimerManager = disclaimerManager,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = {
                            isDarkTheme = !isDarkTheme
                            themeManager.setTheme(
                                if (isDarkTheme) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                            )
                        }
                    )
                }
            }
        }
    }
}
