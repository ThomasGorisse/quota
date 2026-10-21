package com.gorisse.thomas.quota.data

import com.gorisse.thomas.quota.ui.glanceWindows
import com.gorisse.thomas.quota.ui.glanceHero
import com.gorisse.thomas.quota.ui.glanceLabel
import com.gorisse.thomas.quota.ui.formatResetsIn
import java.time.Duration
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StatusTest {
    @Test fun decimalPercentagesKeepTheirSeverityBeforeDisplayRounding() {
        val provider = Json.decodeFromString<Provider>("""{"windows":[{"id":"a","label":"Session","percent":59.9},{"id":"b","label":"Week","percent":85.1}]}""")
        assertEquals(Severity.OK, provider.windows[0].severity)
        assertEquals(40, provider.windows[0].remaining)
        assertEquals(Severity.CRITICAL, provider.windows[1].severity)
        assertEquals(15, provider.windows[1].remaining)
        assertEquals("b", provider.glanceWindows().first().id)
    }

    @Test fun heroUsesValidWindowsAndRetainsPayloadOrderOnTies() {
        val provider = Provider(windows = listOf(
            Window("invalid", "Invalid", Double.NaN), Window("over", "Over", 101.0),
            Window("under", "Under", -1.0), Window("week", "Week", 76.0),
            Window("reserve", "Reserve", 76.0), Window("session", "5 h", 25.0),
        ))
        assertEquals(listOf("week", "reserve", "session"), provider.glanceWindows().map { it.id })
        val hero = provider.glanceWindows().first()
        assertEquals(24, hero.shown(DisplayMode.REMAINING))
        assertEquals(76, hero.shown(DisplayMode.USED))
        assertEquals(Severity.WARN, hero.severity)
        assertTrue(Provider(windows = listOf(Window("bad", "Bad", 101.0))).glanceWindows().isEmpty())
    }

    @Test fun globalHeroUsesProviderOrderAndDoesNotRequireSession() {
        val status = StatusResponse(collectedAt = "2026-10-21T12:00:00Z", providers = linkedMapOf(
            "codex" to Provider(windows = listOf(Window("reserve", "Reserve", 76.0))),
            "claude" to Provider(windows = listOf(Window("week", "Week", 76.0))),
        ))
        val snapshot = Snapshot(status, 0)
        assertEquals("Claude" to "week", snapshot.glanceHero()?.let { it.first to it.second.id })
        assertEquals("reserve", Snapshot(status.copy(providers = status.providers - "claude"), 0).glanceHero()?.second?.id)
        assertNull(Snapshot(status.copy(providers = emptyMap()), 0).glanceHero())
    }

    @Test fun labelsAndResetDurationsStayExplicit() {
        assertEquals("week", Window("week", "  ", 76.0).glanceLabel())
        assertEquals("Reset time unknown", formatResetsIn(null))
        assertEquals("Reset due", formatResetsIn(Duration.ZERO))
        assertEquals("Resets in 1 min", formatResetsIn(Duration.ofSeconds(1)))
        assertEquals("Resets in 2 h 10 min", formatResetsIn(Duration.ofMinutes(130)))
        assertEquals("Resets in 2 d 14 h", formatResetsIn(Duration.ofHours(62)))
    }

    @Test fun providerFreshnessExpiresEvenWhenTheSnapshotWasRecentlyReceived() {
        val provider = Json.decodeFromString<Provider>("""{"measured_at":"2026-09-11T10:00:00Z"}""")
        val measured = java.time.Instant.parse(provider.measuredAt).toEpochMilli()
        assertFalse(provider.isStaleAt(measured + 600_000))
        assertTrue(provider.isStaleAt(measured + 600_001))
        assertTrue(provider.copy(stale = true).isStaleAt(measured))
    }
}
