package com.gorisse.thomas.quota.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Radius and spacing tokens of DESIGN.md; nothing in the UI hardcodes a dp beside these. */
object Dimens {
    val CardRadius: Dp = 20.dp
    val ChipRadius: Dp = 12.dp
    val Grid: Dp = 8.dp
    val RingStroke: Dp = 12.dp
}

private val DarkScheme = darkColorScheme(
    background = QuotaColors.BackgroundDark,
    surface = QuotaColors.SurfaceDark,
    onBackground = QuotaColors.TextPrimaryDark,
    onSurface = QuotaColors.TextPrimaryDark,
    onSurfaceVariant = QuotaColors.TextSecondaryDark,
    primary = QuotaColors.Ok,
    onPrimary = QuotaColors.BackgroundDark,
)

private val LightScheme = lightColorScheme(
    background = QuotaColors.BackgroundLight,
    surface = QuotaColors.SurfaceLight,
    onBackground = QuotaColors.TextPrimaryLight,
    onSurface = QuotaColors.TextPrimaryLight,
    onSurfaceVariant = QuotaColors.TextSecondaryLight,
    primary = QuotaColors.Ok,
    onPrimary = QuotaColors.BackgroundLight,
)

/** Tabular figures everywhere: a gauge that reflows as digits change is unreadable. */
private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

private val QuotaTypography = Typography().run {
    copy(
        displayMedium = displayMedium.merge(TabularNumbers).copy(fontWeight = FontWeight.W600),
        headlineSmall = headlineSmall.merge(TabularNumbers).copy(fontWeight = FontWeight.W600),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.W600),
        bodySmall = bodySmall.merge(TabularNumbers),
        labelMedium = labelMedium.merge(TabularNumbers),
    )
}

@Composable
fun QuotaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = QuotaTypography,
        content = content,
    )
}
