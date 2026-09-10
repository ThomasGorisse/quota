package com.gorisse.thomas.quota.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

/**
 * A canned `/v1/status` behind "Try with sample data" on the pairing screen — shipped in
 * release too, so the app is explorable without a collector, a store reviewer included.
 * Reset times are relative to now, which is what makes the "resets in …" line worth looking
 * at. Nothing here ever reaches the network: [StatusRepository.refresh] returns it directly.
 *
 * The numbers deliberately cover the three severities: an ok 5 h window, a warn week, and a
 * critical Codex session.
 */
object DemoData {

    fun status(now: Instant = Instant.now()): StatusResponse = StatusResponse(
        collectedAt = now.toString(),
        ageSeconds = 42,
        stale = false,
        severity = Severity.CRITICAL.wire,
        history = (0..24).map { hour ->
            buildJsonObject {
                put("t", (now - Duration.ofHours((24 - hour).toLong())).toString())
                put("claude.session", if (hour < 22) 8 + hour * 3 else 5 + (hour - 22) * 3)
                put("claude.weekly", 44 + hour)
                put("codex.session", 20 + hour * 3)
                put("codex.weekly", 10 + hour)
                put("gemini.gemini-3-pro", 20 + hour * 2)
                put("gemini.gemini-2-5-pro", 6 + hour)
            }
        },
        providers = mapOf(
            "claude" to Provider(
                plan = "max",
                windows = listOf(
                    window("session", "5 h", 11, now + Duration.ofMinutes(130)),
                    window("weekly", "Week", 68, now + Duration.ofHours(62)),
                ),
            ),
            "codex" to Provider(
                plan = "plus",
                windows = listOf(
                    window("session", "5 h", 92, now + Duration.ofMinutes(37)),
                    window("weekly", "Week", 34, now + Duration.ofHours(140)),
                    window("reserve", "Reserve", 10, now + Duration.ofHours(96)),
                ),
            ),
            // The Code Assist buckets are per model, most consumed first, so the headline
            // window is a model name rather than a time window.
            "gemini" to Provider(
                plan = "Standard",
                windows = listOf(
                    window("gemini-3-pro", "3 Pro", 62, now + Duration.ofMinutes(315)),
                    window("gemini-2-5-pro", "2.5 Pro", 29, now + Duration.ofHours(14)),
                ),
            ),
        ),
    )

    private fun window(id: String, label: String, percent: Int, resetsAt: Instant) =
        Window(id = id, label = label, percent = percent, resetsAt = resetsAt.toString())
}
