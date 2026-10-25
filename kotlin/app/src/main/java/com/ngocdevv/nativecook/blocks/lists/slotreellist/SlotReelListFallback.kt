package com.ngocdevv.nativecook.blocks.lists.slotreellist

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Slot Reel List — the drums below Android 13, without a runtime shader. Each drum is cut into thin
 horizontal bands; each band shows the slice of the printed strip the cylinder puts there (the same
 θ = asin(yc / radius) mapping as the shader), so rows still squeeze toward the window's edges.
 A shade gradient per drum stands in for the light's fall-off; there is no speed blur, and the win
 glow and sweep are a warm tint and a soft band across the payline.
*/
internal class SlotReelFallback(private val art: SlotArt) {
    private val l = art.layout
    private val bands = 56
    private val src = Rect()
    private val dst = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val steel = Paint().apply { color = 0xFF1E1311.toInt() }
    private val glowPaint = Paint()
    private val sweepShader = LinearGradient(
        -40f, 0f, 40f, 0f,
        intArrayOf(0x00FFEDC7, android.graphics.Color.argb(128, 255, 237, 199), 0x00FFEDC7),
        null, Shader.TileMode.CLAMP,
    )
    private val sweepMatrix = android.graphics.Matrix()
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sweepShader }
    // The light's fall-off over one drum: dark at its top and bottom, lit across the middle.
    private val shade = Paint().apply {
        val n = 17
        val colors = IntArray(n)
        val pos = FloatArray(n)
        for (i in 0 until n) {
            val f = i / (n - 1f)
            val yc = (f - 0.5f) * l.win.h
            val ct = cos(asin((yc / l.radius).coerceIn(-0.999f, 0.999f)))
            val lit = 0.16f + 0.84f * ct.pow(1.6f)
            colors[i] = android.graphics.Color.argb(((1 - lit) * 255).roundToInt(), 0, 0, 0)
            pos[i] = f
        }
        shader = LinearGradient(0f, l.win.y, 0f, l.win.y + l.win.h, colors, pos, Shader.TileMode.CLAMP)
    }

    /** Draws the drums at offsets [off] (rows) in dp coordinates. */
    fun draw(c: Canvas, off: DoubleArray, glow: Float, sweep: Float) {
        val win = l.win
        val px = art.px
        val rows = SLOT_ROWS
        c.drawRect(win.x, win.y, win.x + win.w, win.y + win.h, steel)
        val bandH = win.h / bands
        for (col in 0 until 3) {
            val x = win.x + col * (l.colW + l.gap)
            val srcX0 = (col * l.colW * px).roundToInt()
            val srcX1 = ((col + 1) * l.colW * px).roundToInt()
            for (b in 0 until bands) {
                val y0 = b * bandH
                val y1 = y0 + bandH
                val r0 = rowAt(off[col], y0 - win.h / 2)
                val r1 = rowAt(off[col], y1 - win.h / 2)
                // Rows → strip pixels, wrapped; a band that crosses the strip's end is drawn in two.
                val v0 = r0 - rows * floor(r0 / rows)
                val v1 = v0 + (r1 - r0)
                val top = (v0 * l.rowH * px).toFloat()
                val bottom = (v1 * l.rowH * px).toFloat()
                val end = rows * l.rowH * px
                if (bottom <= end) {
                    blit(c, srcX0, srcX1, top, bottom, x, win.y + y0, win.y + y1)
                } else {
                    val split = y0 + (y1 - y0) * (end - top) / (bottom - top)
                    blit(c, srcX0, srcX1, top, end, x, win.y + y0, win.y + split)
                    blit(c, srcX0, srcX1, 0f, bottom - end, x, win.y + split, win.y + y1)
                }
            }
            c.drawRect(x, win.y, x + l.colW, win.y + win.h, shade)
        }
        // The payline row: a warm tint for the win and a soft band of light sweeping across.
        val half = l.rowH * 0.5f
        val mid = win.y + win.h / 2
        if (glow > 0.001f) {
            glowPaint.color = android.graphics.Color.argb((glow * 0.22f * 255).roundToInt(), 255, 190, 70)
            c.drawRect(win.x, mid - half, win.x + win.w, mid + half, glowPaint)
        }
        if (sweep > -0.5f) {
            val bx = win.x - 80 + (win.w + 160) * sweep
            sweepMatrix.setTranslate(bx, 0f)
            sweepShader.setLocalMatrix(sweepMatrix)
            c.drawRect(win.x, mid - half, win.x + win.w, mid + half, sweepPaint)
        }
    }

    private fun rowAt(o: Double, yc: Float): Double {
        val th = asin((yc / l.radius).toDouble().coerceIn(-0.999, 0.999))
        return o + th * l.radius / l.rowH + 0.5
    }

    private fun blit(c: Canvas, sx0: Int, sx1: Int, sy0: Float, sy1: Float, x: Float, dy0: Float, dy1: Float) {
        src.set(sx0, sy0.roundToInt(), sx1, sy1.roundToInt().coerceAtLeast(sy0.roundToInt() + 1))
        dst.set(x, dy0, x + l.colW, dy1 + 0.5f)
        c.drawBitmap(art.strip, src, dst, bitmapPaint)
    }
}
