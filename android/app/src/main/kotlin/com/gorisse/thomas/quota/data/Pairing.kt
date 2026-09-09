package com.gorisse.thomas.quota.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.URI

/** Host used before the backend is deployed; the pairing URL always wins over it. */
const val DEFAULT_HOST = "https://quota.thomasgorisse.workers.dev"

/** Where this device points and with which bearer token. */
data class Pairing(val host: String, val token: String)

/**
 * Parses `https://<host>/#t=<token>`.
 *
 * Accepts the token in the fragment (the form the collector prints, which keeps it out of
 * server logs) or, as a convenience when a URL has been copied through something that eats
 * fragments, in the query string.
 */
fun parsePairingUrl(input: String): Pairing? {
    val raw = input.trim()
    if (raw.isEmpty()) return null
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "https" && scheme != "http") return null
    val authority = uri.authority?.takeIf { it.isNotBlank() } ?: return null
    val token = tokenFrom(uri.rawFragment) ?: tokenFrom(uri.rawQuery) ?: return null
    if (token.isBlank()) return null
    return Pairing(host = "$scheme://$authority", token = token)
}

private fun tokenFrom(part: String?): String? = part
    ?.split('&')
    ?.mapNotNull { pair ->
        val index = pair.indexOf('=')
        if (index <= 0) null else pair.substring(0, index) to pair.substring(index + 1)
    }
    ?.firstOrNull { (key, _) -> key == "t" || key == "token" }
    ?.second
    ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }

/**
 * Host and token on disk, encrypted. Nothing else in the app persists the token, and it is
 * never logged: the whole point of the product is that credentials stay on the Mac and this
 * device only ever holds one revocable device token.
 */
class PairingStore(context: Context) {

    // EncryptedSharedPreferences is deprecated in security-crypto 1.1.0 with no Jetpack
    // replacement offered. Keep it: on API 26+ it still wraps the token in an AES-GCM key
    // held by the Android keystore, which is exactly what is wanted, and the alternative
    // today is hand-rolling the same thing.
    @Suppress("DEPRECATION")
    private val prefs: SharedPreferences = run {
        val app = context.applicationContext
        val key = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            app,
            "quota.pairing",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun read(): Pairing? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return Pairing(host, token)
    }

    fun write(pairing: Pairing) {
        prefs.edit()
            .putString(KEY_HOST, pairing.host)
            .putString(KEY_TOKEN, pairing.token)
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_HOST).remove(KEY_TOKEN).apply()
    }

    /** Debug-only: render the UI from a canned payload before the backend exists. */
    var demoData: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DEMO, value).apply()
        }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_TOKEN = "token"
        const val KEY_DEMO = "demo_data"
    }
}
