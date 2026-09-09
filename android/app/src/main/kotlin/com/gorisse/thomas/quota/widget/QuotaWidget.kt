package com.gorisse.thomas.quota.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
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
import com.gorisse.thomas.quota.R
import com.gorisse.thomas.quota.data.DisplayMode
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

/**
 * Two layouts, chosen by cell size: 2×1 shows the two 5 h rings, 4×2 shows every window with
 * its label. The widget never fetches — [RefreshWorker] does, then calls `updateAll`.
 */
class QuotaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = StatusRepository.get(context)
        val snapshot = repository.snapshot.value
        // The widget never asks the server anything: it prints the last cached choice.
        val mode = repository.displayMode.value
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
                        LargeContent(snapshot, mode)
                    } else {
                        SmallContent(snapshot, mode)
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

/**
 * A 2 x 1 cell is one row high once the launcher's own widget insets are taken out, so the
 * ring and the provider name cannot be stacked — the name was rendering half cut off. The
 * mark carries the identity instead, inline with the ring, which also buys the ring the
 * pixels the name was wasting. The ring is sized from the height that is actually left.
 */
@Composable
private fun SmallContent(snapshot: Snapshot, mode: DisplayMode) {
    val stale = snapshot.isStale()
    val height = LocalSize.current.height.value
    val diameter = (height - 30).toInt().coerceIn(30, 48)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        ProviderId.entries.forEach { id ->
            val window = snapshot.status.providers[id.key]?.sessionWindow()
            Row(
                // Without this the second provider's mark sits closer to the first
                // provider's ring than to its own; the pair stops reading as a pair.
                GlanceModifier.defaultWeight().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                if (height >= 56) {
                    ProviderMark(id, sizeDp = 16)
                    Spacer(GlanceModifier.size(6.dp))
                    MiniRing(window, stale, diameterDp = diameter, mode = mode)
                } else {
                    // Too short for a ring at all: the mark and the number, nothing else.
                    ProviderMark(id, sizeDp = 14)
                    Spacer(GlanceModifier.size(5.dp))
                    Text(
                        window?.let { "${it.shown(mode)}%" } ?: "\u2014",
                        style = TextStyle(color = primaryText(), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Four primary windows stay equal; reserve is a secondary line, as on the dashboard. */
@Composable
private fun LargeContent(snapshot: Snapshot, mode: DisplayMode) {
    val stale = snapshot.isStale()
    // Two rings per provider at most: five rings on a 7 cm cell rendered ~6sp values.
    val columns = snapshot.orderedProviders().maxOfOrNull { (_, p) -> p.widgetWindows(2).size } ?: 1
    // The cell also has to fit a header, a provider name, a label, a reset time and the
    // footer: give the ring what is left, not what the width alone would allow.
    val diameter = minOf(
        (LocalSize.current.width.value - 56) / (columns * 2),
        LocalSize.current.height.value - 100,
    ).toInt().coerceIn(36, 58)
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text("Quota", style = TextStyle(color = primaryText(), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.size(8.dp))
            // "42 s ago · left" says both how fresh it is and which number it is printing,
            // on the one line a 4 x 2 cell can spare.
            Text(
                formatAge(snapshot.ageSeconds()) + " · " + if (mode.isUsed) "used" else "left",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = secondaryText(), fontSize = 11.sp), maxLines = 1,
            )
            SeverityDot(snapshot.severity())
        }
        Spacer(GlanceModifier.size(6.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            snapshot.orderedProviders().forEach { (name, provider) ->
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        ProviderMark(ProviderId.of(name.lowercase()))
                        Spacer(GlanceModifier.size(5.dp))
                        Text(name, style = TextStyle(color = primaryText(), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        provider.plan?.let {
                            Text(" · " + it.replaceFirstChar(Char::uppercase),
                                style = TextStyle(color = secondaryText(), fontSize = 11.sp), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.size(3.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        provider.widgetWindows(2).forEach { window ->
                            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                                MiniRing(window, stale, diameter, mode)
                                Spacer(GlanceModifier.size(2.dp))
                                Text(window.label, style = TextStyle(color = primaryText(), fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium), maxLines = 1)
                                Text(formatResetsInShort(window.resetsIn()),
                                    style = TextStyle(color = secondaryText(), fontSize = 11.sp), maxLines = 1)
                            }
                        }
                    }
                }
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
                trackArgb = 0,
            ),
        ),
        contentDescription = severity.wire,
        modifier = GlanceModifier.size(8.dp),
    )
}

@Composable
private fun MiniRing(window: Window?, stale: Boolean, diameterDp: Int, mode: DisplayMode) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val severity = when {
        window == null || stale -> Severity.STALE
        else -> window.severity
    }
    val dark = isDarkWidget(context)
    val bitmap = RingBitmap.render(
        sizePx = (diameterDp * density).toInt(),
        strokePx = diameterDp * density * 0.11f,
        fraction = window?.fraction(mode) ?: 0f,
        colorArgb = severity.argb,
        centerText = window?.shown(mode)?.toString() ?: "—",
        centerTextColorArgb = if (dark) {
            QuotaColors.TextPrimaryDark.toArgb()
        } else {
            QuotaColors.TextPrimaryLight.toArgb()
        },
        trackArgb = if (dark) QuotaColors.TrackDark.toArgb() else QuotaColors.TrackLight.toArgb(),
    )
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = window?.let {
            "${it.label}: ${it.shown(mode)} percent ${if (mode.isUsed) "used" else "left"}" +
                if (stale) ", out of date" else ""
        } ?: "No usage available",
        modifier = GlanceModifier.size(diameterDp.dp),
    )
}

/** The real brand mark, tinted with the provider accent — the same one the app shows. */
@Composable
private fun ProviderMark(id: ProviderId?, sizeDp: Int = 14) {
    if (id == null) return
    Image(
        provider = ImageProvider(
            if (id == ProviderId.CODEX) R.drawable.ic_brand_openai else R.drawable.ic_brand_claude,
        ),
        contentDescription = null,
        colorFilter = ColorFilter.tint(ColorProvider(id.accent)),
        modifier = GlanceModifier.size(sizeDp.dp),
    )
}

private fun Provider.sessionWindow(): Window? = widgetWindows(1).firstOrNull()

/**
 * The windows worth a ring on a home screen, most relevant first. A provider may report one,
 * two or three of them — the Pro plan reports only `weekly` and `reserve` — so nothing here
 * assumes `session` exists, and anything unknown still gets its turn after the known ids.
 */
private fun Provider.widgetWindows(max: Int): List<Window> {
    val rank = listOf("session", "weekly", "reserve")
    return windows
        .sortedBy { rank.indexOf(it.id).takeIf { i -> i >= 0 } ?: rank.size }
        .take(max)
}

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
