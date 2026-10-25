package com.ngocdevv.nativecook.blocks.progress.rocketlaunchprogress

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Rocket Launch Progress — the exhaust below Android 13, without a runtime shader: the same twelve
 smoke puffs (same birth times, reach, rise and growth as the shader) as soft grey discs, the
 flame as a teardrop of warm gradients along the nozzle's axis, and the ground glow as a radial
 pool. No fbm billows and no flicker noise.
*/
internal class RocketExhaustFallback {
    private val matrix = Matrix()
    private val puffShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xFFE6E3EA.toInt(), 0xB0B4B0BE.toInt(), 0x00807C8C),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val puff = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = puffShader }
    private val glowShader = RadialGradient(0f, 0f, 1f, intArrayOf(0x80FF8C40.toInt(), 0x00FF8C40), null, Shader.TileMode.CLAMP)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glowShader }
    private val flameShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xFFFFF7DC.toInt(), 0xFFFFB84D.toInt(), 0xE6FF5A1F.toInt(), 0x00FF3A10),
        floatArrayOf(0f, 0.3f, 0.7f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val flame = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = flameShader }
    private val tear = Path()

    private fun hash(x: Double, y: Double): Double {
        // A stand-in for the shader's hash: seeded, the same every loop.
        val v = sin(x * 127.1 + y * 311.7) * 43758.5453
        return v - floor(v)
    }

    /**
     * Draws in dp at the nozzle (nx, ny) pointing along (dx, dy) away from the rocket's nose; the
     * pad's smoke origin at (px, py); the rocket's scale [k].
     */
    fun draw(
        c: Canvas, nx: Float, ny: Float, dirX: Float, dirY: Float, throttle: Float, lift: Float,
        age: Float, fade: Float, padX: Float, padY: Float, k: Float,
    ) {
        if (fade > 0.001f) {
            for (i in 0 until 12) {
                val a = age - i * 0.11f
                if (a <= 0f) continue
                val side = if (i % 2 == 0) -1f else 1f
                val h = hash(i.toDouble(), 3.7).toFloat()
                val reach = (40 + 150 * h) * (1 - exp(-a * 1.5f))
                val cx = padX + side * (16 + reach) * k
                val cy = padY + (-10 - 22 * a - 14 * h) * k
                val r = (16 + 34 * sqrt(a) + 10 * h) * k * 1.25f
                puff.alpha = (fade * 0.85f / (1 + a * 0.6f) * 255).roundToInt().coerceIn(0, 255)
                matrix.setScale(r, r)
                matrix.postTranslate(cx, cy)
                puffShader.setLocalMatrix(matrix)
                c.drawCircle(cx, cy, r, puff)
            }
        }
        if (throttle > 0.001f) {
            // Ground glow.
            val gr = sqrt(9000f) * 1.6f * k
            glow.alpha = (throttle * (1 - lift * 0.8f) * 255).roundToInt().coerceIn(0, 255)
            matrix.setScale(gr, gr)
            matrix.postTranslate(nx, ny)
            glowShader.setLocalMatrix(matrix)
            c.drawCircle(nx, ny, gr, glow)
            // The flame: a teardrop along the axis, its length following the throttle and the climb.
            val len = (34 + 46 * lift) * throttle * k
            val w = 9.5f * k * (0.9f + 0.2f * throttle)
            c.save()
            c.translate(nx, ny)
            // Rotate so +y points away from the rocket (along -dir of the nose).
            val ang = Math.toDegrees(kotlin.math.atan2(-dirX.toDouble(), dirY.toDouble())).toFloat()
            c.rotate(ang + 180f)
            tear.rewind()
            tear.moveTo(-w * 0.7f, 0f)
            tear.cubicTo(-w * 1.2f, len * 0.35f, -w * 0.4f, len * 0.9f, 0f, len * 1.25f)
            tear.cubicTo(w * 0.4f, len * 0.9f, w * 1.2f, len * 0.35f, w * 0.7f, 0f)
            tear.close()
            matrix.setScale(max(w * 1.4f, 1f), max(len * 1.25f, 1f))
            flameShader.setLocalMatrix(matrix)
            flame.alpha = (throttle * 255).roundToInt().coerceIn(0, 255)
            c.drawPath(tear, flame)
            c.restore()
        }
    }
}
