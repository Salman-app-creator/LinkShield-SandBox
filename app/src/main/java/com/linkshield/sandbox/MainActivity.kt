package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel
import com.linkshield.sandbox.ui.theme.LinkShieldSandboxTheme

class MainActivity : ComponentActivity() {

    private val viewModel: UnblockShieldViewModel by viewModels()
    private lateinit var dnsManager: DnsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        dnsManager = DnsManager(applicationContext)

        setContent {
            LinkShieldSandboxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(viewModel = viewModel, dnsManager = dnsManager)
                }
            }
        }
    }
}

@Composable
fun AppContent(
    viewModel: UnblockShieldViewModel,
    dnsManager: DnsManager
) {
    UnblockShieldScreen(
        dnsManager = dnsManager,
        viewModel = viewModel,
        isVisible = true,
        onUrlCaptured = { capturedUrl ->
            viewModel.updateUrl(capturedUrl)
        }
    )
}
