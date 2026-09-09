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
import com.gorisse.thomas.quota.ui.theme.accent
import com.gorisse.thomas.quota.ui.theme.argb
import com.gorisse.thomas.quota.ui.theme.color

/**
 * Two layouts, chosen by cell size: 2×1 shows the two 5 h rings, 4×2 shows every window with
 * its label. The widget never fetches — [RefreshWorker] does, then calls `updateAll`.
 */
class QuotaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = StatusRepository.get(context).snapshot.value
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val wide = size.width >= LARGE.width - 8.dp && size.height >= LARGE.height - 8.dp
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(surface())
                        .cornerRadius(20.dp)
                        .padding(if (wide) 12.dp else 6.dp)
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
        val LARGE = DpSize(250.dp, 110.dp)
    }
}

/** 2×1: Claude 5 h and Codex 5 h, nothing else — that is all that fits and all that matters. */
@Composable
private fun SmallContent(snapshot: Snapshot) {
    val stale = snapshot.isStale()
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        ProviderId.entries.forEachIndexed { index, providerId ->
            val window = snapshot.status.providers[providerId.key]?.sessionWindow()
            if (index > 0) Spacer(GlanceModifier.size(10.dp))
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                MiniRing(window = window, stale = stale, diameterDp = 30)
                Text(
                    text = providerId.displayName.take(6),
                    style = TextStyle(color = secondaryText(), fontSize = 9.sp),
                )
            }
        }
    }
}

/** 4×2: the severity dot, the freshness, then every window of every provider with labels. */
@Composable
private fun LargeContent(snapshot: Snapshot) {
    val stale = snapshot.isStale()
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = "Quota",
                style = TextStyle(
                    color = primaryText(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.size(6.dp))
            Text(
                text = formatAge(snapshot.ageSeconds()),
                style = TextStyle(
                    color = ColorProvider(snapshot.severity().color),
                    fontSize = 11.sp,
                ),
            )
        }
        Spacer(GlanceModifier.size(6.dp))
        Row(verticalAlignment = Alignment.Vertical.Top) {
            snapshot.orderedProviders().forEachIndexed { index, (name, provider) ->
                if (index > 0) Spacer(GlanceModifier.size(12.dp))
                Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                    Text(
                        text = name,
                        style = TextStyle(
                            color = ColorProvider(
                                ProviderId.of(name.lowercase())?.accent
                                    ?: QuotaColors.TextSecondaryDark,
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(GlanceModifier.size(4.dp))
                    Row {
                        provider.windows.forEachIndexed { windowIndex, window ->
                            if (windowIndex > 0) Spacer(GlanceModifier.size(6.dp))
                            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                                MiniRing(window = window, stale = stale, diameterDp = 44)
                                Text(
                                    text = window.label,
                                    style = TextStyle(color = primaryText(), fontSize = 9.sp),
                                )
                                Text(
                                    text = formatResetsInShort(window.resetsIn()),
                                    style = TextStyle(color = secondaryText(), fontSize = 9.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
        // 8 dp stroke on the phone reads as a hairline at 30 dp, so the widget ring keeps a
        // constant *proportion* instead: 8/44 of the diameter, the DESIGN.md ratio at 4x2.
        strokePx = diameterDp * density * (8f / 44f),
        fraction = (window?.remaining ?: 0) / 100f,
        colorArgb = severity.argb,
        centerText = window?.remaining?.toString(),
        centerTextColorArgb = if (dark) {
            QuotaColors.TextPrimaryDark.toArgb()
        } else {
            QuotaColors.TextPrimaryLight.toArgb()
        },
    )
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = window?.let { "${it.label}: ${it.remaining} percent left" },
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
