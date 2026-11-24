// Nebula Dust Swirl — what shows without OpenGL ES 3 (NdsSkiaFallback.tsx): an even sample of the same
// nebula from the same pose() (nds-motion.ts's, with the diffuse disc), each grain a small circle in one
// of four tones, added together. A finger turns the dust around it (a closed-form stir, no state), which
// eases away with the hold. The canvas it draws into must be an offscreen layer, so the grains add up
// among themselves before the layer is laid over the background.
package com.ngocdevv.nativecook.blocks.gestures.nebuladustswirl

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

internal class NdsFallbackPainter {
    /** the seeds of the sampled grains, worked out once */
    private val seeds = DoubleArray(Dust.FALLBACK * 8).also { all ->
        for (n in 0 until Dust.FALLBACK) seedsOf(n * Dust.FALLBACK_STEP, all, n * 8)
    }
    private val q = DoubleArray(4)
    /** core, warm inner arm, cool outer arm, star */
    private val paths = Array(4) { Path() }
    private val paints = arrayOf(
        Color.argb((0.55f * 255).toInt(), 255, 214, 160),
        Color.argb((0.5f * 255).toInt(), 240, 160, 120),
        Color.argb((0.5f * 255).toInt(), 130, 170, 225),
        Color.argb((0.9f * 255).toInt(), 255, 248, 236),
    ).map { tone ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tone
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.PLUS
            else xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }

    /** the canvas is in dp */
    fun draw(canvas: Canvas, f: FloatArray) {
        for (p in paths) p.rewind()
        val t = f[Frame.T].toDouble()
        val on = f[Frame.TOUCH_ON] * f[Frame.HOLD]
        val tx = f[Frame.TOUCH_X]
        val ty = f[Frame.TOUCH_Y]
        val cx = f[Frame.CENTRE_X]
        val cy = f[Frame.CENTRE_Y]
        val unit = f[Frame.UNIT]
        val r2 = (Touch.RADIUS * Touch.RADIUS * 1.6).toFloat()
        for (n in 0 until Dust.FALLBACK) {
            val at = n * 8
            val arm = (n * Dust.FALLBACK_STEP) and 1
            pose(seeds, at, arm, t, q)
            var x = cx + (q[0] * unit).toFloat()
            var y = cy + (q[1] * unit).toFloat()
            if (on > 0.001f) {
                // turn the grain around the finger, more the nearer it is
                val dx = x - tx
                val dy = y - ty
                val a = on * 1.1f * exp(-(dx * dx + dy * dy) / r2)
                val c = cos(a)
                val s = sin(a)
                x = tx + dx * c - dy * s
                y = ty + dx * s + dy * c
            }
            val kind = seeds[at + 6]
            val core = kind < Core.SHARE
            val star = !core && seeds[at + 7] > 1 - Dust.STAR_SHARE
            val k = if (star) 3 else if (core) 0 else if (q[3] < 0.38) 1 else 2
            val size = seeds[at + 2]
            val base = if (star) Dust.STAR else (Dust.SIZE + Dust.SIZE_ADD * size * size)
            paths[k].addCircle(x, y, (base * q[2] * 1.6).toFloat(), Path.Direction.CW)
        }
        // cool, warm, core, then stars, as the RN group draws them
        canvas.drawPath(paths[2], paints[2])
        canvas.drawPath(paths[1], paints[1])
        canvas.drawPath(paths[0], paints[0])
        canvas.drawPath(paths[3], paints[3])
    }
}
