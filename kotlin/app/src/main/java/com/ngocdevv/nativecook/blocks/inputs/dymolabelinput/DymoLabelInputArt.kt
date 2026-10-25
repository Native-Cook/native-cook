package com.ngocdevv.nativecook.blocks.inputs.dymolabelinput

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/*
 Dymo Label Input — what is drawn per frame: the jars where the swap has them, the wheel bitmap at
 its angle (its gloss stays put), the trigger, the tape with its embossed letters, the cutter, and the
 strip in flight. Letters are pressed in: a dark lower-right edge, a bright upper-left edge and a
 stress-whitened face that turns from the tape's colour to near white. Units are pt (dp).
*/

internal class DymoGeo(val w: Float, val h: Float, val heroX: Float, val heroY: Float, val heroW: Float, val heroH: Float) {
    val shelfY = heroY + heroH * 0.43f
    val jarW = min(146f, heroW * 0.4f)
    val jarH = min(jarW * 1.18f, heroH * 0.37f)
    val wheelR = min(min(76f, heroW * 0.2f), heroH * 0.175f)
    val wheelX = heroX + heroW * 0.4f
    val wheelY = heroY + heroH * 0.8f
    val tapeY = wheelY - wheelR - 17
    val cx = heroX + heroW / 2
    val labelX = heroX + heroW / 2
    val labelY = shelfY - jarH * 0.42f
    val headX = wheelX
    val tapeH = 24f
    val pitch = 15f
    /** How far a jar travels when it is swapped. */
    val swap = w * 0.62f + jarW

    fun same(o: DymoGeo?) = o != null && o.w == w && o.h == h && o.heroX == heroX && o.heroY == heroY && o.heroW == heroW && o.heroH == heroH
}

internal class DymoArt(val geo: DymoGeo, val wall: Bitmap, val jar: Bitmap, val body: Bitmap, val bodySide: Float, val wheel: Bitmap)

internal fun dyWeight(w: Int): Typeface =
    if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, w, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

private fun col(c: FloatArray, a: Float) = android.graphics.Color.argb(a.coerceIn(0f, 1f), c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f))

private fun colf(r: Float, g: Float, b: Float, a: Float) = android.graphics.Color.argb(a.coerceIn(0f, 1f), r, g, b)

private fun tone(c: FloatArray, k: Float, out: FloatArray = FloatArray(3)): FloatArray {
    for (i in 0 until 3) out[i] = if (k < 0) c[i] * (1 + k) else c[i] + (1 - c[i]) * k
    return out
}

internal fun stripLength(g: DymoGeo, n: Int) = 2 * 14 + n * g.pitch

