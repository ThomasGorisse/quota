package com.gorisse.thomas.quota.ui

import com.gorisse.thomas.quota.data.Provider
import com.gorisse.thomas.quota.data.Severity
import com.gorisse.thomas.quota.data.Snapshot
import com.gorisse.thomas.quota.data.Window
import java.time.Duration

fun formatAge(seconds: Long): String = when {
    seconds < 60 -> "${seconds.coerceAtLeast(0)} s ago"
    seconds < 3_600 -> "${seconds / 60} min ago"
    seconds < 86_400 -> "${seconds / 3_600} h ago"
    else -> "${seconds / 86_400} d ago"
}

/** Identical precision on every surface; a reset never implies a refreshed quota. */
fun formatResetsIn(duration: Duration?): String {
    if (duration == null) return "Reset time unknown"
    if (duration.isZero || duration.isNegative) return "Reset due"
    return "Resets in ${formatResetsInShort(duration)}"
}

fun formatResetsInShort(duration: Duration?): String {
    if (duration == null) return "Reset time unknown"
    if (duration.isZero || duration.isNegative) return "Reset due"
    val minutes = kotlin.math.ceil(duration.toMillis() / 60000.0).toLong()
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "${minutes / 1440} d ${(minutes % 1440) / 60} h"
    }
}

fun Severity.glanceLabel(): String = when (this) {
    Severity.OK -> "Ready"
    Severity.WARN -> "Running low"
    Severity.CRITICAL -> "Nearly empty"
    Severity.STALE -> "Out of date"
}

/** Kotlin's stable sort retains payload order on ties. Never prefer a window id. */
fun Provider.glanceWindows(): List<Window> = windows.filter { it.percent.isFinite() && it.percent in 0.0..100.0 }.sortedByDescending { it.percent }

fun Snapshot.glanceHero(): Pair<String, Window>? = orderedProviders()
    .mapNotNull { (name, provider) -> provider.glanceWindows().firstOrNull()?.let { name to it } }
    .maxByOrNull { it.second.percent }

fun providerSubtitle(name: String, plan: String?): String {
    val raw = plan.orEmpty()
    val title = raw.replaceFirstChar(Char::uppercase)
    return if (name.equals("codex", ignoreCase = true)) {
        if (raw.startsWith("ChatGPT", ignoreCase = true)) raw else "ChatGPT $title".trim()
    } else {
        listOf(if (name.equals("claude", ignoreCase = true)) "Anthropic" else "Google", title)
            .filter { it.isNotEmpty() }.joinToString(" · ")
    }
}
