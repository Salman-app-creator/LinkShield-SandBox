package com.linkshield.sandbox

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SandboxWebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val TAG = "SandboxWebView"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var urlText: TextView
    private lateinit var securityChip: TextView
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var closeButton: ImageButton

    private var currentUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sandbox_webview)

        currentUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        if (currentUrl.isEmpty()) {
            Toast.makeText(this, "No URL provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initUI()
        setupHardenedWebView()
        loadUrl(currentUrl)
    }

    private fun initUI() {
        webView = findViewById(R.id.sandboxWebView)
        progressBar = findViewById(R.id.progressBar)
        urlText = findViewById(R.id.urlText)
        securityChip = findViewById(R.id.securityChip)
        backButton = findViewById(R.id.backButton)
        forwardButton = findViewById(R.id.forwardButton)
        refreshButton = findViewById(R.id.refreshButton)
        closeButton = findViewById(R.id.closeButton)

        urlText.text = currentUrl

        backButton.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        forwardButton.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        refreshButton.setOnClickListener { webView.reload() }
        closeButton.setOnClickListener { showCloseConfirmation() }

        securityChip.text = getString(R.string.sandbox_active)
        securityChip.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupHardenedWebView() {
        val webSettings = webView.settings

        webSettings.domStorageEnabled = false
        webSettings.databaseEnabled = false
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        webSettings.saveFormData = false
        webSettings.savePassword = false

        CookieManager.getInstance().apply {
            setAcceptCookie(false)
            setAcceptThirdPartyCookies(webView, false)
        }

        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = false
        webSettings.setMediaPlaybackRequiresUserGesture(true)
        webSettings.setGeolocationEnabled(false)
        webSettings.allowFileAccess = false
        webSettings.allowContentAccess = false
        webSettings.allowFileAccessFromFileURLs = false
        webSettings.allowUniversalAccessFromFileURLs = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webSettings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 LinkShield/1.0"
        webSettings.setSupportMultipleWindows(false)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                url?.let {
                    currentUrl = it
                    urlText.text = it
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                updateNavigationButtons()
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                AlertDialog.Builder(this@SandboxWebViewActivity)
                    .setTitle("SSL Certificate Error")
                    .setMessage("This website has an invalid security certificate. Proceed with caution.")
                    .setPositiveButton("Proceed") { _, _ -> handler?.proceed() }
                    .setNegativeButton("Cancel") { _, _ -> handler?.cancel() }
                    .show()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }
            override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }
        }

        webView.setDownloadListener { url, _, _, mimeType, _ ->
            Toast.makeText(this, "Downloads are blocked in Sandbox mode!", Toast.LENGTH_SHORT).show()
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = true
        }
    }

    private fun loadUrl(url: String) {
        webView.clearHistory()
        webView.loadUrl(url)
    }

    private fun updateNavigationButtons() {
        backButton.isEnabled = webView.canGoBack()
        backButton.alpha = if (webView.canGoBack()) 1.0f else 0.3f
        forwardButton.isEnabled = webView.canGoForward()
        forwardButton.alpha = if (webView.canGoForward()) 1.0f else 0.3f
    }

    private fun showCloseConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Close Sandbox?")
            .setMessage("All browsing data (cookies, cache, history) will be permanently deleted.")
            .setPositiveButton("Close & Clear") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAllWebViewData()
    }

    private fun clearAllWebViewData() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }

        webView.apply {
            clearCache(true)
            clearHistory()
            clearFormData()
            clearSslPreferences()
        }

        WebViewDatabase.getInstance(this).apply {
            clearFormData()
            clearHttpAuthUsernamePassword()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else showCloseConfirmation()
    }
}
