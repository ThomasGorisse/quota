package com.gorisse.thomas.quota.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.gorisse.thomas.quota.ui.theme.QuotaColors
import androidx.compose.ui.graphics.toArgb

/**
 * Glance has no arc primitive, so every widget ring is a bitmap drawn here and handed to
 * `Image(ImageProvider(bitmap))`. The bitmap is sized in pixels for the cell it will occupy;
 * the caller converts dp with the widget's density.
 *
 * **Nothing drawn here may depend on the day/night theme.** A bitmap is baked into the
 * `RemoteViews` the launcher keeps, and the launcher does not redraw it when the system
 * flips dark/light: the card background is a `ColorProvider` and flips instantly, so any
 * theme-dependent pixel in the bitmap ends up on the wrong background (white numbers on a
 * white card). The percentage is therefore a Glance `Text` on top of this bitmap, not a
 * `drawText`, and the track is a translucent neutral that composites correctly over both
 * surfaces instead of a colour picked per theme.
 */
object RingBitmap {

    /**
     * @param sizePx outer diameter in pixels
     * @param strokePx ring thickness in pixels (8 dp per DESIGN.md, converted by the caller)
     * @param fraction how much is **left**, 0..1
     * @param colorArgb severity colour, already resolved (grey when stale) — a semantic hue
     *   that means the same thing in both themes, so it is safe to bake
     * @param trackArgb the empty part of the ring; the default is translucent on purpose
     */
    fun render(
        sizePx: Int,
        strokePx: Float,
        fraction: Float,
        colorArgb: Int,
        trackArgb: Int = QuotaColors.TrackNeutral.toArgb(),
    ): Bitmap {
        val size = sizePx.coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokePx / 2f
        val bounds = RectF(inset, inset, size - inset, size - inset)

        if (trackArgb != 0) {
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                color = trackArgb
            }
            canvas.drawArc(bounds, 0f, 360f, false, track)
        }

        if (fraction > 0f) {
            val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                color = colorArgb
            }
            canvas.drawArc(bounds, -90f, 360f * fraction.coerceIn(0f, 1f), false, value)
        }
        return bitmap
    }
}
