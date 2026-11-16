package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.pickers.beamshapetorch.BeamShapeTorchMotion as M
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Below API 33 there is no AGSL, so the capsule's inside is drawn with gradients and paths: the black glass's vertical gradient, the
 * same cone of light (a sector from the lens, full across, a falloff along its length, soft angular edges, tone-mapped), the pencil
 * with its landing spot on the ceiling, the white-hot lens and the afterimage stripe. Dispersion and the wall scatter are left out.
 * All in dp, the canvas already scaled to them.
 */
internal class BeamShapeTorchFallback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sector = Path()

    private fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun col(r: Double, g: Double, b: Double, a: Double = 1.0): Int = Color.argb(
        (a.coerceIn(0.0, 1.0) * 255).toInt(), (r.coerceIn(0.0, 1.0) * 255).toInt(), (g.coerceIn(0.0, 1.0) * 255).toInt(),
        (b.coerceIn(0.0, 1.0) * 255).toInt(),
    )

    /**
     * Draws into the capsule's clip. `capX..` the capsule's rect, (lx, ly) the lens, `sS` dp per design pt (scale x press),
     * `show` the content opacity.
     */
    fun draw(
        c: Canvas,
        capX: Float,
        capY: Float,
        capW: Float,
        capH: Float,
        lx: Float,
        ly: Float,
        sS: Float,
        show: Double,
        k: BeamShapeTorchLook,
        o: BeamShapeTorchOptics,
        glass: FloatArray,
        light: FloatArray,
        reduce: Boolean,
    ) {
        // the black glass: a vertical gradient (the theme's colour), lighter in the middle, a little darker toward the bottom
        val g = DoubleArray(3) { glass[it].toDouble() }
        val stops = floatArrayOf(0f, 0.08f, 0.2f, 0.32f, 0.44f, 0.55f, 0.62f, 0.81f, 1f)
        val colors = IntArray(stops.size) { i ->
            val yy = stops[i].toDouble()
            val a = smoothstep(0.08, 0.55, yy)
            val d = smoothstep(0.62, 1.0, yy)
            val f = mix(mix(0.08, 1.0, a), 0.89, d)
            col(g[0] * f, g[1] * f, g[2] * f)
        }
        paint.shader = LinearGradient(0f, capY, 0f, capY + capH, colors, stops, Shader.TileMode.CLAMP)
        c.drawRect(capX, capY, capX + capW, capY + capH, paint)
        paint.shader = null

        val b = k.b.coerceIn(0.0, 1.0)
        val w = k.w.coerceIn(0.0, 1.0)
        val hot = k.hot.coerceIn(0.0, 1.0)
        val reach = max(o.reach, 1.0)
        val th = o.theta
        val shimmer = 1.0 + 0.03 * (1 - w) * sin(2 * PI * 6 * k.phase) * (if (reduce) 0.0 else 1.0)
        val energy = b * o.gain.pow(0.45) * shimmer
        val lc = DoubleArray(3) { light[it].toDouble() / max(max(light[0], max(light[1], light[2])).toDouble(), 0.001) }
        val pw = 1 - smoothstep(0.0, 0.25, w)

        if (energy > 0.003 && show > 0.01) {
            val rr = (reach * 1.05 * sS).toFloat()
            // the cone: a sector from the lens, the falloff 1 / (1 + (r / r0)^1.6) along it, cut softly past the reach
            val r0 = 26 + 0.3 * reach
            val n = 12
            val pos = FloatArray(n)
            val clr = IntArray(n)
            for (i in 0 until n) {
                val f = i / (n - 1.0)
                val r = f * reach * 1.05
                val fall = 1 / (1 + (r / r0).pow(1.6))
                val cut = 1 - smoothstep(0.75 * reach, 1.05 * reach, r)
                val base = smoothstep(-1.0, 12.0, r * 0.6)
                val x = fall * cut * base * energy * 1.25 * show
                val a = 1 - exp(-x)
                val tint = 0.45 * smoothstep(0.0, 0.6 * reach, r)
                pos[i] = f.toFloat()
                clr[i] = col(
                    mix(lc[0], 213.0 / 255, tint), mix(lc[1], 209.0 / 255, tint), mix(lc[2], 242.0 / 255, tint), a * (1 - pw),
                )
            }
            val soft = Math.toRadians(1.5 + 3.5 * w) / 2
            for ((widen, share) in arrayOf(Pair(soft * 0.9, 0.34), Pair(0.0, 0.4), Pair(-soft * 0.9, 0.26))) {
                val ang = max(th + widen, 0.02)
                sector.rewind()
                sector.moveTo(lx, ly)
                val steps = 14
                for (i in 0..steps) {
                    val a = -ang + 2 * ang * i / steps
                    sector.lineTo(lx + (sin(a) * rr).toFloat(), ly - (kotlin.math.cos(a) * rr).toFloat())
                }
                sector.close()
                paint.shader = RadialGradient(lx, ly, rr, clr, pos, Shader.TileMode.CLAMP)
                paint.alpha = (255 * share.coerceIn(0.0, 1.0)).toInt()
                c.drawPath(sector, paint)
            }
            // the collimated pencil: starts at the lens, widens with the half-angle, brightness falling from 1 to .35 over the reach
            if (pw > 0.01) {
                val n2 = 8
                val pp = FloatArray(n2)
                val pc = IntArray(n2)
                for (i in 0 until n2) {
                    val f = i / (n2 - 1.0)
                    val len = mix(1.0, 0.35, f)
                    val cut = 1 - smoothstep(0.98, 1.1, f * 1.04)
                    val a = 1 - exp(-1.5 * len * cut * energy * show * pw)
                    pp[i] = f.toFloat()
                    pc[i] = col(lc[0], lc[1], lc[2], a)
                }
                val half0 = 5.0
                val half1 = 5.0 + reach * tan(th)
                for ((scaleW, share) in arrayOf(Pair(1.7, 0.3), Pair(1.0, 0.45), Pair(0.5, 0.55))) {
                    sector.rewind()
                    sector.moveTo(lx - (half0 * scaleW * sS).toFloat(), ly)
                    sector.lineTo(lx - (half1 * scaleW * sS).toFloat(), ly - (reach * sS).toFloat())
                    sector.lineTo(lx + (half1 * scaleW * sS).toFloat(), ly - (reach * sS).toFloat())
                    sector.lineTo(lx + (half0 * scaleW * sS).toFloat(), ly)
                    sector.close()
                    paint.shader = LinearGradient(lx, ly, lx, ly - (reach * sS).toFloat(), pc, pp, Shader.TileMode.CLAMP)
                    paint.alpha = (255 * share).toInt()
                    c.drawPath(sector, paint)
                }
            }
            // a 46 x 10 pt landing spot at the pencil's ceiling plane
            val land = smoothstep(190.0, 212.0, reach) * pw * b
            if (land > 0.01) {
                c.save()
                c.translate(lx, ly - (reach * sS).toFloat())
                c.scale(23f * sS, 5f * sS)
                paint.shader = RadialGradient(
                    0f, 0f, 1.7f, col(233.0 / 255, 229.0 / 255, 246.0 / 255, 0.55 * land * show), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
                paint.alpha = 255
                c.drawCircle(0f, 0f, 1.7f, paint)
                c.restore()
            }
        }

        // the white-hot lens, scaled by the lens heat (it glows on for 0.14 s after switching off)
        val heat = hot * mix(1.0, 1.35, ((o.gain - 1) / 6.5).coerceIn(0.0, 1.0))
        if (heat > 0.01 && show > 0.01) {
            val rLens = ((6 + 2 * heat) * 2.2 * sS).toFloat()
            paint.shader = RadialGradient(
                lx, ly, rLens,
                intArrayOf(col(1.0, 1.0, 1.0, (1 - exp(-heat * 2.0)) * show), col(lc[0], lc[1], lc[2], 0.45 * heat * show), Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
            )
            paint.alpha = 255
            c.drawCircle(lx, ly, rLens, paint)
        }

        // the afterimage of a held pencil: a dark lilac stripe above the lens that fades out of the fan as it opens
        if (k.after > 0.01 && !reduce) {
            val len = (M.AFTER_REACH * sS).toFloat()
            val a = 0.18 * k.after * smoothstep(0.03, 0.3, w)
            paint.shader = LinearGradient(
                lx, ly, lx, ly - len,
                intArrayOf(Color.TRANSPARENT, col(42.0 / 255, 36.0 / 255, 64.0 / 255, a), col(42.0 / 255, 36.0 / 255, 64.0 / 255, a), Color.TRANSPARENT),
                floatArrayOf(0f, 0.12f, 0.8f, 1f), Shader.TileMode.CLAMP,
            )
            paint.alpha = 255
            c.drawRect(lx - 9f * sS, ly - len, lx + 9f * sS, ly, paint)
        }
        paint.shader = null
        paint.alpha = 255
    }
}
