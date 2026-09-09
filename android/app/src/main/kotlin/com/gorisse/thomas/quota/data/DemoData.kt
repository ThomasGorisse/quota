package com.gorisse.thomas.quota.data

import java.time.Duration
import java.time.Instant

/**
 * A canned `/v1/status` used by the debug-only "demo data" toggle, so the screens and the
 * widget can be validated before the backend is deployed. Reset times are relative to now,
 * which is what makes the "resets in …" line worth looking at.
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
        ),
    )

    private fun window(id: String, label: String, percent: Int, resetsAt: Instant) =
        Window(id = id, label = label, percent = percent, resetsAt = resetsAt.toString())
}
