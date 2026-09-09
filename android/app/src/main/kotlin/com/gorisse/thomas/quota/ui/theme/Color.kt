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
    val BackgroundDark = Color(0xFF0B0F14)
    val BackgroundLight = Color(0xFFF6F7F9)
    val SurfaceDark = Color(0xFF141A22)
    val SurfaceLight = Color(0xFFFFFFFF)
    val TextPrimaryDark = Color(0xFFF3F4F6)
    val TextPrimaryLight = Color(0xFF111827)
    val TextSecondaryDark = Color(0xFF9AA4B2)
    val TextSecondaryLight = Color(0xFF6B7280)

    val Ok = Color(0xFF22C55E)
    val Warn = Color(0xFFF59E0B)
    val Critical = Color(0xFFEF4444)
    val Stale = Color(0xFF6B7280)

    val Claude = Color(0xFFD97757)
    val Codex = Color(0xFF10A37F)

    /** Ring track: the value colour at 15 % opacity, per DESIGN.md. */
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

val ProviderId.accent: Color
    get() = when (this) {
        ProviderId.CLAUDE -> QuotaColors.Claude
        ProviderId.CODEX -> QuotaColors.Codex
    }
