package com.gorisse.thomas.quota.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** `HttpURLConnection` is enough for one GET; it keeps the APK and the build small. */
class QuotaApi(private val json: Json = DefaultJson) {

    suspend fun status(pairing: Pairing): StatusResponse = withContext(Dispatchers.IO) {
        val url = URL(pairing.host.trimEnd('/') + "/v1/status")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer ${pairing.token}")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                throw UnauthorizedException()
            }
            if (code !in 200..299) {
                // Never echo the body: it is the server's, and the URL carries the token.
                throw IOException("Server returned HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(StatusResponse.serializer(), body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * `PUT /v1/settings {"display": …}` — the one setting this app writes, so the same
     * choice shows on the web dashboard and on the TRMNL screen.
     */
    suspend fun setDisplay(pairing: Pairing, mode: DisplayMode): Unit = withContext(Dispatchers.IO) {
        val url = URL(pairing.host.trimEnd('/') + "/v1/settings")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Authorization", "Bearer ${pairing.token}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
        }
        try {
            connection.outputStream.use { it.write("""{"display":"${mode.wire}"}""".toByteArray()) }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                throw UnauthorizedException()
            }
            if (code !in 200..299) throw IOException("Server returned HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

/** The token was rejected: the only error that should send the user back to pairing. */
class UnauthorizedException : IOException("This device is not paired any more")
