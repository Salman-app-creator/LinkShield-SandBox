package com.linkshield.sandbox.vpn

data class VpnTunnelConfig(
    val serverAddress: String,
    val serverPort: Int,
    val privateKey: String,
    val publicKey: String,
    val clientAddress: String,
    val dnsServer: String = "1.1.1.1",
    val mtu: Int = 1280
)
object VpnTunnelConfigProvider {

    fun load(): VpnTunnelConfig? {
        return null
    }

    fun isConfigured(): Boolean {
        return load() != null
    }
}
/*
 * A real server configuration must be supplied before
 * establishing a remote encrypted tunnel.
 *
 * Do not put private keys or server credentials directly
 * into the source code.
 */
