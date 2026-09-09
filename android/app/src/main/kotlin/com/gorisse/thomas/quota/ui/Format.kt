package com.gorisse.thomas.quota.ui

import java.time.Duration

/** "updated 42 s ago" / "updated 3 min ago" / "updated 2 h ago" / "updated 3 d ago". */
fun formatAge(seconds: Long): String = when {
    seconds < 60 -> "${seconds.coerceAtLeast(0)} s ago"
    seconds < 3_600 -> "${seconds / 60} min ago"
    seconds < 86_400 -> "${seconds / 3_600} h ago"
    else -> "${seconds / 86_400} d ago"
}

/**
 * "resets in 2 h 10" — the exact shape asked for in DESIGN.md — degrading to minutes below
 * the hour and to days above the day, because "resets in 62 h 10" reads as nothing.
 */
fun formatResetsIn(duration: Duration?): String {
    if (duration == null) return "reset time unknown"
    val minutes = duration.toMinutes()
    return when {
        minutes < 1 -> "resets now"
        minutes < 60 -> "resets in $minutes min"
        minutes < 24 * 60 -> "resets in ${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
        else -> "resets in ${minutes / (24 * 60)} d ${(minutes / 60) % 24} h"
    }
}

/** Compact form for the widget, where a cell is 110 dp wide: "2h10", "37m", "3d". */
fun formatResetsInShort(duration: Duration?): String {
    if (duration == null) return "--"
    val minutes = duration.toMinutes()
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"
        else -> "${minutes / (24 * 60)}d"
    }
}
