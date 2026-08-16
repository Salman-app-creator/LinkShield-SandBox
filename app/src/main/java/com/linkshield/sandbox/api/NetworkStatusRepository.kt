package com.linkshield.sandbox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class NetworkStatus(
    val publicIp: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val countryCode: String = "",
    val timezone: String = "",
    val isp: String = "",
    val encryptedDns: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val locationText: String
        get() {
            return listOf(
                city,
                region,
                countryCode
            )
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
        }
}

class NetworkStatusRepository {

    suspend fun fetchStatus(): NetworkStatus =
        withContext(Dispatchers.IO) {

            try {
                val json =
                    requestJson(
                        "https://ipapi.co/json/"
                    )

                val ip =
                    json.optString("ip")

                val city =
                    json.optString("city")

                val region =
                    json.optString("region")

                val country =
                    json.optString("country_name")

                val countryCode =
                    json.optString("country_code")

                val timezone =
                    json.optString("timezone")

                val isp =
                    json.optString("org")

                return@withContext NetworkStatus(
                    publicIp = ip,
                    city = city,
                    region = region,
                    country = country,
                    countryCode = countryCode,
                    timezone = timezone,
                    isp = isp,
                    encryptedDns =
                        detectEncryptedDns(),
                    isLoading = false
                )
            } catch (firstError: Exception) {

                return@withContext try {
                    fetchFromIpApi()
                } catch (secondError: Exception) {
                    NetworkStatus(
                        isLoading = false,
                        error =
                            secondError.message
                                ?: firstError.message
                                ?: "Network status unavailable"
                    )
                }
            }
        }

    private fun fetchFromIpApi(): NetworkStatus {
        val json =
            requestJson(
                "http://ip-api.com/json/?fields=" +
                    "status,message,query,city," +
                    "regionName,country,countryCode," +
                    "timezone,isp,org"
            )

        if (
            json.optString("status")
                .equals("fail", true)
        ) {
            throw IllegalStateException(
                json.optString(
                    "message",
                    "IP lookup failed"
                )
            )
        }

        return NetworkStatus(
            publicIp =
                json.optString("query"),
            city =
                json.optString("city"),
            region =
                json.optString("regionName"),
            country =
                json.optString("country"),
            countryCode =
                json.optString("countryCode"),
            timezone =
                json.optString("timezone"),
            isp =
                json.optString("isp")
                    .ifBlank {
                        json.optString("org")
                    },
            encryptedDns =
                detectEncryptedDns(),
            isLoading = false
        )
    }

    private fun requestJson(
        endpoint: String
    ): JSONObject {

        val connection =
            (URL(endpoint).openConnection()
                as HttpURLConnection).apply {

                requestMethod = "GET"

                connectTimeout = 10_000
                readTimeout = 15_000

                useCaches = false

                setRequestProperty(
                    "Accept",
                    "application/json"
                )

                setRequestProperty(
                    "User-Agent",
                    "LinkShield/1.0"
                )
            }

        try {
            val status =
                connection.responseCode

            if (status !in 200..299) {
                throw IllegalStateException(
                    "HTTP $status"
                )
            }

            val response =
                connection.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun detectEncryptedDns(): Boolean {
        /*
         * A public-IP API cannot reliably prove that
         * every DNS query is encrypted.
         *
         * This lightweight check only verifies whether
         * the device can reach a DNS-over-HTTPS endpoint.
         */
        return try {
            val connection =
                (URL(
                    "https://cloudflare-dns.com/dns-query"
                ).openConnection()
                    as HttpURLConnection).apply {

                    requestMethod = "HEAD"

                    connectTimeout = 3_000
                    readTimeout = 3_000

                    setRequestProperty(
                        "Accept",
                        "application/dns-message"
                    )
                }

            val success =
                connection.responseCode in 200..399

            connection.disconnect()

            success
        } catch (_: Exception) {
            false
        }
    }
        suspend fun refresh(): NetworkStatus =
        withContext(Dispatchers.IO) {
            fetchStatus()
        }

    suspend fun getPublicIp(): String =
        withContext(Dispatchers.IO) {
            try {
                requestJson(
                    "https://ipapi.co/ip/"
                )
                    .optString(
                        "ip"
                    )
            } catch (_: Exception) {
                try {
                    val connection =
                        (URL(
                            "https://api.ipify.org"
                        ).openConnection()
                            as HttpURLConnection).apply {

                            requestMethod = "GET"

                            connectTimeout = 5_000
                            readTimeout = 5_000
                        }

                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                                .trim()
                        }
                        .also {
                            connection.disconnect()
                        }
                } catch (_: Exception) {
                    ""
                }
            }
        }
}
