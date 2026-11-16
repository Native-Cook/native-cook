package com.ngocdevv.nativecook.blocks.cards.reededglassreveal

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A separable gaussian blur by three box passes (the usual approximation), on a bitmap, for the two defocused copies of the portrait the glass and
 * the capsule's ends refract (RN blurs the photo once with a Skia image filter and caches it). Runs once, off the main thread.
 */
internal object ReededGlassRevealBlur {
    /** `sigmaX` / `sigmaY` in pixels of `src`; the result has the same size. */
    fun blur(src: Bitmap, sigmaX: Double, sigmaY: Double): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val r = IntArray(w * h)
        val g = IntArray(w * h)
        val b = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            r[i] = (c shr 16) and 255
            g[i] = (c shr 8) and 255
            b[i] = c and 255
        }
        val tmp = IntArray(w * h)
        val boxesX = boxes(sigmaX)
        val boxesY = boxes(sigmaY)
        for (ch in arrayOf(r, g, b)) {
            for (k in 0 until 3) {
                pass(ch, tmp, w, h, boxesX[k], true)
                pass(ch, tmp, w, h, boxesY[k], false)
            }
        }
        for (i in px.indices) px[i] = (0xFF shl 24) or (r[i] shl 16) or (g[i] shl 8) or b[i]
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** the three box radii that approximate a gaussian of this sigma */
    private fun boxes(sigma: Double): IntArray {
        val n = 3
        val ideal = sqrt((12 * sigma * sigma / n) + 1)
        var wl = ideal.toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val m = Math.round((12 * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4.0 * wl - 4)).toInt()
        return IntArray(n) { (if (it < m) wl else wu).let { size -> max(0, (size - 1) / 2) } }
    }

    /** one box pass of radius `rad` along x or y (edges clamp), in place through `tmp` */
    private fun pass(a: IntArray, tmp: IntArray, w: Int, h: Int, rad: Int, horizontal: Boolean) {
        if (rad <= 0) return
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val stride = if (horizontal) 1 else w
        val lineStep = if (horizontal) w else 1
        val inv = 1.0 / (2 * rad + 1)
        for (line in 0 until lines) {
            val base = line * lineStep
            var acc = 0
            // window [-rad, rad] around 0 with clamped edges
            for (k in -rad..rad) acc += a[base + k.coerceIn(0, len - 1) * stride]
            for (i in 0 until len) {
                tmp[base + i * stride] = (acc * inv + 0.5).toInt()
                val add = a[base + (i + rad + 1).coerceIn(0, len - 1) * stride]
                val sub = a[base + (i - rad).coerceIn(0, len - 1) * stride]
                acc += add - sub
            }
            for (i in 0 until len) a[base + i * stride] = tmp[base + i * stride]
        }
    }
}
