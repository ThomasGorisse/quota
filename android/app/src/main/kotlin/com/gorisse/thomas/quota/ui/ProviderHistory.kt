package com.gorisse.thomas.quota.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.quota.data.DisplayMode
import com.gorisse.thomas.quota.data.ProviderId
import com.gorisse.thomas.quota.ui.theme.QuotaColors
import com.gorisse.thomas.quota.ui.theme.accent
import kotlinx.serialization.json.*
import java.time.Instant

private data class HistoryPoint(val time: Long, val session: Float?, val weekly: Float?)

@Composable
fun ProviderHistory(
    provider: String, history: List<JsonElement>, stale: Boolean, now: Instant,
    mode: DisplayMode = DisplayMode.REMAINING,
) {
    val end = now.toEpochMilli()
    val start = end - 86_400_000L
    val points = remember(provider, history, mode) {
        history.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val time = (obj["t"] as? JsonPrimitive)?.contentOrNull
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: return@mapNotNull null
            fun shown(window: String) = (obj["$provider.$window"] as? JsonPrimitive)
                ?.floatOrNull?.takeIf { it.isFinite() }
                ?.let { (if (mode.isUsed) it else 100f - it).coerceIn(0f, 100f) }
            HistoryPoint(time, shown("session"), shown("weekly"))
        }.sortedBy { it.time }
    }.filter { it.time in start..end }
    val hasData = points.count { it.session != null } >= 2 || points.count { it.weekly != null } >= 2
    val accent = if (stale) QuotaColors.Stale else ProviderId.of(provider)?.accent ?: QuotaColors.Stale
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = line)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Last 24 hours", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasData) Canvas(Modifier.weight(1f).height(32.dp).semantics {
                contentDescription = "Quota ${if (mode.isUsed) "used" else "remaining"} over the last 24 hours. " +
                    "Solid line: 5 hour window. Dashed line: week."
            }) {
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                listOf(false, true).forEach { weekly ->
                    val path = Path()
                    var connected = false
                    points.forEach { point ->
                        val value = if (weekly) point.weekly else point.session
                        if (value == null) connected = false else {
                            val x = (point.time - start).toFloat() / 86_400_000f * size.width
                            val y = 2.dp.toPx() + (1 - value / 100f) * (size.height - 4.dp.toPx())
                            if (connected) path.lineTo(x, y) else path.moveTo(x, y)
                            connected = true
                        }
                    }
                    drawPath(path, accent.copy(alpha = if (weekly) .6f else 1f), style = Stroke(
                        width = 1.5.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = if (weekly) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null))
                }
            } else Text("Waiting for more updates", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
