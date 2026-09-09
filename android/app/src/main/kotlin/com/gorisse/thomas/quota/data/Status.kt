package com.gorisse.thomas.quota.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Duration
import java.time.Instant

/**
 * The `GET /v1/status` payload, exactly as specified in the repository README.
 *
 * `percent` is the percentage **used**; every gauge in this app shows [Window.remaining].
 */
@Serializable
data class StatusResponse(
    @SerialName("collected_at") val collectedAt: String,
    @SerialName("age_seconds") val ageSeconds: Long = 0,
    val stale: Boolean = false,
    val providers: Map<String, Provider> = emptyMap(),
    val severity: String = Severity.OK.wire,
    val history: List<JsonElement> = emptyList(),
)

@Serializable
data class Provider(
    val plan: String? = null,
    val windows: List<Window> = emptyList(),
    val extra: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class Window(
    val id: String,
    val label: String,
    val percent: Int,
    @SerialName("resets_at") val resetsAt: String? = null,
) {
    /** What is left, which is the number worth reading. */
    val remaining: Int get() = (100 - percent).coerceIn(0, 100)

    val severity: Severity get() = Severity.ofUsedPercent(percent)

    /** Time until this window resets, or null when the payload carries no reset date. */
    fun resetsIn(now: Instant = Instant.now()): Duration? {
        val at = resetsAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val left = Duration.between(now, at)
        return if (left.isNegative) Duration.ZERO else left
    }
}

enum class Severity(val wire: String) {
    OK("ok"),
    WARN("warn"),
    CRITICAL("critical"),
    STALE("stale");

    companion object {
        /** README rule: `<60` ok, `60–85` warn, `>85` critical — on the percentage used. */
        fun ofUsedPercent(percent: Int): Severity = when {
            percent > 85 -> CRITICAL
            percent >= 60 -> WARN
            else -> OK
        }

        fun ofWire(value: String?): Severity =
            entries.firstOrNull { it.wire == value } ?: OK
    }
}

/** Display order and labels for the two providers this version knows about. */
enum class ProviderId(val key: String, val displayName: String) {
    CLAUDE("claude", "Claude"),
    CODEX("codex", "Codex");

    companion object {
        fun of(key: String): ProviderId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A [StatusResponse] plus the moment this device received it, so that "updated 42 s ago"
 * keeps counting while the screen is open and the widget can tell a cached payload is old.
 */
data class Snapshot(
    val status: StatusResponse,
    val receivedAtMillis: Long,
) {
    fun ageSeconds(nowMillis: Long = System.currentTimeMillis()): Long =
        status.ageSeconds + ((nowMillis - receivedAtMillis) / 1000).coerceAtLeast(0)

    /** Anything older than ten minutes is shown grey, whatever the server said. */
    fun isStale(nowMillis: Long = System.currentTimeMillis()): Boolean =
        status.stale || ageSeconds(nowMillis) > STALE_AFTER_SECONDS

    fun severity(nowMillis: Long = System.currentTimeMillis()): Severity =
        if (isStale(nowMillis)) Severity.STALE else Severity.ofWire(status.severity)

    /** Providers in a stable order: the ones we know about first, then anything new. */
    fun orderedProviders(): List<Pair<String, Provider>> {
        val known = ProviderId.entries.mapNotNull { id ->
            status.providers[id.key]?.let { id.displayName to it }
        }
        val extra = status.providers
            .filterKeys { key -> ProviderId.of(key) == null }
            .map { (key, provider) -> key.replaceFirstChar(Char::uppercase) to provider }
        return known + extra
    }

    companion object {
        const val STALE_AFTER_SECONDS = 10 * 60L
    }
}
