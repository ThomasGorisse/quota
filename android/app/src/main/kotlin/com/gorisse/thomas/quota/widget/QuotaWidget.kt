package com.gorisse.thomas.quota.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Box
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
import com.gorisse.thomas.quota.MainActivity
import com.gorisse.thomas.quota.R
import com.gorisse.thomas.quota.data.DisplayMode
import com.gorisse.thomas.quota.data.ProviderId
import com.gorisse.thomas.quota.data.Severity
import com.gorisse.thomas.quota.data.Snapshot
import com.gorisse.thomas.quota.data.StatusRepository
import com.gorisse.thomas.quota.data.Window
import com.gorisse.thomas.quota.ui.formatResetsIn
import com.gorisse.thomas.quota.ui.glanceHero
import com.gorisse.thomas.quota.ui.glanceWindows
import com.gorisse.thomas.quota.ui.glanceLabel
import com.gorisse.thomas.quota.ui.providerSubtitle
import com.gorisse.thomas.quota.ui.theme.QuotaColors
import com.gorisse.thomas.quota.ui.theme.accent
import com.gorisse.thomas.quota.ui.theme.argb

/**
 * Two sizes of the same glance: one overall hero, with provider detail on the large tile. The widget never fetches — [RefreshWorker] does, then calls `updateAll`.
 */
class QuotaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = StatusRepository.get(context)
        val snapshot = repository.snapshot.value
        // The widget never asks the server anything: it prints the last cached choice.
        val mode = repository.displayMode.value
        // Sample data has to be legible on the home screen too: a widget showing invented
        // numbers with nothing saying so is indistinguishable from a real one.
        val demo = repository.isDemo
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
                        LargeContent(snapshot, mode, demo)
                    } else {
                        SmallContent(snapshot, mode, demo)
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

/** One overall hero, even with two providers. A tiny host loses the ring before the unit. */
@Composable
private fun SmallContent(snapshot: Snapshot, mode: DisplayMode, demo: Boolean = false) {
    val hero = snapshot.glanceHero()
    if (hero == null) {
        Text("No data", style = TextStyle(color = secondaryText(), fontSize = 12.sp))
        return
    }
    val (name, window) = hero
    val stale = snapshot.isStale() || snapshot.orderedProviders().firstOrNull { it.first == hero?.first }?.second?.isStaleAt() == true
    val size = LocalSize.current
    val diameter = minOf(size.height.value - 8, size.width.value * .36f).toInt().coerceIn(24, 64)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        if (diameter >= 48) {
            MiniRing(window, stale, diameter, mode)
        } else {
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Text("${window.shown(mode)}%", style = TextStyle(color = primaryText(),
                    fontSize = 22.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(if (mode.isUsed) "used" else "left", style = TextStyle(color = secondaryText(),
                    fontSize = 10.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.size(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                ProviderMark(ProviderId.of(name.lowercase()), sizeDp = 10, stale = stale)
                Spacer(GlanceModifier.size(4.dp))
                Text("$name · ${window.label.ifEmpty { window.id }}",
                    style = TextStyle(color = primaryText(), fontSize = 11.sp,
                        fontWeight = FontWeight.Medium), maxLines = 1)
            }
            val reset = if (stale) "Out of date" else formatResetsIn(window.resetsIn())
            if (demo) Text("Sample data", style = TextStyle(color = secondaryText(), fontSize = 10.sp), maxLines = 1)
            Text(reset, style = TextStyle(color = secondaryText(), fontSize = 10.sp), maxLines = 2)
        }
    }
}

/** One overall hero plus plain provider headlines; no secondary rings. */
@Composable
private fun LargeContent(snapshot: Snapshot, mode: DisplayMode, demo: Boolean = false) {
    val hero = snapshot.glanceHero()
    val stale = snapshot.isStale() || snapshot.orderedProviders().firstOrNull { it.first == hero?.first }?.second?.isStaleAt() == true
    if (hero == null) {
        Text("Quota · No data", style = TextStyle(color = secondaryText(), fontSize = 14.sp))
        return
    }
    val (name, window) = hero
    val diameter = minOf(88f, LocalSize.current.height.value - 60,
        LocalSize.current.width.value * .32f).toInt().coerceAtLeast(48)
    Column(GlanceModifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            SeverityDot(if (stale) Severity.STALE else window.severity)
            Spacer(GlanceModifier.size(8.dp))
            Text("${if (demo) "Sample data" else "Quota"} · ${if (stale) Severity.STALE.glanceLabel() else window.severity.glanceLabel()}",
                style = TextStyle(color = primaryText(), fontSize = 12.sp,
                    fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Spacer(GlanceModifier.size(8.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                MiniRing(window, stale, diameter, mode)
                Spacer(GlanceModifier.size(4.dp))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    ProviderMark(ProviderId.of(name.lowercase()), sizeDp = 10, stale = stale)
                    Spacer(GlanceModifier.size(4.dp))
                    Text("$name · ${window.label.ifEmpty { window.id }}",
                        style = TextStyle(color = primaryText(), fontSize = 10.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.size(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(providerSubtitle(name, snapshot.orderedProviders().firstOrNull { it.first == name }?.second?.plan),
                    style = TextStyle(color = secondaryText(), fontSize = 10.sp), maxLines = 1)
                Text(formatResetsIn(window.resetsIn()),
                    style = TextStyle(color = secondaryText(), fontSize = 11.sp), maxLines = 2)
                Spacer(GlanceModifier.size(8.dp))
                snapshot.orderedProviders().filter { it.first != name }.forEach { (providerName, provider) ->
                    provider.glanceWindows().firstOrNull()?.let { limit ->
                        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                            ProviderMark(ProviderId.of(providerName.lowercase()), stale = snapshot.isStale() || provider.isStaleAt())
                            Spacer(GlanceModifier.size(4.dp))
                            Text("$providerName · ${limit.label.ifEmpty { limit.id }} ${limit.shown(mode)}% ${if (mode.isUsed) "used" else "left"}${if (snapshot.isStale() || provider.isStaleAt()) " · Out of date" else ""}",
                                style = TextStyle(color = primaryText(), fontSize = 11.sp), maxLines = 2)
                        }
                        Spacer(GlanceModifier.size(4.dp))
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
                trackArgb = 0,
            ),
        ),
        contentDescription = severity.glanceLabel(),
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
    val bitmap = RingBitmap.render(
        sizePx = (diameterDp * density).toInt(),
        strokePx = diameterDp * density * 0.11f,
        fraction = window?.fraction(mode) ?: 0f,
        colorArgb = severity.argb,
    )
    val shown = window?.shown(mode)
    Box(
        modifier = GlanceModifier.size(diameterDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = window?.let {
                "${it.label}: ${it.shown(mode)} percent ${if (mode.isUsed) "used" else "left"}" +
                    if (stale) ", out of date" else ""
            } ?: "No usage available",
            modifier = GlanceModifier.size(diameterDp.dp),
        )
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(shown?.let { "$it%" } ?: "—",
                style = TextStyle(color = primaryText(), fontSize = (diameterDp * .29f).sp,
                    fontWeight = FontWeight.Medium), maxLines = 1)
            Text(if (mode.isUsed) "used" else "left",
                style = TextStyle(color = secondaryText(), fontSize = (diameterDp * .14f).sp), maxLines = 1)
        }
    }
}

/**
 * The real brand mark, the same one the app shows. Claude and Codex are tinted with their
 * accent — a day/night pair for Codex, whose brand is monochrome and would otherwise vanish
 * against one of the two surfaces. Gemini uses the flat widget tint.
 */
@Composable
private fun ProviderMark(id: ProviderId?, sizeDp: Int = 14, stale: Boolean = false) {
    if (id == null) return

    Image(
        provider = ImageProvider(
            when (id) {
                ProviderId.CODEX -> R.drawable.ic_brand_openai
                ProviderId.GEMINI -> R.drawable.ic_brand_gemini
                else -> R.drawable.ic_brand_claude
            },
        ),
        contentDescription = null,
        colorFilter = ColorFilter.tint(
            DayNightColorProvider(
                day = if (stale) QuotaColors.Stale else id.accent(dark = false),
                night = if (stale) QuotaColors.Stale else id.accent(dark = true)),
        ),
        modifier = GlanceModifier.size(sizeDp.dp),
    )
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
