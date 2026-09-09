package com.gorisse.thomas.quota.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * The one place that knows how to get a [Snapshot]: used by the app and, in the same
 * process, by the Glance widget's worker. The last successful payload is cached in plain
 * preferences — it holds percentages and reset dates only, never the token.
 */
class StatusRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val api = QuotaApi()
    private val json: Json = QuotaApi.DefaultJson
    private val cache = appContext.getSharedPreferences("quota.cache", Context.MODE_PRIVATE)

    val pairingStore = PairingStore(appContext)

    private val _snapshot = MutableStateFlow(readCache())

    /** Last known status, cache included, so a cold start paints immediately. */
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    val isPaired: Boolean get() = pairingStore.read() != null
    val isDemo: Boolean get() = pairingStore.demoData

    /** Fetches a fresh status, updates the cache, and returns it. */
    suspend fun refresh(): Result<Snapshot> {
        if (pairingStore.demoData) {
            val snapshot = Snapshot(DemoData.status(Instant.now()), System.currentTimeMillis())
            store(snapshot)
            return Result.success(snapshot)
        }
        val pairing = pairingStore.read()
            ?: return Result.failure(IllegalStateException("This device is not paired yet"))
        return runCatching {
            Snapshot(api.status(pairing), System.currentTimeMillis()).also { store(it) }
        }
    }

    fun clearPairing() {
        pairingStore.clear()
        pairingStore.demoData = false
        cache.edit().clear().apply()
        _snapshot.value = null
    }

    private fun store(snapshot: Snapshot) {
        cache.edit()
            .putString(KEY_PAYLOAD, json.encodeToString(StatusResponse.serializer(), snapshot.status))
            .putLong(KEY_RECEIVED_AT, snapshot.receivedAtMillis)
            .apply()
        _snapshot.value = snapshot
    }

    private fun readCache(): Snapshot? {
        val payload = cache.getString(KEY_PAYLOAD, null) ?: return null
        val receivedAt = cache.getLong(KEY_RECEIVED_AT, 0L)
        val status = runCatching {
            json.decodeFromString(StatusResponse.serializer(), payload)
        }.getOrNull() ?: return null
        return Snapshot(status, receivedAt)
    }

    companion object {
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_RECEIVED_AT = "received_at"

        @Volatile
        private var instance: StatusRepository? = null

        fun get(context: Context): StatusRepository =
            instance ?: synchronized(this) {
                instance ?: StatusRepository(context).also { instance = it }
            }
    }
}
