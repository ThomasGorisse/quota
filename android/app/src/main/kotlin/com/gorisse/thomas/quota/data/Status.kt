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
    /** How every client prints a percentage. Absent on older workers: assume remaining. */
    val display: String = DisplayMode.REMAINING.wire,
    val history: List<JsonElement> = emptyList(),
) {
    val displayMode: DisplayMode get() = DisplayMode.ofWire(display)
}

/**
 * `remaining` prints what is left and fills the ring as capacity drains; `used` prints what
 * has been spent and fills the ring as it is spent. Thresholds always run on the used
 * percentage, so severity colours never move with this setting.
 */
enum class DisplayMode(val wire: String) {
    REMAINING("remaining"),
    USED("used");

    val isUsed: Boolean get() = this == USED

    companion object {
        fun ofWire(value: String?): DisplayMode =
            entries.firstOrNull { it.wire == value } ?: REMAINING
    }
}

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

    /** The number to print in this mode: what is left, or what has been spent. */
    fun shown(mode: DisplayMode): Int = if (mode.isUsed) percent.coerceIn(0, 100) else remaining

    /** How much of the ring is swept: it drains in remaining mode, it grows in used mode. */
    fun fraction(mode: DisplayMode): Float = shown(mode) / 100f

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

    val displayMode: DisplayMode get() = status.displayMode

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
