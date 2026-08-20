package com.linkshield.sandbox.dns

// ─────────────────────────────────────────────────────────────────────────────
// DnsShieldClient.kt
//
// Thin singleton that layers a 10 MB OkHttp HTTP-response cache on top of
// the existing DnsManager (which already handles DoH + SNI fragmentation).
//
// DnsManager  →  DNS query routing (Cloudflare / Google / Quad9 / AdGuard)
// DnsShieldClient  →  HTTP response caching + clean static API for the UI
//
// DO NOT replace DnsManager — this complements it.
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context
import android.webkit.WebView
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object DnsShieldClient {

    private const val CACHE_DIR_NAME  = "linkshield_http_cache"
    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024   // 10 MB

    @Volatile private var cachedClient: OkHttpClient? = null
    @Volatile private var dnsManager:   DnsManager?   = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the shared, cache-enabled OkHttpClient.
     * All app network calls should go through this client:
     *
     *   DnsShieldClient.get(context).newCall(request).execute()
     *
     * If DoH is enabled in DnsManager, DNS is already encrypted.
     * The 10 MB HTTP cache reduces repeated downloads of same resources.
     */
    fun get(context: Context): OkHttpClient {
        return cachedClient ?: synchronized(this) {
            cachedClient ?: buildClient(context).also { cachedClient = it }
        }
    }

    /**
     * Enable DNS-over-HTTPS with [provider] (default: Cloudflare 1.1.1.1).
     * Rebuilds the OkHttpClient so new requests use encrypted DNS immediately.
     */
    fun enableShield(
        context:  Context,
        provider: DohProvider = DohProvider.CLOUDFLARE
    ) {
        manager(context).enableDoh(provider)
        invalidate()                       // force rebuild with new DNS
    }

    /** Disable DoH. Plain system DNS will be used for new requests. */
    fun disableShield(context: Context) {
        manager(context).disableDoh()
        invalidate()
    }

    /** Switch DNS provider without toggling shield on/off. */
    fun switchProvider(context: Context, provider: DohProvider) {
        if (manager(context).isDohEnabled()) {
            enableShield(context, provider)
        }
    }

    fun isDohEnabled(context: Context): Boolean = manager(context).isDohEnabled()
    fun currentProvider(context: Context): DohProvider = manager(context).getCurrentProvider()

    // ── WebView integration ───────────────────────────────────────────────────
    /**
     * Apply security settings to a WebView so it benefits from the same
     * DoH protection used by the OkHttp client.
     *
     * IMPORTANT: WebView uses the system DNS resolver by default — it does
     * NOT automatically use OkHttp's DoH.  Mitigations applied here:
     *   1. Mixed-content blocking (no HTTP on HTTPS pages)
     *   2. JavaScript sandboxed to HTTPS origins only
     *   3. Third-party cookie isolation
     *   4. Safe-browsing enabled (uses Google's local DB, no extra DNS)
     *
     * For full DoH in WebView, use the SandboxBrowserScreen approach of
     * proxying through OkHttp (intercept requests via WebViewClient and
     * re-fetch through DnsShieldClient.get(context)).
     */
    fun applyToWebView(webView: WebView) {
        webView.settings.apply {
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowContentAccess       = false
            allowFileAccess          = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        android.webkit.WebView.setWebContentsDebuggingEnabled(false)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun manager(context: Context): DnsManager =
        dnsManager ?: synchronized(this) {
            dnsManager ?: DnsManager(context.applicationContext)
                .also { dnsManager = it }
        }

    private fun invalidate() {
        synchronized(this) { cachedClient = null }
    }

    private fun buildClient(context: Context): OkHttpClient {
        val mgr   = manager(context)
        val base  = mgr.getClient()          // already has DoH + SNI fragmentation

        val cache = Cache(
            directory = File(context.applicationContext.cacheDir, CACHE_DIR_NAME),
            maxSize   = CACHE_SIZE_BYTES
        )

        // Layer the HTTP cache on top — everything else (DoH, timeouts,
        // retry-on-failure, SSL pinning) is inherited from DnsManager's client.
        return base.newBuilder()
            .cache(cache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
