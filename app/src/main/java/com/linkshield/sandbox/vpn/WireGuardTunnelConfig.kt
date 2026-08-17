package com.linkshield.sandbox.vpn

data class WireGuardTunnelConfig(
    val interfacePrivateKey: String,
    val interfaceAddress: String,
    val dnsServers: List<String>,
    val peerPublicKey: String,
    val peerEndpoint: String,
    val allowedIps: List<String>,
    val persistentKeepalive: Int? = null
)
fun WireGuardTunnelConfig.toWireGuardConfig(): String {
    return buildString {

        appendLine("[Interface]")
        appendLine(
            "PrivateKey = $interfacePrivateKey"
        )
        appendLine(
            "Address = $interfaceAddress"
        )

        if (dnsServers.isNotEmpty()) {
            appendLine(
                "DNS = ${dnsServers.joinToString(", ")}"
            )
        }

        appendLine()
        appendLine("[Peer]")
        appendLine(
            "PublicKey = $peerPublicKey"
        )
        appendLine(
            "Endpoint = $peerEndpoint"
        )
        appendLine(
            "AllowedIPs = ${allowedIps.joinToString(", ")}"
        )

        persistentKeepalive?.let {
            appendLine(
                "PersistentKeepalive = $it"
            )
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
