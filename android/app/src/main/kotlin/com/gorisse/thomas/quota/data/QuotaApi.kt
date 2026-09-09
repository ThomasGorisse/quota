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

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

/** The token was rejected: the only error that should send the user back to pairing. */
class UnauthorizedException : IOException("This device is not paired any more")
