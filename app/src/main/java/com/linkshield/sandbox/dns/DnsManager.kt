package com.linkshield.sandbox.dns

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// DnsManager.kt
//
// Responsibilities:
//   1. Build and manage OkHttp clients with DNS-over-HTTPS (DoH) resolvers.
//   2. Support multiple DoH providers with per-provider bootstrap IPs.
//   3. Provide a Cloudflare WARP-style fallback resolver when primary DoH fails.
//   4. Persist shield state and selected provider across app restarts.
//   5. Expose pro/trial state checks so callers can gate shield features.
//   6. Allow callers to query remaining free trial downloads.
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "DnsManager"

// SharedPreferences keys — must match PreferencesManager constants exactly
private const val PREFS_NAME            = "shield_prefs"
private const val KEY_SHIELD_ENABLED    = "shield_enabled"
private const val KEY_DNS_PROVIDER      = "dns_provider"
private const val KEY_IS_PRO            = "is_pro"
private const val KEY_DOWNLOAD_COUNT    = "download_count"
private const val KEY_INITIALIZED       = "initialized"
private const val FREE_DOWNLOAD_LIMIT   = 20

// Cloudflare WARP DoH endpoint (WARP uses 1.1.1.1 with /dns-query, same as Cloudflare DoH,
// but we separate it as a fallback so callers know which resolver is currently active)
private const val WARP_DOH_URL = "https://1.1.1.1/dns-query"

class DnsManager(private val context: Context) {

    // ── Provider catalogue ────────────────────────────────────────────────────
    enum class DnsProvider(
        val displayName:  String,
        val dohUrl:       String,
        val bootstrapIps: List<String>
    ) {
        CLOUDFLARE(
            displayName  = "Cloudflare",
            dohUrl       = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1")
        ),
        CLOUDFLARE_WARP(
            displayName  = "Cloudflare WARP (Fallback)",
            dohUrl       = WARP_DOH_URL,
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1")
        ),
        ADGUARD(
            displayName  = "AdGuard",
            dohUrl       = "https://dns.adguard-dns.com/dns-query",
            bootstrapIps = listOf("94.140.14.14", "94.140.15.15")
        ),
        QUAD9(
            displayName  = "Quad9",
            dohUrl       = "https://dns.quad9.net/dns-query",
            bootstrapIps = listOf("9.9.9.9", "149.112.112.112")
        ),
        GOOGLE(
            displayName  = "Google",
            dohUrl       = "https://dns.google/dns-query",
            bootstrapIps = listOf("8.8.8.8", "8.8.4.4")
        )
    }

    // ── Internal state ────────────────────────────────────────────────────────
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Plain bootstrap client — no custom DNS, used to reach DoH endpoints.
    // Also returned when shield is OFF so the rest of the app always gets a
    // valid OkHttpClient regardless of shield state.
    private val bootstrapClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10,  TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Active DoH client — null when shield is disabled
    private var activeDohClient: OkHttpClient? = null

    // Currently selected provider (relevant only when shield is ON)
    private var activeProvider: DnsProvider = DnsProvider.CLOUDFLARE

