package com.ngocdevv.nativecook.blocks.cards.filmnegativecard

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.shared.sigmaToRadius

/*
 Below API 33 there is no runtime shader, so the strip is drawn with plain canvas calls that
 follow the shader's maths linearly: each frame of the photo through a colour matrix (the orange
 mask times 1 − 0.93 × scene, at 55 % off the light), the clear base around them, and, clipped to
 the lit panel (inset by the shader's half-falloff), the positive (the photo through the inverse
 matrix) on a black mount, with a blurred warm stroke for the halo. Holes are cut with CLEAR. The
 loupe draws the positive 1.8× inside a circle.
 */
internal class FilmNegativeFallback(private val L: FilmNegativeLayout, private val photo: Bitmap) {
    private val base = floatArrayOf(0.90f, 0.54f, 0.30f)
    private val offPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(negative(0.55f)) }
    private val posPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(positive(1f)) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = filmNegativeArgb(255, 184, 107, 0.2f)
        maskFilter = BlurMaskFilter(sigmaToRadius(5f), BlurMaskFilter.Blur.NORMAL)
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private val path = Path()

    private fun negative(k: Float) = ColorMatrix(
        floatArrayOf(
            -0.93f * base[0] * k, 0f, 0f, 0f, 255f * base[0] * k,
            0f, -0.93f * base[1] * k, 0f, 0f, 255f * base[1] * k,
            0f, 0f, -0.93f * base[2] * k, 0f, 255f * base[2] * k,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private fun positive(k: Float): ColorMatrix {
        val s = 0.93f / 0.95f * k
        val o = -0.03f / 0.95f * 255f * k
        return ColorMatrix(
            floatArrayOf(
                s * 1.03f, 0f, 0f, 0f, o * 1.03f,
                0f, s, 0f, 0f, o,
                0f, 0f, s * 0.965f, 0f, o * 0.965f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun frames(c: Canvas, paint: Paint, roll: Boolean) {
        val F = L.frame
        val iw = photo.width.toFloat()
        val ih = photo.height.toFloat()
        for (i in -1..1) {
            val x0 = F.left + i * (F.width() + L.gap)
            dst.set(x0, F.top, x0 + F.width(), F.bottom)
            var k = 1f
            var ox = 0f
            var oy = 0f
            if (i == -1) { k = 0.78f; ox = 150f; oy = 90f }
            if (i == 1) { k = 0.82f; ox = 10f; oy = 70f }
            if (roll) { k = 0.62f; ox = 60f + (i + 1) * 110f; oy = 40f + (i + 1) * 30f }
            src.set(ox.toInt(), oy.toInt(), (ox + iw * k).toInt(), (oy + ih * k).toInt())
            c.save()
            path.reset()
            path.addRoundRect(dst, 2.5f, 2.5f, Path.Direction.CW)
            c.clipPath(path)
            c.drawBitmap(photo, src, dst, paint)
            c.restore()
        }
    }

    /** The strip in strip space; `light` and the loupe are in strip space too. */
    fun draw(c: Canvas, light: RectF, loupeX: Float, loupeY: Float, loupeR: Float, loupeA: Float, roll: Boolean) {
        val sw = L.stripW
        val sh = L.stripH
        c.saveLayer(0f, 0f, sw, sh, null)
        path.reset()
        path.addRoundRect(0f, 0f, sw, sh, 4f, 4f, Path.Direction.CW)
        c.clipPath(path)
        fill.color = filmNegativeArgb((base[0] * 0.95f * 0.55f * 255).toInt(), (base[1] * 0.95f * 0.55f * 255).toInt(), (base[2] * 0.95f * 0.55f * 255).toInt())
        c.drawRect(0f, 0f, sw, sh, fill)
        frames(c, offPaint, roll)
        if (!roll && light.right > light.left) {
            c.save()
            path.reset()
            path.addRoundRect(light.left + 6f, light.top + 6f, light.right - 6f, light.bottom - 6f, 8f, 8f, Path.Direction.CW)
            c.clipPath(path)
            fill.color = filmNegativeArgb(4, 3, 3)
            c.drawRect(0f, 0f, sw, sh, fill)
            frames(c, posPaint, false)
            c.restore()
            c.drawRoundRect(light.left + 6f, light.top + 6f, light.right - 6f, light.bottom - 6f, 8f, 8f, halo)
        }
        if (loupeA > 0.001f) {
            c.save()
            path.reset()
            path.addCircle(loupeX, loupeY, loupeR, Path.Direction.CW)
            c.clipPath(path)
            c.saveLayerAlpha(loupeX - loupeR, loupeY - loupeR, loupeX + loupeR, loupeY + loupeR, (loupeA * 255).toInt())
            fill.color = filmNegativeArgb(4, 3, 3)
            c.drawCircle(loupeX, loupeY, loupeR, fill)
            c.save()
            c.scale(1.8f, 1.8f, loupeX, loupeY)
            frames(c, posPaint, false)
            c.restore()
            rim.shader = RadialGradient(loupeX, loupeY, loupeR, intArrayOf(0, 0, filmNegativeArgb(0, 0, 0, 0.32f)), floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(loupeX, loupeY, loupeR, rim)
            c.restore()
            c.restore()
        }
        // Perforations, cut right through.
        var x = L.perfPhase + L.perfPitch * 0.5f - L.perfPitch * 2
        while (x < sw + L.perfPitch) {
            for (cy in floatArrayOf(L.perfTop, L.perfBottom)) {
                c.drawRoundRect(x - L.perfW / 2, cy - L.perfH / 2, x + L.perfW / 2, cy + L.perfH / 2, 2.4f, 2.4f, clear)
            }
            x += L.perfPitch
        }
        c.restore()
    }
}
