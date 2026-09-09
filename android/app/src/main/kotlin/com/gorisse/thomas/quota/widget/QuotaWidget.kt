package com.gorisse.thomas.quota.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gorisse.thomas.quota.MainActivity
import com.gorisse.thomas.quota.data.Provider
import com.gorisse.thomas.quota.data.ProviderId
import com.gorisse.thomas.quota.data.Severity
import com.gorisse.thomas.quota.data.Snapshot
import com.gorisse.thomas.quota.data.StatusRepository
import com.gorisse.thomas.quota.data.Window
import com.gorisse.thomas.quota.ui.formatAge
import com.gorisse.thomas.quota.ui.formatResetsInShort
import com.gorisse.thomas.quota.ui.theme.QuotaColors
import com.gorisse.thomas.quota.ui.theme.argb

/**
 * Two layouts, chosen by cell size: 2×1 shows the two 5 h rings, 4×2 shows every window with
 * its label. The widget never fetches — [RefreshWorker] does, then calls `updateAll`.
 */
class QuotaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = StatusRepository.get(context).snapshot.value
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val wide = size.width >= LARGE.width && size.height >= LARGE.height
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(surface())
                        .cornerRadius(20.dp)
                        .padding(if (wide) 12.dp else 4.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                ) {
                    if (snapshot == null) {
                        Text(
                            text = "Tap to pair Quota",
                            style = TextStyle(color = secondaryText(), fontSize = 12.sp),
                        )
                    } else if (wide) {
                        LargeContent(snapshot)
                    } else {
                        SmallContent(snapshot)
                    }
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 40.dp)
        val LARGE = DpSize(250.dp, 150.dp)
    }
}

/** Use the actual host size: the old 110 × 40 breakpoint shrank even spacious cells. */
@Composable
private fun SmallContent(snapshot: Snapshot) {
    val stale = snapshot.isStale()
    val height = LocalSize.current.height.value
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        ProviderId.entries.forEach { id ->
            val window = snapshot.status.providers[id.key]?.sessionWindow()
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                if (height >= 64) {
                    MiniRing(window, stale, diameterDp = 42)
                    Text(id.displayName, style = TextStyle(color = primaryText(), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                } else {
                    Text(id.displayName, style = TextStyle(color = secondaryText(), fontSize = 11.sp), maxLines = 1)
                    Text(window?.let { "${it.remaining}%" } ?: "—",
                        style = TextStyle(color = primaryText(), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

/** Four primary windows stay equal; reserve is a secondary line, as on the dashboard. */
@Composable
private fun LargeContent(snapshot: Snapshot) {
    val stale = snapshot.isStale()
    val diameter = minOf((LocalSize.current.width.value - 48) / 4, LocalSize.current.height.value - 118)
        .toInt().coerceIn(32, 52)
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text("Quota", style = TextStyle(color = primaryText(), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.size(8.dp))
            Text("Updated " + formatAge(snapshot.ageSeconds()),
                modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = secondaryText(), fontSize = 11.sp), maxLines = 1)
            SeverityDot(snapshot.severity())
        }
        Spacer(GlanceModifier.size(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            snapshot.orderedProviders().forEach { (name, provider) ->
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(name, style = TextStyle(color = primaryText(), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        provider.plan?.let {
                            Text(" · " + it.replaceFirstChar(Char::uppercase),
                                style = TextStyle(color = secondaryText(), fontSize = 11.sp), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.size(4.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        provider.windows.filter { it.id != "reserve" }.take(2).forEach { window ->
                            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                                MiniRing(window, stale, diameter)
                                Text(window.label, style = TextStyle(color = primaryText(), fontSize = 12.sp), maxLines = 1)
                                Text(formatResetsInShort(window.resetsIn()), style = TextStyle(color = secondaryText(), fontSize = 11.sp), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Spacer(GlanceModifier.size(8.dp))
        val reserve = snapshot.status.providers[ProviderId.CODEX.key]?.windows?.firstOrNull { it.id == "reserve" }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text("Remaining", GlanceModifier.defaultWeight(), style = TextStyle(color = secondaryText(), fontSize = 11.sp))
            reserve?.let {
                Text("Reserve ${it.remaining}% · ${formatResetsInShort(it.resetsIn())}",
                    style = TextStyle(color = primaryText(), fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

/** Glance has no circle either, so the severity dot is a filled ring bitmap. */
@Composable
private fun SeverityDot(severity: Severity) {
    val density = LocalContext.current.resources.displayMetrics.density
    val sizePx = (8 * density).toInt()
    Image(
        provider = ImageProvider(
            RingBitmap.render(
                sizePx = sizePx,
                strokePx = sizePx / 2f,
                fraction = 1f,
                colorArgb = severity.argb,
                centerText = null,
                centerTextColorArgb = 0,
            ),
        ),
        contentDescription = severity.wire,
        modifier = GlanceModifier.size(8.dp),
    )
}

@Composable
private fun MiniRing(window: Window?, stale: Boolean, diameterDp: Int) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val severity = when {
        window == null || stale -> Severity.STALE
        else -> window.severity
    }
    val dark = isDarkWidget(context)
    val bitmap = RingBitmap.render(
        sizePx = (diameterDp * density).toInt(),
        strokePx = diameterDp * density * 0.10f,
        fraction = (window?.remaining ?: 0) / 100f,
        colorArgb = severity.argb,
        centerText = window?.remaining?.toString() ?: "—",
        centerTextColorArgb = if (dark) {
            QuotaColors.TextPrimaryDark.toArgb()
        } else {
            QuotaColors.TextPrimaryLight.toArgb()
        },
    )
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = window?.let { "${it.label}: ${it.remaining} percent left${if (stale) ", out of date" else ""}" } ?: "No usage available",
        modifier = GlanceModifier.size(diameterDp.dp),
    )
}

private fun Provider.sessionWindow(): Window? =
    windows.firstOrNull { it.id == "session" } ?: windows.firstOrNull()

private fun isDarkWidget(context: Context): Boolean {
    val mode = context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
}

private fun surface() = DayNightColorProvider(
    day = QuotaColors.SurfaceLight,
    night = QuotaColors.SurfaceDark,
)

private fun primaryText() = DayNightColorProvider(
    day = QuotaColors.TextPrimaryLight,
    night = QuotaColors.TextPrimaryDark,
)

private fun secondaryText() = DayNightColorProvider(
    day = QuotaColors.TextSecondaryLight,
    night = QuotaColors.TextSecondaryDark,
)
