package com.linkshield.sandbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinkInterceptorActivity : AppCompatActivity() {

    private var bottomSheetDialog: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.dataString
        if (url.isNullOrEmpty()) {
            finish()
            return
        }

        showScannerBottomSheet(url)
    }

    private fun showScannerBottomSheet(url: String) {
        bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_scanner, null)

        val closeButton = view.findViewById<ImageView>(R.id.closeButton)
        val urlText = view.findViewById<TextView>(R.id.urlText)
        val scanProgressBar = view.findViewById<ProgressBar>(R.id.scanProgressBar)
        val scanStatusText = view.findViewById<TextView>(R.id.scanStatusText)
        val threatScoreText = view.findViewById<TextView>(R.id.threatScoreText)
        val warningsContainer = view.findViewById<LinearLayout>(R.id.warningsContainer)
        val actionButtonsContainer = view.findViewById<LinearLayout>(R.id.actionButtonsContainer)
        val openInAppButton = view.findViewById<MaterialButton>(R.id.openInAppButton)
        val openInSandboxButton = view.findViewById<MaterialButton>(R.id.openInSandboxButton)
        val blockButton = view.findViewById<MaterialButton>(R.id.blockButton)

        urlText.text = url

        closeButton.setOnClickListener {
            bottomSheetDialog?.dismiss()
            finish()
        }

        bottomSheetDialog?.setOnDismissListener {
            finish()
        }

        bottomSheetDialog?.setContentView(view)
        bottomSheetDialog?.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = SecurityChecker.analyzeUrl(url)

            withContext(Dispatchers.Main) {
                scanProgressBar.visibility = View.GONE
                actionButtonsContainer.visibility = View.VISIBLE

                scanStatusText.text = when {
                    result.threatScore >= 70 -> "High Risk Link Detected!"
                    result.threatScore >= 30 -> "Suspicious Link Detected"
                    else -> "Link Scanned - Safe"
                }

                threatScoreText.text = "Threat Score: ${result.threatScore}/100"
                threatScoreText.setTextColor(
                    if (result.threatScore >= 50) ContextCompat.getColor(this@LinkInterceptorActivity, R.color.linkshield_error)
                    else ContextCompat.getColor(this@LinkInterceptorActivity, android.R.color.holo_green_dark)
                )

                warningsContainer.removeAllViews()
                result.warnings.forEach { warning ->
                    val warningView = LayoutInflater.from(this@LinkInterceptorActivity)
                        .inflate(R.layout.item_warning, warningsContainer, false)
                    val warningText = warningView.findViewById<TextView>(R.id.warningText)
                    warningText.text = "• $warning"
                    warningsContainer.addView(warningView)
                }

                if (result.isOfficialAppAvailable && result.officialPackageName != null) {
                    openInAppButton.visibility = View.VISIBLE
                    openInAppButton.text = getString(R.string.open_in_official_app)
                    openInAppButton.setOnClickListener {
                        launchOfficialApp(result.finalUrl, result.officialPackageName)
                        bottomSheetDialog?.dismiss()
                    }
                } else {
                    openInAppButton.visibility = View.GONE
                }

                openInSandboxButton.setOnClickListener {
                    launchSandbox(result.finalUrl)
                    bottomSheetDialog?.dismiss()
                }

                blockButton.setOnClickListener {
                    bottomSheetDialog?.dismiss()
                    finish()
                }
            }
        }
    }

    private fun launchOfficialApp(url: String, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            launchSandbox(url)
        }
    }

    private fun launchSandbox(url: String) {
        val intent = Intent(this, SandboxWebViewActivity::class.java).apply {
            putExtra(SandboxWebViewActivity.EXTRA_URL, url)
        }
        startActivity(intent)
    }
}
