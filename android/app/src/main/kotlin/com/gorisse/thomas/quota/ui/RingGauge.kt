package com.gorisse.thomas.quota.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorisse.thomas.quota.data.DisplayMode
import com.gorisse.thomas.quota.data.Severity
import com.gorisse.thomas.quota.data.Window
import com.gorisse.thomas.quota.ui.theme.Dimens
import com.gorisse.thomas.quota.ui.theme.QuotaColors
import com.gorisse.thomas.quota.ui.theme.color
import java.time.Duration

/**
 * The gauge of DESIGN.md: a ring whose swept part is what is **left**, that percentage in
 * the centre, the window label and its reset time below. In [DisplayMode.USED] the same
 * ring grows with usage and prints the spent percentage instead — the colour does not move,
 * severity always runs on the used percentage.
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
    showDetails: Boolean = true,
    mode: DisplayMode = DisplayMode.REMAINING,
) {
    val severity = if (stale) Severity.STALE else window.severity
    val color = severity.color
    val word = if (mode.isUsed) "used" else "left"
    val sweep by animateFloatAsState(
        targetValue = window.fraction(mode),
        animationSpec = tween(durationMillis = 400),
        label = "ring-${window.id}",
    )

    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription =
                "${window.glanceLabel()}: ${window.shown(mode)} percent $word, ${formatResetsIn(resetsIn)}${if (stale) ", out of date" else ""}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.Grid),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Ring(
                fraction = sweep,
                color = color,
                diameter = diameter,
                stroke = if (showDetails) Dimens.RingStroke else 8.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${window.shown(mode)}%",
                    style = MaterialTheme.typography.displayMedium,
                    fontSize = (diameter.value * 0.29f).sp,
                    lineHeight = (diameter.value * 0.32f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (stale) QuotaColors.Stale else MaterialTheme.colorScheme.onSurface,
                )
                // The mode has to be readable at a glance, not guessed from the number.
                Text(
                    text = word,
                    fontSize = (diameter.value * 0.135f).sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDetails) Text(
            text = window.glanceLabel(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showDetails) Text(
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
    val track = if (isSystemInDarkTheme()) QuotaColors.TrackDark else QuotaColors.TrackLight
    Canvas(modifier = Modifier.size(diameter)) {
        val inset = strokePx / 2f
        val arcSize = androidx.compose.ui.geometry.Size(
            size.width - strokePx,
            size.height - strokePx,
        )
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(
            color = track,
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
