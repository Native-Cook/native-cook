package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a postmark once per day into a small CPU bitmap (postmark.ts): a double ring with
 * "MAY . 2026" and "STAMP DIARY" around it, the day in the middle and four wavy cancellation lines
 * to its left. Only the alpha is used; strokes carry a baked soft edge (blur sigma 2.2 px) so the
 * shader can thin them (fresh, crisp ink) or thicken them (ink bleeding into the paper) by moving
 * one threshold. The font is Android's bold sans-serif (Roboto), which exists on every device.
 */
internal object StampDiaryCalendarPostmark {
    private val P = StampDiaryCalendarPostmarkSpec
    private val cache = HashMap<String, Bitmap>()

    fun bitmap(day: Int, month: Int, year: Int): Bitmap {
        val key = "$year-$month-$day"
        return cache.getOrPut(key) { make(day, month, year) }
    }

    private fun arcText(canvas: Canvas, text: String, radius: Double, centerAngle: Double, paint: Paint, bottom: Boolean) {
        val chars = text.map { it.toString() }
        val widths = chars.map { paint.measureText(it).toDouble() }
        val tracking = 2.0
        val total = widths.fold(-tracking) { sum, w -> sum + w + tracking }
        // Arc length -> angle. Top text runs clockwise, bottom text counter-clockwise, both upright.
        var along = -total / 2
        chars.forEachIndexed { i, ch ->
            val mid = along + widths[i] / 2
            val angle = centerAngle + (if (bottom) -1 else 1) * (mid / radius)
            canvas.save()
            canvas.translate((P.RING_X + cos(angle) * radius).toFloat(), (P.RING_Y + sin(angle) * radius).toFloat())
            canvas.rotate((((angle + if (bottom) -PI / 2 else PI / 2) * 180) / PI).toFloat())
            val capHalf = paint.textSize * 0.36f
            canvas.drawText(ch, (-widths[i] / 2).toFloat(), capHalf, paint)
            canvas.restore()
            along += widths[i] + tracking
        }
    }

    private fun make(day: Int, month: Int, year: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(P.WIDTH, P.HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Skia sigma 2.2 -> Android blur radius (sigma = 0.57735 * radius + 0.5).
        val soft = BlurMaskFilter(((P.SOFTNESS - 0.5) / 0.57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            maskFilter = soft
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            maskFilter = soft
            typeface = bold
        }

        stroke.strokeWidth = P.OUTER_STROKE.toFloat()
        canvas.drawCircle(P.RING_X.toFloat(), P.RING_Y.toFloat(), P.OUTER.toFloat(), stroke)
        stroke.strokeWidth = P.INNER_STROKE.toFloat()
        canvas.drawCircle(P.RING_X.toFloat(), P.RING_Y.toFloat(), P.INNER.toFloat(), stroke)

        val textRadius = (P.OUTER + P.INNER) / 2
        val top = StampDiaryCalendarTheme.MONTH_NAMES[month - 1].take(3).uppercase() + " · " + year
        fill.textSize = P.ARC_FONT.toFloat()
        arcText(canvas, top, textRadius, -PI / 2, fill, false)
        arcText(canvas, "STAMP DIARY", textRadius, PI / 2, fill, true)

        fill.textSize = P.DAY_FONT.toFloat()
        val label = day.toString()
        canvas.drawText(label, (P.RING_X - fill.measureText(label) / 2).toFloat(), (P.RING_Y + P.DAY_FONT * 0.36).toFloat(), fill)

        stroke.strokeWidth = P.WAVE_STROKE.toFloat()
        for (i in 0 until P.WAVES) {
            val y = P.RING_Y + (i - (P.WAVES - 1) / 2.0) * P.WAVE_GAP
            val path = Path()
            path.moveTo(P.WAVE_FROM.toFloat(), y.toFloat())
            var x = P.WAVE_FROM
            while (x <= P.WAVE_TO) {
                path.lineTo(x.toFloat(), (y + sin(((x - P.WAVE_FROM) / P.WAVE_LENGTH) * 2 * PI) * P.WAVE_AMP).toFloat())
                x += 4
            }
            canvas.drawPath(path, stroke)
        }
        return bitmap
    }
}
