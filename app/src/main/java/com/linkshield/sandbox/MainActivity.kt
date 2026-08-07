package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val ROLE_REQUEST_CODE = 1001
        private const val DEFAULT_APPS_REQUEST_CODE = 1002
    }

    private lateinit var btnEnable: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnEnable = findViewById(R.id.btnEnableProtection)
        tvStatus = findViewById(R.id.tvStatus)

        btnEnable.setOnClickListener {
            enableProtection()
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        if (isDefaultBrowser()) {
            tvStatus.text = "Protection ACTIVE - All links are sandboxed"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnEnable.text = "Protection Enabled"
            btnEnable.isEnabled = false
        } else {
            tvStatus.text = "Protection INACTIVE - Links open in Chrome"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnEnable.text = "Enable Link Protection"
            btnEnable.isEnabled = true
        }
    }

    private fun isDefaultBrowser(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == packageName
        }
    }

    private fun enableProtection() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                requestBrowserRoleQPlus()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                openDefaultAppsSettings()
            }
            else -> {
                openLegacyAppSettings()
            }
        }
    }

    /**
     * Android 10+ (API 29+): Direct system bottom-sheet trigger hoti hai
     * "Set LinkShield as your default browser?" type ka prompt aata hai
     */
    private fun requestBrowserRoleQPlus() {
        try {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    startActivityForResult(intent, ROLE_REQUEST_CODE)
                } else {
                    Toast.makeText(this, "Protection is ALREADY Active!", Toast.LENGTH_SHORT).show()
                }
            } else {
                openDefaultAppsSettings()
            }
        } catch (e: Exception) {
            openDefaultAppsSettings()
        }
    }

    /**
     * Android 7-9: Default apps settings screen open karta hai
     */
    private fun openDefaultAppsSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivityForResult(intent, DEFAULT_APPS_REQUEST_CODE)
        } catch (e: Exception) {
            openLegacyAppSettings()
        }
    }

    /**
     * Pre-Android 7: App details settings
     */
    private fun openLegacyAppSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ROLE_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "LinkShield Sandbox Active!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Please allow 'Set as default browser' to enable protection",
                        Toast.LENGTH_LONG
                    ).show()
                }
                updateUI()
            }
            DEFAULT_APPS_REQUEST_CODE -> {
                updateUI()
            }
        }
    }
}
