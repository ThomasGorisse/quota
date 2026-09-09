package com.gorisse.thomas.quota.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils

/**
 * Glance has no arc primitive, so every widget ring is a bitmap drawn here and handed to
 * `Image(ImageProvider(bitmap))`. The bitmap is sized in pixels for the cell it will occupy;
 * the caller converts dp with the widget's density.
 */
object RingBitmap {

    /**
     * @param sizePx outer diameter in pixels
     * @param strokePx ring thickness in pixels (8 dp per DESIGN.md, converted by the caller)
     * @param fraction how much is **left**, 0..1
     * @param colorArgb severity colour, already resolved (grey when stale)
     * @param centerText the percentage to print, or null for the smallest cells
     * @param trackArgb the empty part of the ring — a neutral grey, so the proportion reads
     */
    fun render(
        sizePx: Int,
        strokePx: Float,
        fraction: Float,
        colorArgb: Int,
        centerText: String?,
        centerTextColorArgb: Int,
        trackArgb: Int = ColorUtils.setAlphaComponent(colorArgb, 38),
    ): Bitmap {
        val size = sizePx.coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokePx / 2f
        val bounds = RectF(inset, inset, size - inset, size - inset)

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = trackArgb
        }
        canvas.drawArc(bounds, 0f, 360f, false, track)

        if (fraction > 0f) {
            val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                color = colorArgb
            }
            canvas.drawArc(bounds, -90f, 360f * fraction.coerceIn(0f, 1f), false, value)
        }

        if (centerText != null) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = centerTextColorArgb
                textAlign = Paint.Align.LEFT
                textSize = size * 0.40f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val metrics = text.fontMetrics
            val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
            if (centerText.all(Char::isDigit)) {
                val unit = Paint(text).apply {
                    textSize = size * 0.20f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                val width = text.measureText(centerText) + unit.measureText("%")
                val left = (size - width) / 2f
                canvas.drawText(centerText, left, baseline, text)
                canvas.drawText("%", left + text.measureText(centerText), baseline, unit)
            } else {
                canvas.drawText(centerText, (size - text.measureText(centerText)) / 2f, baseline, text)
            }
        }
        return bitmap
    }
}
