package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Tone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Each column is a drum of ten digits. A digit fades and flattens as it leaves the window,
// so adjacent digits read as a single blur without needing a clipping edge.
internal class ReferralPoints(
    density: Float,
    fontScale: Float,
) {
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Tone.ink
            textSize = Size.pointsFont * fontScale
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

    // Tabular digits share one advance, measured once from a zero.
    private val advance = ceil(text.measureText("0") / 2) * 2
    private val fontMetrics = text.fontMetrics
    private val glyphs = Array(10) { it.toString() }

    // A visible digit is less than 0.75 steps away, so its maximum blur is 5 * 0.75 = 3.75 dp.
    // Cache those sigmas once; interpolating neighboring levels avoids filtering glyphs every frame.
    private val glyphImages =
        Array(10) { i ->
            val width = ceil((advance + 32) * density).toInt()
            val height = ceil((Size.pointsLine * fontScale + 32) * density).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(density, density)
            canvas.translate(width / 2f / density, height / 2f / density)
            canvas.drawText(glyphs[i], 0f, -(fontMetrics.ascent + fontMetrics.descent) / 2, text)
            ReferralBlurImages(
                bitmap,
                floatArrayOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.75f),
                density,
            )
        }

    fun draw(
        canvas: Canvas,
        state: ReferralState,
        width: Float,
    ) {
        canvas.save()
        canvas.translate(width / 2, (Size.cy - Size.pointsRise).toFloat())
        val scale = (1 + 0.1 * state.bump.value).toFloat()
        canvas.scale(scale, scale)
        val columns = state.columns.value
        for (order in 0..2) {
            val shown = state.shown[order].value.toFloat()
            if (shown <= 0) continue
            val x = ((columns - 1) * advance / 2 - order * advance).toFloat()
            for (digit in 0..9) {
                val away = ReferralMath.wrap(digit - state.turns[order].value + 5, 10.0) - 5
                val far = min(abs(away), 1.0)
                // At three quarters of a step the digit is gone, before it can read as a second number.
                val alpha = (max(1 - far / 0.75, 0.0).pow(1.4) * shown).toFloat()
                if (alpha <= 0) continue
                val y = if (state.reduced) 0f else (away * 28.8 + (1 - shown) * 14.4).toFloat()
                canvas.save()
                canvas.translate(x, y)
                if (!state.reduced) canvas.scale(1f, (1 - 0.3 * far).toFloat())
                if (!state.reduced && far > 0.005) {
                    glyphImages[digit].draw(canvas, (5 * far).toFloat(), alpha)
                } else {
                    text.alpha = (alpha * 255).toInt()
                    canvas.drawText(
                        glyphs[digit],
                        0f,
                        -(fontMetrics.ascent + fontMetrics.descent) / 2,
                        text,
                    )
                }

                canvas.restore()
            }
        }
        canvas.restore()
    }
}
