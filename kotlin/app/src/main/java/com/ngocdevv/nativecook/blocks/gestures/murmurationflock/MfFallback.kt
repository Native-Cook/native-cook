// Murmuration Flock — what shows without OpenGL ES 3 (MfSkiaFallback.tsx): an even sample of the same
// flock from the same pose(), each bird a short stroke along its heading, in one drawLines call.
package com.ngocdevv.nativecook.blocks.gestures.murmurationflock

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.exp
import kotlin.math.sqrt

private const val STEP = Flock.COUNT / Flock.FALLBACK

internal class MfFallbackPainter {
    /** every sampled bird's seeds, worked out once */
    private val seeds = DoubleArray(Flock.FALLBACK * 4).also { all ->
        val one = DoubleArray(4)
        for (n in 0 until Flock.FALLBACK) {
            seedsOf(n * STEP, one)
            one.copyInto(all, n * 4)
        }
    }
    private val lines = FloatArray(Flock.FALLBACK * 4)
    private val p = DoubleArray(4)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        color = Color.argb((0.72f * 255f).toInt(), 24, 18, 38)
    }

    /** the canvas is in dp */
    fun draw(canvas: Canvas, f: FloatArray) {
        val t = f[Frame.T].toDouble()
        val on = f[Frame.TOUCH_ON] * f[Frame.HOLD]
        val tx = f[Frame.TOUCH_X]
        val ty = f[Frame.TOUCH_Y]
        val cx = f[Frame.CENTRE_X]
        val cy = f[Frame.CENTRE_Y]
        val unit = f[Frame.UNIT]
        val r2max = Touch.RADIUS * Touch.RADIUS * 2.2f
        for (n in 0 until Flock.FALLBACK) {
            val a = seeds[n * 4]
            val b = seeds[n * 4 + 1]
            val c = seeds[n * 4 + 2]
            val d = seeds[n * 4 + 3]
            pose(a, b, c, d, t, p, 0)
            pose(a, b, c, d, t - 0.05, p, 2)
            var x = cx + (p[0] * unit).toFloat()
            var y = cy + (p[1] * unit).toFloat()
            if (on > 0.001f) {
                val dx = x - tx
                val dy = y - ty
                val r2 = dx * dx + dy * dy
                val push = on * 52f * exp(-r2 / r2max) / sqrt(r2 + 1f)
                x += dx * push - dy * push * 0.6f
                y += dy * push + dx * push * 0.6f
            }
            val hx = ((p[0] - p[2]) * unit).toFloat()
            val hy = ((p[1] - p[3]) * unit).toFloat()
            val len = sqrt(hx * hx + hy * hy) + 0.001f
            val k = (1.6f + 1.8f * c.toFloat()) / len
            lines[n * 4] = x - hx * k
            lines[n * 4 + 1] = y - hy * k
            lines[n * 4 + 2] = x + hx * k
            lines[n * 4 + 3] = y + hy * k
        }
        canvas.drawLines(lines, paint)
    }
}
