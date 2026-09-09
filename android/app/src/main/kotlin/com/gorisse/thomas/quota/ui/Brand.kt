package com.gorisse.thomas.quota.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorisse.thomas.quota.data.ProviderId
import com.gorisse.thomas.quota.ui.theme.accent
import kotlin.math.cos
import kotlin.math.sin

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

/** Small vector marks, matching the web's sunburst and code brackets. */
@Composable
fun ProviderMark(id: ProviderId?, modifier: Modifier = Modifier) {
    val color = id?.accent ?: MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(32.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(7.dp)) {
        if (id == ProviderId.CLAUDE) {
            repeat(12) { i ->
                val angle = i * Math.PI / 6
                val inner = size.width * 0.12f
                val outer = size.width * if (i % 3 == 0) 0.50f else 0.43f
                drawLine(color,
                    Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                    Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                    strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
            }
        } else {
            val path = Path().apply {
                moveTo(size.width * .30f, size.height * .20f)
                lineTo(size.width * .04f, size.height * .50f)
                lineTo(size.width * .30f, size.height * .80f)
                moveTo(size.width * .70f, size.height * .20f)
                lineTo(size.width * .96f, size.height * .50f)
                lineTo(size.width * .70f, size.height * .80f)
                moveTo(size.width * .60f, 0f)
                lineTo(size.width * .40f, size.height)
            }
            drawPath(path, color, style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
