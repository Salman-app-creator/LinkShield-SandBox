package com.linkshield.sandbox.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class VpnTunnelTransport {

    private var socket: Socket? = null

    suspend fun connect(
        config: VpnTunnelConfig
    ): Boolean = withContext(Dispatchers.IO) {

        if (config.serverAddress.isBlank()) {
            return@withContext false
        }

        if (config.serverPort !in 1..65535) {
            return@withContext false
        }

        runCatching {
            val newSocket = Socket()

            newSocket.connect(
                InetSocketAddress(
                    config.serverAddress,
                    config.serverPort
                ),
                CONNECT_TIMEOUT
            )

            socket = newSocket

            false
        }.getOrElse {
            socket?.close()
            socket = null
            false
        }
    }
    fun isConnected(): Boolean {
        return socket?.let {
            it.isConnected &&
                !it.isClosed
        } == true
    }

    suspend fun disconnect() =
        withContext(Dispatchers.IO) {
            runCatching {
                socket?.close()
            }

            socket = null
        }

    fun inputStream() =
        socket?.getInputStream()

    fun outputStream() =
        socket?.getOutputStream()
        companion object {
        private const val CONNECT_TIMEOUT = 5_000
    }
}
