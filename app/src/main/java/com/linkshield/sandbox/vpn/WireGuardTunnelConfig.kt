package com.linkshield.sandbox.vpn

data class WireGuardTunnelConfig(
    val interfacePrivateKey: String,
    val interfaceAddress: String,
    // Default DNS set kar diya hai taake DNS Over HTTPS/DoH fail na ho
    val dnsServers: List<String> = listOf("1.1.1.1", "1.0.0.1"),
    val peerPublicKey: String,
    val peerEndpoint: String,
    val allowedIps: List<String> = listOf("0.0.0.0/0", "::/0"),
    val persistentKeepalive: Int? = 25
)

fun WireGuardTunnelConfig.toWireGuardConfig(): String {
    return buildString {

        appendLine("[Interface]")
        appendLine("PrivateKey = $interfacePrivateKey")
        appendLine("Address = $interfaceAddress")

        // Agar list khali bhi di jaye tab bhi secure Cloudflare DNS force hoga
        val activeDns = if (dnsServers.isNotEmpty()) dnsServers else listOf("1.1.1.1", "1.0.0.1")
        appendLine("DNS = ${activeDns.joinToString(", ")}")

        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = $peerPublicKey")
        appendLine("Endpoint = $peerEndpoint")
        appendLine("AllowedIPs = ${allowedIps.joinToString(", ")}")

        persistentKeepalive?.let {
            appendLine("PersistentKeepalive = $it")
        }
    }
}

fun WireGuardTunnelConfig.isUsable(): Boolean {
    return interfacePrivateKey.isNotBlank() &&
        interfaceAddress.isNotBlank() &&
        peerPublicKey.isNotBlank() &&
        peerEndpoint.isNotBlank() &&
        allowedIps.isNotEmpty()
}
