// Coral Growth Streak — the reef without OpenGL ES 3, and under reduced motion, on every API level.
//
// No simulation: the look of the RN fallback's labyrinth (seven plane waves of one wavelength,
// thresholded, with the view's colours and light) is computed once on the CPU into two bitmaps, the
// bare sea bed and the full reef. Each frame draws the bed, then the reef clipped to the open radius
// and around each touch, faded by the level, with the bloom's band added. The caustic does not drift.
package com.ngocdevv.nativecook.blocks.progress.coralgrowthstreak

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** touches the fallback remembers */
internal const val TOUCHES = 8
private const val BAKE_SIZE = 384
private const val BAND_STOPS = 17

internal class CgsBaked(val bed: Bitmap, val reef: Bitmap)

private fun step01(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

private fun lin(c: Double) = c.pow(2.2)

private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t

private fun labyrinth(x: Double, y: Double): Double {
    val px = x + 0.03 * sin(y * 5.0)
    val py = y + 0.03 * sin(x * 4.3)
    var sum = 0.0
    for (i in 0 until 7) {
        val a = i * 2.399 + 0.5
        sum += cos(38.0 * (px * cos(a) + py * sin(a)) + i * 1.7)
    }
    return step01(-0.1, 0.22, sum / 7.0)
}

private fun grain(x: Double, y: Double): Double {
    val v = sin(x * 127.1 + y * 311.7) * 43758.5453
    return v - floor(v)
}

private fun encode(c: Double) = (c.coerceIn(0.0, 1.0).pow(1.0 / 2.2) * 255.0 + 0.5).toInt()

/** the bed and the full reef, computed once; call off the main thread */
internal fun bakeFallback(): CgsBaked {
    val n = BAKE_SIZE
    val bedPixels = IntArray(n * n)
    val reefPixels = IntArray(n * n)
    val e = 2.0 / 256.0
    val lx = -0.45 / sqrt(0.45 * 0.45 + 0.6 * 0.6 + 0.66 * 0.66)
    val ly = -0.6 / sqrt(0.45 * 0.45 + 0.6 * 0.6 + 0.66 * 0.66)
    val lz = 0.66 / sqrt(0.45 * 0.45 + 0.6 * 0.6 + 0.66 * 0.66)
    val hLen = sqrt(lx * lx + ly * ly + (lz + 1.0) * (lz + 1.0))
    val hx = lx / hLen
    val hy = ly / hLen
    val hz = (lz + 1.0) / hLen
    for (py in 0 until n) {
        for (px in 0 until n) {
            val dx = (px + 0.5) / n * 2.0 - 1.0
            val dy = (py + 0.5) / n * 2.0 - 1.0
            val r = sqrt(dx * dx + dy * dy)
            val alpha = ((1.0 - r) * n * 0.5).coerceIn(0.0, 1.0)
            if (alpha <= 0.0) continue
            val hc = labyrinth(dx, dy)
            val sx = labyrinth(dx + e, dy) - hc
            val sy = labyrinth(dx, dy + e) - hc
            val nx0 = -sx * 2.4
            val ny0 = -sy * 2.4
            val nLen = sqrt(nx0 * nx0 + ny0 * ny0 + 1.0)
            val nx = nx0 / nLen
            val ny = ny0 / nLen
            val nz = 1.0 / nLen
            val dif = max(nx * lx + ny * ly + nz * lz, 0.0)
            val spec = max(nx * hx + ny * hy + nz * hz, 0.0).pow(40.0)
            val shadow = labyrinth(dx - 2.0 * e, dy - 3.0 * e) * (1.0 - hc)

            val qx = dx * 3.2
            val qy = dy * 3.2
            val c1 = sin(qx * 1.7 + sin(qy * 2.3) * 1.2)
            val c2 = sin(qy * 2.1 - sin(qx * 1.9) * 1.3)
            val caustic = (0.5 + 0.5 * c1 * c2).pow(3.0)
            val rr = step01(0.0, 1.0, r)
            val sand = if (grain(floor(px / 1.5), floor(py / 1.5)) >= 0.965) 0.02 else 0.0
            val bedR = mix(lin(0.075), lin(0.030), rr) + caustic * 0.022 * 0.5 + sand
            val bedG = mix(lin(0.300), lin(0.170), rr) + caustic * 0.022 + sand * 0.9
            val bedB = mix(lin(0.340), lin(0.215), rr) + caustic * 0.022 * 0.95 + sand * 0.7

            val tone = step01(0.1, 1.0, r)
            var bodyR = mix(lin(0.900), lin(1.000), tone)
            var bodyG = mix(lin(0.300), lin(0.530), tone)
            var bodyB = mix(lin(0.370), lin(0.360), tone)
            val flank = step01(0.15, 0.75, hc)
            bodyR = mix(bodyR * 0.34, bodyR, flank)
            bodyG = mix(bodyG * 0.22, bodyG, flank)
            bodyB = mix(bodyB * 0.42, bodyB, flank)
            val ridge = step01(0.82, 1.0, hc) * 0.3
            bodyR = mix(bodyR, lin(1.000), ridge)
            bodyG = mix(bodyG, lin(0.880), ridge)
            bodyB = mix(bodyB, lin(0.720), ridge)
            val lit = 0.42 + 0.74 * dif
            val coralR = bodyR * lit + spec * 0.3
            val coralG = bodyG * lit + spec * 0.3 * 0.95
            val coralB = bodyB * lit + spec * 0.3 * 0.85
            val cover = step01(0.08, 0.3, hc)
            val shade = 1.0 - 0.55 * shadow

            val rim = step01(0.5, 1.0, r)
            val window = 1.0 - 0.4 * rim * rim
            val a = (alpha * 255.0 + 0.5).toInt()
            bedPixels[py * n + px] = Color.argb(a, encode(bedR * window), encode(bedG * window), encode(bedB * window))
            reefPixels[py * n + px] = Color.argb(
                a,
                encode(mix(bedR * shade, coralR, cover) * window),
                encode(mix(bedG * shade, coralG, cover) * window),
                encode(mix(bedB * shade, coralB, cover) * window),
            )
        }
    }
    val bed = Bitmap.createBitmap(bedPixels, n, n, Bitmap.Config.ARGB_8888)
    val reef = Bitmap.createBitmap(reefPixels, n, n, Bitmap.Config.ARGB_8888)
    return CgsBaked(bed, reef)
}

/** draws the fallback reef; one per screen, used on the main thread */
internal class CgsFallbackPainter {
    private val image = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val open = Path()
    private val rect = RectF()
    private val colors = IntArray(BAND_STOPS)
    private val stops = FloatArray(BAND_STOPS) { it / (BAND_STOPS - 1f) }

    fun draw(canvas: Canvas, baked: CgsBaked, size: Float, reefDp: Float, ring: Float, level: Float, bloom: Float, clock: Double, touches: FloatArray) {
        val radius = size / 2f
        rect.set(0f, 0f, size, size)
        image.alpha = 255
        canvas.drawBitmap(baked.bed, null, rect, image)

        open.rewind()
        if (ring > 0f) open.addCircle(radius, radius, ring * radius, Path.Direction.CW)
        for (i in 0 until TOUCHES) {
            if (touches[i * 4 + 3] <= 0f) continue
            val age = clock - touches[i * 4 + 2]
            if (age <= 0.0) continue
            val reach = (1.2 * (1.0 - exp(-age * 0.35))).toFloat() * radius
            val scale = size / reefDp
            open.addCircle(touches[i * 4] * scale, touches[i * 4 + 1] * scale, reach, Path.Direction.CW)
        }
        if (!open.isEmpty && level > 0f) {
            val saved = canvas.save()
            canvas.clipPath(open)
            image.alpha = (level.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            canvas.drawBitmap(baked.reef, null, rect, image)
            canvas.restoreToCount(saved)
            image.alpha = 255
        }

        if (bloom > 0f && bloom < 1f) {
            val strength = sin(PI * bloom)
            for (i in 0 until BAND_STOPS) {
                val off = (stops[i] - (bloom * 1.5f - 0.25f)) / 0.17f
                val band = exp(-(off * off).toDouble()) * strength
                colors[i] = Color.argb(
                    255,
                    (min(1.0, band * 0.51) * 255.0).toInt(),
                    (min(1.0, band * 0.345) * 255.0).toInt(),
                    (min(1.0, band * 0.26) * 255.0).toInt(),
                )
            }
            glow.shader = RadialGradient(radius, radius, radius, colors, stops, Shader.TileMode.CLAMP)
            canvas.drawCircle(radius, radius, radius, glow)
            glow.shader = null
        }
    }
}