internal class DymoPainter(private val art: DymoArt) {
    private val g = art.geo
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurred = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurs = HashMap<Int, BlurMaskFilter>()
    private val font = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = dyWeight(700); textSize = 17f }
    private val widths = HashMap<Char, Float>().apply { for (ch in WHEEL) put(ch, font.measureText(ch.toString())) }
    private val tmp = FloatArray(3)
    private val tmp2 = FloatArray(3)
    private val index = Path()
    private val lever = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG)
    private val head = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private var leverAt = Float.NaN

    init {
        val iy = g.wheelY - g.wheelR - 2
        index.moveTo(g.headX - 5, iy - 6)
        index.lineTo(g.headX + 5, iy - 6)
        index.lineTo(g.headX, iy + 1)
        index.close()
        guide.shader = LinearGradient(
            0f, g.tapeY - 18, 0f, g.tapeY + 18,
            intArrayOf(colf(0.92f, 0.9f, 0.86f, 1f), colf(0.8f, 0.77f, 0.72f, 1f), colf(0.62f, 0.59f, 0.55f, 1f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        head.shader = LinearGradient(
            g.headX - 10, 0f, g.headX + 10, 0f,
            intArrayOf(colf(0.62f, 0.64f, 0.67f, 1f), colf(0.93f, 0.94f, 0.95f, 1f), colf(0.55f, 0.57f, 0.6f, 1f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        gloss.shader = RadialGradient(
            g.wheelX - g.wheelR * 0.35f, g.wheelY - g.wheelR * 0.45f, g.wheelR * 1.1f,
            intArrayOf(colf(1f, 1f, 1f, 0.22f), colf(1f, 1f, 1f, 0.05f), colf(1f, 1f, 1f, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }

    private fun blur(sigma: Float): BlurMaskFilter {
        val key = (sigma * 4).toInt()
        return blurs.getOrPut(key) { BlurMaskFilter(sigmaToRadius(key / 4f), BlurMaskFilter.Blur.NORMAL) }
    }

    /** A run of glossy tape from x0 to x1 on centre line y. */
    private fun drawTape(c: Canvas, x0: Float, x1: Float, y: Float, h: Float, rgb: FloatArray) {
        if (x1 <= x0) return
        rect.set(x0, y - h / 2, x1, y + h / 2)
        blurred.maskFilter = blur(1.6f)
        blurred.color = colf(0.1f, 0.06f, 0.04f, 0.3f)
        c.save(); c.translate(0.6f, 1.6f); c.drawRoundRect(rect, 2.5f, 2.5f, blurred); c.restore()
        fill.shader = LinearGradient(
            0f, y - h / 2, 0f, y + h / 2,
            intArrayOf(col(tone(rgb, 0.22f, tmp), 1f), col(rgb, 1f), col(tone(rgb, -0.12f, tmp), 1f), col(tone(rgb, -0.32f, tmp), 1f)),
            floatArrayOf(0f, 0.3f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(rect, 2.5f, 2.5f, fill)
        fill.shader = null
        // The gloss: a bright band near the top edge and a hairline on the lower edge.
        fill.color = colf(1f, 1f, 1f, 0.32f)
        rect.set(x0 + 1.5f, y - h / 2 + 2, x1 - 1.5f, y - h / 2 + 4.6f)
        c.drawRoundRect(rect, 1.3f, 1.3f, fill)
        fill.color = colf(1f, 1f, 1f, 0.12f)
        c.drawRect(x0 + 1, y + h / 2 - 1.6f, x1 - 1, y + h / 2 - 0.8f, fill)
    }

    /** One embossed letter centred at (x, y): w = how white it has become, s = scale. */
    private fun drawLetter(c: Canvas, ch: Char, x: Float, y: Float, rgb: FloatArray, w: Float, s: Float) {
        if (w <= 0.01f || ch == ' ') return
        val width = widths[ch] ?: 9f
        val str = ch.toString()
        c.save()
        c.translate(x, y)
        c.scale(0.84f * s, s)
        val bx = -width / 2
        val by = 6f
        font.color = col(tone(rgb, -0.55f, tmp), 0.7f * w)
        c.drawText(str, bx + 0.9f, by + 1.1f, font)
        font.color = colf(1f, 1f, 1f, 0.75f * w)
        c.drawText(str, bx - 0.55f, by - 0.65f, font)
        val k = 0.88f * w
        tmp2[0] = rgb[0] + (0.97f - rgb[0]) * k; tmp2[1] = rgb[1] + (0.95f - rgb[1]) * k; tmp2[2] = rgb[2] + (0.93f - rgb[2]) * k
        font.color = col(tmp2, 1f)
        c.drawText(str, bx, by, font)
        c.restore()
    }

    /** The whole scene for a pose. */
    fun draw(c: Canvas, pose: DymoPose) {
        rect.set(0f, 0f, g.w, g.h)
        c.drawBitmap(art.wall, null, rect, bmp)

        // Jars: the one on the shelf and, during the swap, the next one.
        val jarSide = g.jarW + 60
        val jarTop = g.shelfY - g.jarH - 30
        val oldX = g.cx - pose.slide * g.swap
        val newX = g.cx + (1 - pose.slide) * g.swap
        if (pose.slide < 1) { rect.set(oldX - jarSide / 2, jarTop, oldX + jarSide / 2, jarTop + g.jarH + 50); c.drawBitmap(art.jar, null, rect, bmp) }
        if (pose.slide > 0) { rect.set(newX - jarSide / 2, jarTop, newX + jarSide / 2, jarTop + g.jarH + 50); c.drawBitmap(art.jar, null, rect, bmp) }

        // The embosser.
        val bs = art.bodySide
        rect.set(g.wheelX - bs / 2, g.wheelY - bs / 2, g.wheelX + bs / 2, g.wheelY + bs / 2)
        c.drawBitmap(art.body, null, rect, bmp)
        // The trigger lever under the handle, pivoting at its left end.
        val tx = g.wheelX + g.wheelR * 0.45f
        val ty = g.wheelY + g.wheelR * 0.62f
        if (leverAt != ty) {
            lever.shader = LinearGradient(
                0f, ty - 11, 0f, ty + 11,
                intArrayOf(colf(0.36f, 0.38f, 0.42f, 1f), colf(0.22f, 0.23f, 0.26f, 1f), colf(0.12f, 0.12f, 0.14f, 1f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            leverAt = ty
        }
        c.save()
        c.rotate(-7 * pose.trigger, tx, ty)
        val lr = RectF(tx - 6, ty - 10, tx - 6 + g.wheelR * 2.55f, ty + 12)
        blurred.maskFilter = blur(4f)
        blurred.color = colf(0.1f, 0.06f, 0.03f, 0.3f)
        c.save(); c.translate(1f, 4f); c.drawRoundRect(lr, 11f, 11f, blurred); c.restore()
        c.drawRoundRect(lr, 11f, 11f, lever)
        fill.color = colf(1f, 1f, 1f, 0.1f)
        for (k in 0 until 7) {
            val x = tx + g.wheelR * 1.1f + k * 7
            c.drawRect(x, ty - 6, x + 2, ty + 6, fill)
        }
        c.restore()

        // Wheel, turned; its gloss and the head's index stay put.
        val ws = g.wheelR * 2 + 8
        c.save()
        c.rotate(pose.wheel, g.wheelX, g.wheelY)
        rect.set(g.wheelX - ws / 2, g.wheelY - ws / 2, g.wheelX + ws / 2, g.wheelY + ws / 2)
        c.drawBitmap(art.wheel, null, rect, bmp)
        c.restore()
        c.drawCircle(g.wheelX, g.wheelY, g.wheelR, gloss)
        fill.color = colf(0.85f, 0.2f, 0.2f, 1f)
        c.drawPath(index, fill)

        // Tape: blank lead, then the pressed letters; the newest sits under the head and pops as it is struck.
        val half = g.pitch / 2
        val leading = g.headX - pose.advance * g.pitch - half - pose.lead
        drawTape(c, leading, g.headX + 70, g.tapeY, g.tapeH, pose.tapeRGB)
        for (i in pose.tape.indices) {
            val x = g.headX - (pose.advance - i) * g.pitch
            drawLetter(c, pose.tape[i], x, g.tapeY, pose.tapeRGB, pose.whiten[i], 1 + 0.12f * pose.pop[i])
        }
        // Tape guide over the tape inside the machine, the punch head and the cutter.
        val guideR = RectF(g.headX + 11, g.tapeY - 17, g.headX + 103, g.tapeY + 17)
        blurred.maskFilter = blur(3f)
        blurred.color = colf(0.1f, 0.06f, 0.03f, 0.28f)
        c.save(); c.translate(-1.5f, 2.5f); c.drawRoundRect(guideR, 9f, 9f, blurred); c.restore()
        c.drawRoundRect(guideR, 9f, 9f, guide)
        val hy = g.tapeY - 19 + 3 * pose.trigger
        rect.set(g.headX - 9, hy, g.headX + 9, hy + 9)
        c.drawRoundRect(rect, 2.5f, 2.5f, head)
        c.save()
        c.rotate(-16 * pose.cutter, g.headX - 30, g.tapeY - 24)
        rect.set(g.headX - 46, g.tapeY - 30, g.headX - 16, g.tapeY - 21)
        c.drawRoundRect(rect, 4.5f, 4.5f, head)
        c.restore()

        // The cut strip: from where it was cut, along an arc with a turn and a lift, onto the jar.
        if (pose.strip.isNotEmpty()) {
            val len = stripLength(g, pose.strip.length)
            val sx = g.headX - half - 4 - len / 2
            val sy = g.tapeY
            val ex = g.labelX - pose.slide * g.swap
            val ey = g.labelY
            val f = pose.fly
            val mx = (sx + ex) / 2
            val my = min(sy, ey) - 120
            val x = (1 - f) * (1 - f) * sx + 2 * f * (1 - f) * mx + f * f * ex
            val y = (1 - f) * (1 - f) * sy + 2 * f * (1 - f) * my + f * f * ey
            val lift = sin(PI.toFloat() * f)
            val rot = -18 * lift - 2 * f
            val scale = 1 + 0.16f * lift
            blurred.maskFilter = blur(2 + 8 * lift)
            blurred.color = colf(0.1f, 0.06f, 0.03f, 0.25f - 0.1f * lift)
            c.save()
            c.translate(x + 4 + 16 * lift, y + 5 + 26 * lift)
            c.rotate(rot)
            rect.set(-len / 2, -g.tapeH / 2, len / 2, g.tapeH / 2)
            c.drawRoundRect(rect, 3f, 3f, blurred)
            c.restore()
            c.save()
            c.translate(x, y)
            c.rotate(rot)
            c.scale(scale * (1 + 0.05f * pose.squash), scale * (1 - 0.1f * pose.squash))
            drawTape(c, -len / 2, len / 2, 0f, g.tapeH, pose.stripRGB)
            for (i in pose.strip.indices) drawLetter(c, pose.strip[i], -len / 2 + 14 + (i + 0.5f) * g.pitch, 0f, pose.stripRGB, 1f, 1f)
            // On the jar the strip wraps round the glass: darker toward its ends.
            if (f > 0.9f) {
                val a = (f - 0.9f) / 0.1f
                fill.shader = LinearGradient(
                    -len / 2, 0f, len / 2, 0f,
                    intArrayOf(colf(0f, 0f, 0f, 0.3f * a), colf(0f, 0f, 0f, 0f), colf(1f, 1f, 1f, 0.08f * a), colf(0f, 0f, 0f, 0.32f * a)),
                    floatArrayOf(0f, 0.3f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
                rect.set(-len / 2, -g.tapeH / 2, len / 2, g.tapeH / 2)
                c.drawRoundRect(rect, 2.5f, 2.5f, fill)
                fill.shader = null
            }
            c.restore()
        }
    }
}
