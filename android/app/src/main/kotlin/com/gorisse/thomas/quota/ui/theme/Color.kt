package com.gorisse.thomas.quota.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.gorisse.thomas.quota.data.ProviderId
import com.gorisse.thomas.quota.data.Severity

/**
 * The palette of `docs/DESIGN.md`, and the only place colours are written down.
 * Dynamic colour is deliberately off: severity has to mean the same thing on every device.
 */
object QuotaColors {
    val BackgroundDark = Color(0xFF101713)
    val BackgroundLight = Color(0xFFF7F7F2)
    val SurfaceDark = Color(0xFF1A231E)
    val SurfaceLight = Color(0xFFFFFFFF)
    val TextPrimaryDark = Color(0xFFF3F4F6)
    val TextPrimaryLight = Color(0xFF18221C)
    val TextSecondaryDark = Color(0xFF9AA4B2)
    val TextSecondaryLight = Color(0xFF6B7280)

    val Ok = Color(0xFF22C55E)
    val Warn = Color(0xFFF59E0B)
    val Critical = Color(0xFFEF4444)
    val Stale = Color(0xFF6B7280)

    val Claude = Color(0xFFD97757)

    /**
     * OpenAI retired the ChatGPT green in its 2025 rebrand and is monochrome now. Their black
     * would vanish on [BackgroundDark], so each theme takes its own end of the pair.
     */
    val CodexDark = Color(0xFFECECEC)
    val CodexLight = Color(0xFF0D0D0D)

    /** The Gemini mark is a blue → violet → pink sweep, drawn as one wherever it is cheap. */
    val GeminiStart = Color(0xFF4285F4)
    val GeminiMid = Color(0xFF9B72CB)
    val GeminiEnd = Color(0xFFD96570)

    /** The flat fallback, for a history stroke or anything else that takes one colour. */
    val Gemini = Color(0xFF8E75B2)

    /**
     * Ring track. It used to be the value colour at 15 % opacity, which left the empty part
     * of a nearly-full ring invisible — you could not read the proportion. It is now a
     * neutral grey with real contrast against both surfaces, like the web dashboard.
     */
    val TrackDark = Color(0xFF374151)
    val TrackLight = Color(0xFFD8DCE3)

    /**
     * The widget track. A widget ring is a baked bitmap the launcher never redraws on a
     * dark/light flip, so it cannot pick between [TrackDark] and [TrackLight]: this one
     * neutral is translucent and composites to roughly either over the matching surface
     * (#D2D8E1 over white, #4A5461 over [SurfaceDark]).
     */
    val TrackNeutral = Color(0x6B94A3B8)

    @Deprecated("The track is a solid neutral now, not a tint of the value colour.")
    const val TrackAlpha = 0.15f
}

val Severity.color: Color
    get() = when (this) {
        Severity.OK -> QuotaColors.Ok
        Severity.WARN -> QuotaColors.Warn
        Severity.CRITICAL -> QuotaColors.Critical
        Severity.STALE -> QuotaColors.Stale
    }

/** The same severity colour for the widget, which draws with `android.graphics.Paint`. */
val Severity.argb: Int get() = color.toArgb()

/** The provider accent for one theme. Only Codex differs between the two. */
fun ProviderId.accent(dark: Boolean): Color = when (this) {
    ProviderId.CLAUDE -> QuotaColors.Claude
    ProviderId.CODEX -> if (dark) QuotaColors.CodexDark else QuotaColors.CodexLight
    ProviderId.GEMINI -> QuotaColors.Gemini
}

@androidx.compose.runtime.Composable
fun ProviderId.accent(): Color = accent(androidx.compose.foundation.isSystemInDarkTheme())

/**
 * What fills the mark's rounded square: one flat tint, except Gemini, whose brand is the
 * sweep itself — the same choice the web dashboard makes with a conic gradient.
 */
@androidx.compose.runtime.Composable
fun ProviderId.accentBrush(alpha: Float): androidx.compose.ui.graphics.Brush =
    if (this == ProviderId.GEMINI) {
        androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(
                QuotaColors.GeminiStart.copy(alpha = alpha),
                QuotaColors.GeminiMid.copy(alpha = alpha),
                QuotaColors.GeminiEnd.copy(alpha = alpha),
                QuotaColors.GeminiStart.copy(alpha = alpha),
            ),
        )
    } else {
        androidx.compose.ui.graphics.SolidColor(accent().copy(alpha = alpha))
    }

/**
 * Keep the semantic hues, with at least 4.5:1 text contrast on the surfaces and on the pill's
 * tint: darker shades in light, and in dark the token unless it falls short. Same shades as iOS.
 */
@androidx.compose.runtime.Composable
fun Severity.textColor(): Color = if (androidx.compose.foundation.isSystemInDarkTheme()) when (this) {
    Severity.OK, Severity.WARN -> color
    Severity.CRITICAL -> Color(0xFFF87171)
    Severity.STALE -> Color(0xFF9CA3AF)
} else when (this) {
    Severity.OK -> Color(0xFF166534)
    Severity.WARN -> Color(0xFF92400E)
    Severity.CRITICAL -> Color(0xFFB91C1C)
    Severity.STALE -> Color(0xFF4B5563)
}
