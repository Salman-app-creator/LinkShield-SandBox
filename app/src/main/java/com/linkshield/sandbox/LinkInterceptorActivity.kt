package com.linkshield.sandbox

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LinkInterceptorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = intent?.dataString

        if (!targetUrl.isNullOrEmpty()) {
            // Direct open isolated sandbox view
            val intent = Intent(this, SandboxWebViewActivity::class.java).apply {
                putExtra("URL_TO_LOAD", targetUrl)
            }
            startActivity(intent)
        }
        
        finish()
    }
}
