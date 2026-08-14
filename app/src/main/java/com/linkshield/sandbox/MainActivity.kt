package com.linkshield.sandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.linkshield.sandbox.dns.DnsManager
import com.linkshield.sandbox.ui.UnblockShieldScreen
import com.linkshield.sandbox.ui.UnblockShieldViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: UnblockShieldViewModel by viewModels()
    private lateinit var dnsManager: DnsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        dnsManager = DnsManager(applicationContext)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
            }
        }
    }
}
