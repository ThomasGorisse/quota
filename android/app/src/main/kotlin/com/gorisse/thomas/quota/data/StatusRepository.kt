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

    /**
     * Left or Used, cached next to the payload so the widget paints the chosen mode on a
     * cold start too. The server is authoritative — every fresh status overwrites this —
     * but a local toggle writes it first so the UI and the widget turn together.
     */
    private val _displayMode = MutableStateFlow(readDisplayCache())
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    val isPaired: Boolean get() = pairingStore.read() != null
    val isDemo: Boolean get() = pairingStore.demoData

    /** Fetches a fresh status, updates the cache, and returns it. */
    suspend fun refresh(): Result<Snapshot> {
        if (pairingStore.demoData) {
            val demo = DemoData.status(Instant.now()).copy(display = _displayMode.value.wire)
            val snapshot = Snapshot(demo, System.currentTimeMillis())
            store(snapshot)
            return Result.success(snapshot)
        }
        val pairing = pairingStore.read()
            ?: return Result.failure(IllegalStateException("This device is not paired yet"))
        return runCatching {
            Snapshot(api.status(pairing), System.currentTimeMillis()).also { store(it) }
        }
    }

    /**
     * Optimistic, like the web dashboard: paint the new mode at once, persist it for the
     * other screens, then refresh so the server's answer is the one that sticks. Returns
     * false when the write failed, after putting the old mode back.
     */
    suspend fun setDisplayMode(mode: DisplayMode): Boolean {
        val previous = _displayMode.value
        if (mode == previous) return true
        writeDisplay(mode)
        val pairing = pairingStore.read()
        if (pairingStore.demoData || pairing == null) return true
        val sent = runCatching { api.setDisplay(pairing, mode) }
        if (sent.isFailure) {
            writeDisplay(previous)
            return false
        }
        refresh()
        return true
    }

    private fun writeDisplay(mode: DisplayMode) {
        cache.edit().putString(KEY_DISPLAY, mode.wire).apply()
        _displayMode.value = mode
    }

    fun clearPairing() {
        pairingStore.clear()
        pairingStore.demoData = false
        cache.edit().clear().apply()
        _snapshot.value = null
        _displayMode.value = DisplayMode.REMAINING
    }

    private fun store(snapshot: Snapshot) {
        cache.edit()
            .putString(KEY_PAYLOAD, json.encodeToString(StatusResponse.serializer(), snapshot.status))
            .putLong(KEY_RECEIVED_AT, snapshot.receivedAtMillis)
            .putString(KEY_DISPLAY, snapshot.displayMode.wire)
            .apply()
        _snapshot.value = snapshot
        _displayMode.value = snapshot.displayMode
    }

    private fun readDisplayCache(): DisplayMode =
        DisplayMode.ofWire(cache.getString(KEY_DISPLAY, null))

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
        private const val KEY_DISPLAY = "display"

        @Volatile
        private var instance: StatusRepository? = null

        fun get(context: Context): StatusRepository =
            instance ?: synchronized(this) {
                instance ?: StatusRepository(context).also { instance = it }
            }
    }
}
