package com.gorisse.thomas.quota.data

import com.gorisse.thomas.quota.ui.glanceWindows
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

    @Test fun providerFreshnessExpiresEvenWhenTheSnapshotWasRecentlyReceived() {
        val provider = Json.decodeFromString<Provider>("""{"measured_at":"2026-09-11T10:00:00Z"}""")
        val measured = java.time.Instant.parse(provider.measuredAt).toEpochMilli()
        assertFalse(provider.isStaleAt(measured + 600_000))
        assertTrue(provider.isStaleAt(measured + 600_001))
        assertTrue(provider.copy(stale = true).isStaleAt(measured))
    }
}
