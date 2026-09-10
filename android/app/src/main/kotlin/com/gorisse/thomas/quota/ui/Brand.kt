package com.gorisse.thomas.quota.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorisse.thomas.quota.R
import com.gorisse.thomas.quota.data.ProviderId
import com.gorisse.thomas.quota.ui.theme.accent
import com.gorisse.thomas.quota.ui.theme.accentBrush

@Composable
fun QuotaWordmark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(24.dp)) {
            drawArc(ink, 20f, 290f, false, Offset(3.dp.toPx(), 3.dp.toPx()),
                androidx.compose.ui.geometry.Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        Text("Quota", style = MaterialTheme.typography.headlineSmall, letterSpacing = (-1).sp)
    }
}

/**
 * The providers' real marks: `docs/brand/claude.svg`, `openai.svg` and `gemini.svg`,
 * converted to 24 × 24 vector drawables inside a rounded square of their own accent.
 *
 * Claude and Codex are single-colour marks and take the accent as a tint. The Gemini mark
 * carries the Google sweep in the drawable itself, so it is drawn untinted and its tile
 * takes the same sweep rather than a flat wash.
 */
@Composable
fun ProviderMark(id: ProviderId?, modifier: Modifier = Modifier, size: Dp = 32.dp, stale: Boolean = false) {
    val fallback = MaterialTheme.colorScheme.onSurfaceVariant
    val color = if (stale) com.gorisse.thomas.quota.ui.theme.QuotaColors.Stale else id?.accent() ?: fallback
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = if (stale) androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.12f))
                else id?.accentBrush(0.12f) ?: androidx.compose.ui.graphics.SolidColor(fallback.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(size / 4),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val mark = when (id) {
            ProviderId.CODEX -> R.drawable.ic_brand_openai
            ProviderId.GEMINI -> R.drawable.ic_brand_gemini
            else -> R.drawable.ic_brand_claude
        }
        if (id == ProviderId.GEMINI && !stale) {
            androidx.compose.foundation.Image(
                painter = painterResource(mark),
                contentDescription = null,
                modifier = Modifier.size(size * 0.58f),
            )
        } else {
            Icon(
                painter = painterResource(mark),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size * 0.58f),
            )
        }
    }
}
