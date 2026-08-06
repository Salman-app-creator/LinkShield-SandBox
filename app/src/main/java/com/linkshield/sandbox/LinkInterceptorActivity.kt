package com.linkshield.sandbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LinkInterceptorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intentData: Uri? = intent?.data
        if (intentData != null) {
            val sandboxIntent = Intent(this, SandboxWebViewActivity::class.java).apply {
                putExtra("TARGET_URL", intentData.toString())
            }
            startActivity(sandboxIntent)
        }
        finish()
    }
}
