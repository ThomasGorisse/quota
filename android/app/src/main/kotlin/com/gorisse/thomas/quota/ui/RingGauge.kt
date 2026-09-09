package com.gorisse.thomas.quota.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorisse.thomas.quota.data.Severity
import com.gorisse.thomas.quota.data.Window
import com.gorisse.thomas.quota.ui.theme.Dimens
import com.gorisse.thomas.quota.ui.theme.QuotaColors
import com.gorisse.thomas.quota.ui.theme.color
import java.time.Duration

/**
 * The gauge of DESIGN.md: a ring whose swept part is what is **left**, the remaining
 * percentage in the centre, the window label and its reset time below.
 *
 * The sweep is the only thing that animates, 400 ms ease-out, on value change only.
 */
@Composable
fun RingGauge(
    window: Window,
    stale: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 112.dp,
    resetsIn: Duration? = window.resetsIn(),
) {
    val severity = if (stale) Severity.STALE else window.severity
    val color = severity.color
    val sweep by animateFloatAsState(
        targetValue = window.remaining / 100f,
        animationSpec = tween(durationMillis = 400),
        label = "ring-${window.id}",
    )

    Column(
        modifier = modifier.semantics {
            contentDescription =
                "${window.label}: ${window.remaining} percent left, ${formatResetsIn(resetsIn)}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.Grid),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Ring(
                fraction = sweep,
                color = color,
                diameter = diameter,
                stroke = Dimens.RingStroke,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${window.remaining}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = (diameter.value * 0.30f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "% left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = window.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatResetsIn(resetsIn),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Ring(fraction: Float, color: Color, diameter: Dp, stroke: Dp) {
    val strokePx = with(LocalDensity.current) { stroke.toPx() }
    Canvas(modifier = Modifier.size(diameter)) {
        val inset = strokePx / 2f
        val arcSize = androidx.compose.ui.geometry.Size(
            size.width - strokePx,
            size.height - strokePx,
        )
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(
            color = color.copy(alpha = QuotaColors.TrackAlpha),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx),
        )
        if (fraction > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}
