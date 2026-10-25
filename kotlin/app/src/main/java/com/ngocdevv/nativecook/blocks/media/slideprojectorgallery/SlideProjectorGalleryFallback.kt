package com.ngocdevv.nativecook.blocks.media.slideprojectorgallery

import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader

/*
 Below API 33: the wall is the CPU-baked bitmap of the shader's own plaster; the slide is the
 atlas tile mapped onto the keystoned trapezoid (Matrix.setPolyToPoly from the unit square), the
 two nearest focus levels cross-faded by `focus`, the open gate above a dropping slide filled with
 light, darkened by a radial vignette, then added onto the wall in the lamp's colour (ADD, like the
 shader's `wall + light`). The beam is the same cone as a vertical gradient path, also added, and
 the lens glows. The spill around the frame and the beam's shafts are left out.
 */
internal class SlideProjectorFallback(private val L: SlideProjectorLayout, private val art: SlideProjectorArt) {
    private val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val add = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0.5f, 0.5f, 0.71f, intArrayOf(slideArgb(255, 255, 255), slideArgb(225, 225, 225), slideArgb(95, 95, 95)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    }
    private val beam = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val m = Matrix()
    private val trap = Path()
    private val cone = Path()
    private val src = Rect()
    private val unit = RectF(0f, 0f, 1f, 1f)
    private val full = RectF(0f, 0f, L.W, L.H)

    init {
        val top = L.projTop
        val bot = L.projTop + L.projH
        val ht = L.projW * 0.5f * SLIDE_KEY_TOP
        val hb = L.projW * 0.5f * SLIDE_KEY_BOTTOM
        val pts = floatArrayOf(L.projCx - ht, top, L.projCx + ht, top, L.projCx + hb, bot, L.projCx - hb, bot)
        m.setPolyToPoly(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f), 0, pts, 0, 4)
        trap.moveTo(pts[0], pts[1]); trap.lineTo(pts[2], pts[3]); trap.lineTo(pts[4], pts[5]); trap.lineTo(pts[6], pts[7]); trap.close()
        cone.moveTo(L.lensX - 11f, L.lensY); cone.lineTo(L.lensX + 11f, L.lensY); cone.lineTo(pts[4], pts[5]); cone.lineTo(pts[6], pts[7]); cone.close()
    }

    private fun lampColor(k: Float): Int {
        val r = (255f * k).toInt().coerceIn(0, 255)
        val g = (0.9f * 255f * k).toInt().coerceIn(0, 255)
        val b = (0.74f * 255f * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun tile(c: Canvas, row: Int, col: Int, alpha: Float, drop: Float, scale: Float) {
        if (alpha <= 0.001f) return
        val tw = SLIDE_TILE_W
        val th = SLIDE_TILE_H
        src.set(col * tw, row * th, col * tw + tw, row * th + th)
        tilePaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        val w = scale
        val left = 0.5f - w / 2f
        val top = 0.5f - w / 2f + drop
        c.drawBitmap(art.atlas, src, RectF(left, top, left + w, top + w), tilePaint)
    }

    fun draw(c: Canvas, p: SlideProjectorPose) {
        art.wall?.let { c.drawBitmap(it, null, full, bmp) }
        val lampOn = p.bright * p.flicker
        if (lampOn <= 0.003f) return
        // The slide, in the unit square of the trapezoid, added in the lamp's colour.
        add.colorFilter = LightingColorFilter(lampColor(lampOn * 0.92f), 0)
        c.saveLayer(full, add)
        c.save()
        c.clipPath(trap)
        c.concat(m)
        fill.color = slideArgb(235, 235, 235)
        c.drawRect(unit, fill)
        val f = SlideProjectorMotion.clamp01(p.focus)
        val d = p.drop / L.projH
        if (f < 0.5f) {
            tile(c, p.slide, 0, 1f, d, p.scale)
            tile(c, p.slide, 1, f * 2f, d, p.scale)
        } else {
            tile(c, p.slide, 1, 1f, d, p.scale)
            tile(c, p.slide, 2, f * 2f - 1f, d, p.scale)
        }
        c.drawRect(unit, vig)
        c.restore()
        c.restore()
        // The beam from the lens to the slide's lower corners, and the lens' glow.
        beam.shader = LinearGradient(0f, L.lensY, 0f, L.projTop + L.projH, intArrayOf(lampColor(0.23f * lampOn), lampColor(0.03f * lampOn)), null, Shader.TileMode.CLAMP)
        c.drawPath(cone, beam)
        glow.shader = RadialGradient(L.lensX, L.lensY, 34f, intArrayOf(lampColor(0.55f * lampOn), lampColor(0f)), null, Shader.TileMode.CLAMP)
        c.drawCircle(L.lensX, L.lensY, 34f, glow)
    }
}