    // ── Initialisation — restore persisted state ──────────────────────────────
    init {
        ensureDownloadCountInitialized()

        if (isShieldPersistedOn()) {
            val saved = getSavedProvider()
            try {
                buildAndSetDohClient(saved)
                Log.d(TAG, "Restored DoH shield: ${saved.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore DoH on init, disabling shield: ${e.message}")
                prefs.edit().putBoolean(KEY_SHIELD_ENABLED, false).apply()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the active DoH OkHttpClient when shield is ON,
     * or the plain bootstrap client when shield is OFF.
     * Callers should always use this — never cache the result.
     */
    fun getClient(): OkHttpClient = activeDohClient ?: bootstrapClient

    /**
     * Enables DoH with the given provider.
     * Automatically falls back to CLOUDFLARE_WARP if the primary provider
     * fails to build a valid resolver.
     *
     * @param provider  The desired DoH provider.
     * @return          The newly built DoH OkHttpClient.
     * @throws IllegalStateException if even the WARP fallback fails.
     */
    fun enableDoh(provider: DnsProvider = DnsProvider.CLOUDFLARE): OkHttpClient {
        return try {
            buildAndSetDohClient(provider)
            persistShieldState(enabled = true, provider = provider)
            Log.d(TAG, "DoH enabled: ${provider.displayName}")
            activeDohClient!!
        } catch (primary: Exception) {
            Log.w(TAG, "Primary DoH failed (${provider.displayName}), trying WARP fallback: ${primary.message}")
            try {
                buildAndSetDohClient(DnsProvider.CLOUDFLARE_WARP)
                persistShieldState(enabled = true, provider = DnsProvider.CLOUDFLARE_WARP)
                Log.d(TAG, "DoH fallback active: CLOUDFLARE_WARP")
                activeDohClient!!
            } catch (fallback: Exception) {
                Log.e(TAG, "WARP fallback also failed: ${fallback.message}")
                throw IllegalStateException(
                    "DoH unavailable — primary: ${primary.message}, WARP: ${fallback.message}"
                )
            }
        }
    }

    /**
     * Disables DoH. All subsequent [getClient] calls return the plain client.
     */
    fun disableDoh() {
        activeDohClient = null
        prefs.edit()
            .putBoolean(KEY_SHIELD_ENABLED, false)
            .remove(KEY_DNS_PROVIDER)
            .apply()
        Log.d(TAG, "DoH disabled")
    }

    /** True when the DoH client is built and active. */
    fun isDohEnabled(): Boolean = activeDohClient != null

    /** The provider whose resolver is currently active. */
    fun getCurrentProvider(): DnsProvider = activeProvider

    /** True when the shield was persisted ON in SharedPreferences. */
    fun isShieldPersistedOn(): Boolean =
        prefs.getBoolean(KEY_SHIELD_ENABLED, false)

    // ─────────────────────────────────────────────────────────────────────────
    // PRO / TRIAL STATE  (read-only — writes go through PreferencesManager /
    //                     LicenseManager; DnsManager only reads to gate access)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if the user has an active Pro licence.
     * Pro users get unlimited shield usage and unlimited downloads.
     */
    fun isProUser(): Boolean =
        prefs.getBoolean(KEY_IS_PRO, false)

    /**
     * Returns the number of free downloads the user has consumed so far.
     * Range: 0 … FREE_DOWNLOAD_LIMIT (20).
     */
    fun getDownloadCount(): Int =
        prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    /**
     * Returns the number of free downloads still available.
     * Always returns Int.MAX_VALUE for pro users.
     */
    fun getRemainingDownloads(): Int =
        if (isProUser()) Int.MAX_VALUE
        else maxOf(0, FREE_DOWNLOAD_LIMIT - getDownloadCount())

    /**
     * Returns true if the user is allowed to trigger another download
     * (either Pro, or still within the free 20-download trial).
     */
    fun canDownload(): Boolean =
        isProUser() || getDownloadCount() < FREE_DOWNLOAD_LIMIT

    /**
     * Returns true if the shield DoH bypass feature is accessible.
     * Pro users always get access; trial users get access while downloads remain.
     * Note: the shield UI toggle itself is always visible — this gates the
     * actual DoH interception being applied inside shouldInterceptRequest.
     */
    fun isShieldAccessible(): Boolean = canDownload()

    /**
     * Resolves a hostname to a list of InetAddresses using the active DoH
     * client. Falls back to the system resolver if DoH is not enabled or fails.
     *
     * Useful for callers that need manual DNS resolution (e.g. prefetch checks).
     */
    fun resolveHostname(hostname: String): List<InetAddress> {
        if (!isDohEnabled()) {
            return try {
                InetAddress.getAllByName(hostname).toList()
            } catch (e: UnknownHostException) {
                Log.e(TAG, "System DNS failed for $hostname: ${e.message}")
                emptyList()
            }
        }
        return try {
            // OkHttp's DnsOverHttps implements okhttp3.Dns — we can call it directly
            val dnsResolver = buildDnsResolver(activeProvider)
            dnsResolver.lookup(hostname)
        } catch (e: Exception) {
            Log.e(TAG, "DoH resolution failed for $hostname: ${e.message}")
            try {
                InetAddress.getAllByName(hostname).toList()
            } catch (fallback: UnknownHostException) {
                emptyList()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a [DnsOverHttps] resolver for the given provider and attaches it
     * to a new OkHttpClient, storing both in [activeDohClient] and
     * [activeProvider].
     */
    private fun buildAndSetDohClient(provider: DnsProvider) {
        val dns = buildDnsResolver(provider)

        activeDohClient = bootstrapClient.newBuilder()
            .dns(dns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20,  TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        activeProvider = provider
    }

    /**
     * Constructs a [DnsOverHttps] instance for [provider] using its
     * hardcoded bootstrap IP addresses so the DoH endpoint itself can be
     * reached without relying on the system resolver (which may be blocked).
     */
    private fun buildDnsResolver(provider: DnsProvider): DnsOverHttps {
        val httpUrl = provider.dohUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid DoH URL: ${provider.dohUrl}")

        val bootstrapHosts: Array<InetAddress> = provider.bootstrapIps
            .map { InetAddress.getByName(it) }
            .toTypedArray()

        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(httpUrl)
            .bootstrapDnsHosts(*bootstrapHosts)
            .includeIPv6(true)
            .post(true)             // use POST for privacy (query not in URL)
            .build()
    }

    /**
     * Writes shield enabled state and selected provider to SharedPreferences.
     */
    private fun persistShieldState(enabled: Boolean, provider: DnsProvider) {
        prefs.edit()
            .putBoolean(KEY_SHIELD_ENABLED, enabled)
            .putString(KEY_DNS_PROVIDER, provider.name)
            .apply()
    }

    /**
     * Reads back the last persisted [DnsProvider], defaulting to CLOUDFLARE.
     */
    private fun getSavedProvider(): DnsProvider {
        val saved = prefs.getString(KEY_DNS_PROVIDER, DnsProvider.CLOUDFLARE.name)
        return try {
            DnsProvider.valueOf(saved ?: DnsProvider.CLOUDFLARE.name)
        } catch (_: IllegalArgumentException) {
            DnsProvider.CLOUDFLARE
        }
    }

    /**
     * Guarantees that the download counter starts at 0 on a fresh install.
     * Uses a separate boolean flag (KEY_INITIALIZED) so we only reset once —
     * if the prefs file somehow survives a reinstall with a stale counter,
     * the absence of KEY_INITIALIZED triggers a clean reset.
     */
    private fun ensureDownloadCountInitialized() {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putInt(KEY_DOWNLOAD_COUNT, 0)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
            Log.d(TAG, "Download counter initialized to 0 (fresh install)")
        }
    }
}
