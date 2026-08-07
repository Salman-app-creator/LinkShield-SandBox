package com.linkshield.sandbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LinkInterceptorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = intent?.data
        if (data != null) {
            val scheme = data.scheme
            // Sirf http aur https URLs ko sandbox mein bhejo
            if (scheme == "http" || scheme == "https") {
                val sandboxIntent = Intent(this, SandboxWebViewActivity::class.java).apply {
                    putExtra("TARGET_URL", data.toString())
                    // Ensure proper task behavior
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(sandboxIntent)
            }
        }
        // Activity immediately finish ho jati hai - user ko yeh screen nahi dikhti
        finish()
    }
}
