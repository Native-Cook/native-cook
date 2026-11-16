package com.ngocdevv.nativecook.blocks.cards.cardvacuumseal

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.cards.cardvacuumseal.CardVacuumSealMotion as M
import com.ngocdevv.nativecook.blocks.cards.cardvacuumseal.CardVacuumSealTheme as Theme
import kotlin.math.sin

/**
 * Below API 33 (and until the card's snapshot exists) there is no film shader: the card is drawn as it is, and the film is a set of strokes
 * and gradients over it. A falling and resting air pillow (a faint lilac sheet with a bright rim, larger than the card, lowered), then as
 * the air leaves a milky tube along the lip with soft bunches at the corners, three creases that catch a travelling highlight, the contact
 * shadow, the dent under a finger and the crease piling at a squeegeeing fingertip. No refraction and no studio reflections.
 * Coordinates are dp; (x, y, w, h) is the card's rect and `s` dp per design unit.
 */
internal class CardVacuumSealFallback {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val blur = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)

    private fun sat(v: Double) = v.coerceIn(0.0, 1.0)
    private fun a(c: Int, alpha: Double) = Color.argb((alpha.coerceIn(0.0, 1.0) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    fun draw(c: Canvas, card: Bitmap, x: Float, y: Float, w: Float, h: Float, s: Float, e: CardVacuumSealEngine) {
        val look = e.look
        val p = look.p
        val fade = e.fade.value
        // the card
        rect.set(x, y, x + w, y + h)
        c.drawBitmap(card, null, rect, bitmapPaint)
        if (p <= 0.001 || fade <= 0.001) return
        val drape = sat(p / M.P_DRAPE)
        val kf = fade * sat(drape * 1.6)
        val air = sat((p - M.P_DRAPE) / (1 - M.P_DRAPE))
        val rr = (Theme.RADIUS_CARD * s).toFloat()
        // contact shadow
        fill.color = a(Theme.shadow, 0.3 * kf)
        fill.maskFilter = blur
        rect.set(x + 2 * s, y + 3 * s, x + w + 2 * s, y + h + 3 * s)
        c.drawRoundRect(rect, rr, rr, fill)
        fill.maskFilter = null
        // the air pillow: the sheet falls and lands lowered, then deflates onto the card
        val pil = 1 - air
        if (pil > 0.01) {
            val one = 1 - drape
            val shift = (-60 * one * one * one + 3 * sin(Math.PI * sat((drape - 0.62) / 0.38))) * s
            val dy = (14.5 * s + shift).toFloat()
            rect.set(x - 34 * s, y - 38.5f * s + dy, x + w + 34 * s, y + h + 38.5f * s + dy)
            fill.color = a(Color.rgb(0xC9, 0xB8, 0xFF), 0.10 * pil * kf)
            c.drawRoundRect(rect, 44 * s, 44 * s, fill)
            stroke.color = a(Color.WHITE, 0.55 * pil * kf)
            stroke.strokeWidth = 1.2f
            c.drawRoundRect(rect, 44 * s, 44 * s, stroke)
            fill.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom, a(Color.WHITE, 0.16 * pil * kf), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            c.drawRoundRect(rect, 44 * s, 44 * s, fill)
            fill.shader = null
        }
        if (air > 0.01) {
            // the skin: a milky tube along the lip, soft bunches at the corners
            val lip = (Theme.FILM_LIP * s).toFloat()
            rect.set(x - lip, y - lip, x + w + lip, y + h + lip)
            val r2 = rr + lip
            stroke.color = a(Color.rgb(0xE6, 0xDE, 0xFF), 0.34 * air * kf)
            stroke.strokeWidth = 11 * s
            c.drawRoundRect(rect, r2, r2, stroke)
            stroke.color = a(Color.WHITE, 0.6 * air * kf)
            stroke.strokeWidth = 1.2f
            c.drawRoundRect(rect, r2, r2, stroke)
            fill.color = a(Color.rgb(0xE6, 0xDE, 0xFF), 0.22 * air * kf)
            for (cx in floatArrayOf(rect.left + 2 * s, rect.right - 2 * s)) for (cy in floatArrayOf(rect.top + 2 * s, rect.bottom - 2 * s)) {
                c.drawCircle(cx, cy, (Theme.FILM_EAR * 0.62 * s).toFloat(), fill)
            }
            // three creases and the highlight that travels along them
            val amp = look.settle * e.amp
            val cr = floatArrayOf(
                x + 0.1f * w, y + 0.12f * h, x + 0.44f * w, y + 0.5f * h,
                x + 0.97f * w, y + 0.08f * h, x + 0.7f * w, y + 0.4f * h,
                x + 0.9f * w, y + 0.9f * h, x + 0.62f * w, y + 0.72f * h,
            )
            stroke.strokeWidth = (1.3 * s).toFloat()
            for (i in 0 until 3) {
                stroke.color = a(Color.WHITE, (0.16 + 0.3 * look.glint * 0.6 + 0.22 * look.sweepK) * air * amp * kf)
                c.drawLine(cr[i * 4], cr[i * 4 + 1], cr[i * 4 + 2], cr[i * 4 + 3], stroke)
            }
            if (look.sweepK > 0.01) {
                val gx = x + w * (0.5f + 0.5f * look.sweep.toFloat())
                fill.shader = LinearGradient(
                    gx - 40 * s, y, gx + 40 * s, y + h, intArrayOf(Color.TRANSPARENT, a(Color.WHITE, 0.2 * look.sweepK * kf), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP,
                )
                rect.set(x, y, x + w, y + h)
                c.drawRoundRect(rect, rr, rr, fill)
                fill.shader = null
            }
        }
        // a finger pressing the sealed film, and the crease at a squeegeeing fingertip
        if (e.dimpleK.value > 0.01) {
            val dx = (x + e.dimpleX * s).toFloat()
            val dyy = (y + e.dimpleY * s).toFloat()
            fill.shader = RadialGradient(
                dx, dyy, 46 * s, a(Color.BLACK, 0.28 * e.dimpleK.value), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            c.drawCircle(dx, dyy, 46 * s, fill)
            fill.shader = null
        }
        if (e.fingerK.value > 0.01) {
            val fx = (x + e.fingerX * s).toFloat()
            val fy = (y + e.fingerY * s).toFloat()
            fill.shader = RadialGradient(fx, fy, 30 * s, a(Color.WHITE, 0.22 * e.fingerK.value), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(fx, fy, 30 * s, fill)
            fill.shader = null
        }
    }
}
