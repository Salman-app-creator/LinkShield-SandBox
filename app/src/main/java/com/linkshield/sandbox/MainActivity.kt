package com.linkshield.sandbox

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val ROLE_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEnable: Button = findViewById(R.id.btnEnableProtection)
        btnEnable.setOnClickListener {
            enableProtection()
        }
    }

    private fun enableProtection() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                        startActivityForResult(intent, ROLE_REQUEST_CODE)
                    } else {
                        Toast.makeText(this, "Protection is ALREADY Active!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    openAppSettings()
                }
            } else {
                openAppSettings()
            }
        } catch (e: Exception) {
            // Fallback to Settings if RoleManager fails
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ROLE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "LinkShield Sandbox Active!", Toast.LENGTH_SHORT).show()
            } else {
                openAppSettings()
            }
        }
    }
}
