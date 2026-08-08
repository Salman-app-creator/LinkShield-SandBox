package com.linkshield.sandbox

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SandboxWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvUrl: TextView
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var errorView: View
    private lateinit var tvErrorTitle: TextView
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: ImageButton

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sandbox_webview)

        initViews()
        setupWebView()
        setupToolbar()

        val url = intent.getStringExtra("TARGET_URL")
        if (!url.isNullOrEmpty()) {
            loadUrl(url)
        } else {
            showError("No URL provided", "The link you clicked does not contain a valid URL.")
        }
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        tvUrl = findViewById(R.id.tvUrl)
        tvTitle = findViewById(R.id.tvTitle)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnClose = findViewById(R.id.btnClose)
        btnShare = findViewById(R.id.btnShare)
        errorView = findViewById(R.id.errorView)
        tvErrorTitle = findViewById(R.id.tvErrorTitle)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnRetry = findViewById(R.id.btnRetry)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (url.startsWith("http://") || url.startsWith("https://")) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(this@SandboxWebViewActivity, "Cannot open this link", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
                errorView.visibility = View.GONE
                webView.visibility = View.VISIBLE
                updateUrlDisplay(url)
                updateNavButtons()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                updateUrlDisplay(url)
                updateNavButtons()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    val errorCode = error?.errorCode ?: 0
                    val description = error?.description?.toString() ?: "Unknown error"
                    handleWebError(errorCode, description)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                tvTitle.text = title ?: "Loading..."
            }
        }
    }

    private fun setupToolbar() {
        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        btnForward.setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()
            }
        }

        btnRefresh.setOnClickListener {
            webView.reload()
        }

        btnRetry.setOnClickListener {
            webView.reload()
        }

        btnClose.setOnClickListener {
            finish()
        }

        btnShare.setOnClickListener {
            val url = webView.url
            if (!url.isNullOrEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                startActivity(Intent.createChooser(shareIntent, "Share URL"))
            }
        }
    }

    private fun loadUrl(url: String) {
        webView.loadUrl(url)
        tvUrl.text = extractDomain(url)
        tvTitle.text = "Loading..."
    }

    private fun updateUrlDisplay(url: String?) {
        url?.let {
            tvUrl.text = extractDomain(it)
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: url
            host.removePrefix("www.")
        } catch (e: Exception) {
            url
        }
    }

    private fun updateNavButtons() {
        btnBack.isEnabled = webView.canGoBack()
        btnBack.alpha = if (webView.canGoBack()) 1.0f else 0.3f

        btnForward.isEnabled = webView.canGoForward()
        btnForward.alpha = if (webView.canGoForward()) 1.0f else 0.3f
    }

    private fun handleWebError(errorCode: Int, description: String) {
        val (title, message) = when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP ->
                "No Internet" to "Please check your internet connection and try again."
            WebViewClient.ERROR_TIMEOUT ->
                "Connection Timed Out" to "The website took too long to respond. Please try again."
            WebViewClient.ERROR_CONNECT ->
                "Connection Failed" to "Unable to connect to the website. It may be down."
            WebViewClient.ERROR_UNKNOWN ->
                "Page Not Found" to "The website could not be loaded. It may not exist."
            else ->
                "Error Loading Page" to description
        }
        showError(title, message)
    }

    private fun showError(title: String, message: String) {
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        tvErrorTitle.text = title
        tvErrorMessage.text = message
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
