package com.linkshield.sandbox.vpn

import android.util.Base64

/**
 * ShadowsocksConfig.kt
 *
 * Single source of truth for the LinkShield backend server details.
 * All values are compile-time constants so R8/ProGuard can inline and
 * obfuscate them in release builds.
 *
 * To update the server, change values here — no other file needs editing.
 */
object ShadowsocksConfig {

    // ── Server Details ────────────────────────────────────────────────────────

    const val HOST       = "141.148.223.177"
    const val PORT       = 8080
    const val PASSWORD   = "08f8ECxZJMzOzHSc3DWTh9"
    const val METHOD     = "chacha20-ietf-poly1305"
    const val PROFILE_NAME = "LinkShield VPN"

    // ── TUN Interface Settings ────────────────────────────────────────────────

    const val TUN_ADDRESS    = "10.233.233.1"
    const val TUN_PREFIX_LEN = 24
    const val TUN_ROUTE      = "0.0.0.0"               // Route ALL traffic through VPN
    const val TUN_ROUTE_LEN  = 0
    const val TUN_MTU        = 1500
    const val TUN_DNS_PRIMARY   = "1.1.1.1"            // Cloudflare
    const val TUN_DNS_SECONDARY = "8.8.8.8"            // Google fallback

    // ── Local SOCKS5 Proxy Port (ss-local listens here) ──────────────────────
    // tun2socks will forward TUN traffic to this SOCKS5 port
    const val LOCAL_PORT = 1080

    /**
     * Builds an Outline-compatible access key (ss:// URI) from config values.
     *
     * Format: ss://BASE64(method:password)@host:port
     *
     * This is the format Outline SDK / shadowsocks-libev expects.
     */
    fun buildAccessKey(): String {
        val userInfo   = "$METHOD:$PASSWORD"
        val encoded    = Base64.encodeToString(
            userInfo.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )
        return "ss://$encoded@$HOST:$PORT"
    }

    /**
     * Returns a JSON config string for ss-local / shadowsocks-libev.
     * Used when starting the native proxy process.
     */
    fun buildJsonConfig(): String = """
        {
            "server":       "$HOST",
            "server_port":  $PORT,
            "local_address":"127.0.0.1",
            "local_port":   $LOCAL_PORT,
            "password":     "$PASSWORD",
            "method":       "$METHOD",
            "timeout":      300,
            "mode":         "tcp_and_udp",
            "fast_open":    false
        }
    """.trimIndent()
}
