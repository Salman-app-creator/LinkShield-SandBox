package com.linkshield.sandbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class LinkInterceptorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.dataString
        if (url != null) {
            showScanBottomSheet(url)
        } else {
            finish()
        }
    }

    private fun showScanBottomSheet(url: String) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_scanner, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        val urlText = view.findViewById<TextView>(R.id.urlText)
        val scanStatusText = view.findViewById<TextView>(R.id.scanStatusText)
        val scanProgressBar = view.findViewById<ProgressBar>(R.id.scanProgressBar)
        val threatScoreText = view.findViewById<TextView>(R.id.threatScoreText)
        val warningsContainer = view.findViewById<LinearLayout>(R.id.warningsContainer)
        val actionButtonsContainer = view.findViewById<LinearLayout>(R.id.actionButtonsContainer)
        val openInSandboxButton = view.findViewById<MaterialButton>(R.id.openInSandboxButton)
        val blockButton = view.findViewById<MaterialButton>(R.id.blockButton)
        val closeButton = view.findViewById<View>(R.id.closeButton)

        urlText.text = url

        closeButton.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        view.postDelayed({
            val result = SecurityChecker.analyzeUrl(url)

            scanProgressBar.visibility = View.GONE
            scanStatusText.text = if (result.isDangerous) "Warning: High Risk Link!" else "Scan Complete"
            threatScoreText.text = "Safety Score: ${result.score}/100"

            warningsContainer.removeAllViews()
            val warningsList: List<String> = result.warnings
            for (warning in warningsList) {
                val warningView = layoutInflater.inflate(R.layout.item_warning, warningsContainer, false) as TextView
                warningView.text = "• $warning"
                warningsContainer.addView(warningView)
            }

            actionButtonsContainer.visibility = View.VISIBLE

            openInSandboxButton.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, SandboxWebViewActivity::class.java).apply {
                    putExtra("TARGET_URL", url)
                }
                startActivity(intent)
                finish()
            }

            blockButton.setOnClickListener {
                dialog.dismiss()
                finish()
            }

        }, 1200)

        dialog.show()
    }
}
