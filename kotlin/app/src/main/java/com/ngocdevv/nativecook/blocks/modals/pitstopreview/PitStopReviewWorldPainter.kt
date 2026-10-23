package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import kotlin.math.floor
import kotlin.math.sin

internal class PitStopReviewWorldPainter {
    private val api33 = if (Build.VERSION.SDK_INT >= 33) PitStopReviewApi33WorldPainter() else null
    private val fallback = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bands = arrayOf(
        floatArrayOf(28f, 2.6f, 0.33f),
        floatArrayOf(58f, 0.6f, 0.04f),
        floatArrayOf(64f, 0.8f, 0.07f),
        floatArrayOf(117.5f, 2.2f, 0.15f),
        floatArrayOf(127.5f, 1.8f, 0.13f),
        floatArrayOf(140f, 0.8f, 0.11f),
        floatArrayOf(151f, 3.2f, 0.14f),
        floatArrayOf(160f, 0.6f, 0.08f),
    )

    fun draw(canvas: Canvas, scene: Double, unit: Float) {
        canvas.save()
        canvas.scale(unit, unit)
        if (Build.VERSION.SDK_INT >= 33) {
            api33?.draw(canvas, scene)
        } else {
            drawFallback(canvas, scene)
        }
        canvas.restore()
    }

    private fun drawFallback(canvas: Canvas, scene: Double) {
        val blur = PitStopReviewMotion.worldBlur(scene)
        val speed = (blur / 40.0).coerceIn(0.0, 1.0).toFloat()
        fallback.alpha = 255
        fallback.shader = LinearGradient(
            0f,
            0f,
            0f,
            PitStopReviewTheme.Hero.height,
            intArrayOf(Color.TRANSPARENT, Color.rgb(16, 16, 17), Color.rgb(4, 4, 5), Color.TRANSPARENT),
            floatArrayOf(0f, 0.08f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, PitStopReviewTheme.Hero.width, PitStopReviewTheme.Hero.height, fallback)
        fallback.shader = null
        bands.forEach { band ->
            fallback.color = Color.WHITE
            fallback.alpha = (255f * band[2] * (1.35f - 0.35f * speed)).toInt().coerceIn(0, 255)
            fallback.strokeWidth = band[1] * 2f
            canvas.drawLine(0f, band[0], PitStopReviewTheme.Hero.width, band[0], fallback)
        }
        if (PitStopReviewMotion.speedLines(scene) > 0.001) {
            val strength = PitStopReviewMotion.speedLines(scene).toFloat()
            for (row in 0 until 138) {
                if (hash(row.toFloat()) < 0.9f) continue
                val y = row * 1.6f
                val phase = hash(row + floor(scene * 12.0).toFloat())
                val length = 18f + 56f * phase
                val x = ((row * 37f + scene.toFloat() * 540f) % 470f) - 70f
                fallback.alpha = (255f * strength * (0.25f + 0.45f * hash(row + 9f))).toInt().coerceIn(0, 255)
                fallback.strokeWidth = 1f
                canvas.drawLine(x, y, x + length, y, fallback)
            }
        }
    }

    private fun hash(value: Float): Float {
        val number = sin(value * 91.345f + 47.853f) * 43758.5453f
        return number - floor(number)
    }
}
