package com.ngocdevv.nativecook.blocks.inputs.agentstatuspill

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** The same sRGB shader sampled at half dp resolution for Android 8–12. */
internal class AgentStatusPillBitmap : AgentStatusPillPainter {
    private val bitmap = Bitmap.createBitmap(150, 42, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(150 * 42)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private val clip = Path()
    private val color = DoubleArray(3)
    private var previousTick = -1

    override fun draw(canvas: Canvas, width: Float, height: Float, time: Double) {
        val loopTime = ((time % 8.8) + 8.8) % 8.8
        // 264 samples per loop: identical first/last state and at most 30 CPU updates/s.
        val tick = floor(loopTime * 30).toInt().coerceIn(0, 263)
        if (tick != previousTick) {
            render(tick / 30.0)
            previousTick = tick
        }
        bounds.set(0f, 0f, width, height)
        clip.rewind()
        clip.addRoundRect(bounds, height / 2, height / 2, Path.Direction.CW)
        val checkpoint = canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(bitmap, null, bounds, paint)
        canvas.restoreToCount(checkpoint)
    }

    private fun render(time: Double) {
        val phase = time / 8.8 * 2 * PI
        val first = floor(time / 2.2).toInt() % 4
        val second = (first + 1) % 4
        val firstWeight = agentStatusPillMode(time, first).toDouble()
        val secondWeight = agentStatusPillMode(time, second).toDouble()
        var pixel = 0
        for (y in 0 until 42) {
            val qy = (y + 0.5) / 42
            val highlight = 0.22 * smooth(0.42, 0.0, qy) * smooth(0.0, 0.08, qy)
            val lowerEdge = 1 - 0.18 * smooth(0.7, 1.0, qy)
            for (x in 0 until 150) {
                val qx = (x + 0.5) / 150
                sample(first, qx, qy, phase)
                var red = color[0] * firstWeight
                var green = color[1] * firstWeight
                var blue = color[2] * firstWeight
                if (secondWeight > 0) {
                    sample(second, qx, qy, phase)
                    red += color[0] * secondWeight
                    green += color[1] * secondWeight
                    blue += color[2] * secondWeight
                }
                val r = (((red + highlight) * lowerEdge).coerceIn(0.0, 1.0) * 255).roundToInt()
                val g = (((green + highlight) * lowerEdge).coerceIn(0.0, 1.0) * 255).roundToInt()
                val b = (((blue + highlight) * lowerEdge).coerceIn(0.0, 1.0) * 255).roundToInt()
                pixels[pixel++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, 150, 0, 0, 150, 42)
    }

    /** Writes one of the four original shader formulas without allocating per pixel. */
    private fun sample(mode: Int, x: Double, y: Double, phase: Double) {
        when (mode) {
            0 -> {
                val wave = 0.55 + 0.22 * sin(x * 5 + phase * 2) + 0.08 * sin(x * 11 - phase * 3)
                val distance = (y - wave) / 0.22
                val band = exp(-distance * distance)
                val firstHue = smooth(0.1, 0.55, x)
                val secondHue = smooth(0.55, 0.95, x)
                val r = mix(mix(1.0, 0.45, firstHue), 0.72, secondHue)
                val g = mix(mix(0.55, 1.0, firstHue), 0.25, secondHue)
                val b = mix(mix(0.12, 0.45, firstHue), 1.0, secondHue)
                val glow = band * 1.1 + smooth(0.75, 1.0, y) * 0.5
                color[0] = mix(0.06, 0.16, y) + r * glow
                color[1] = mix(0.03, 0.08, y) + g * glow
                color[2] = mix(0.16, 0.3, y) + b * glow
            }
            1 -> {
                val column = floor(x * 6)
                val shade = 0.12 + 0.08 * sin(column * 2.7 + phase * 2) +
                    0.06 * sin(y * 6 + column + phase * 3)
                val sweepX = x - phase / 6.2831853
                val distance = ((sweepX - floor(sweepX)) - 0.5) / 0.12
                val sweep = exp(-distance * distance)
                val smoke = 0.08 * sin(x * 9 + y * 5 - phase * 4)
                val value = shade + smoke + 0.35 * sweep
                color[0] = value * 0.95
                color[1] = value * 0.95
                color[2] = value
            }
            2 -> {
                val edge = 0.5 + 0.12 * sin(y * 4 + phase * 2) + 0.06 * sin(y * 9 - phase * 3)
                val transition = smooth(edge - 0.08, edge + 0.08, x)
                val left = smooth(0.0, edge, x)
                val right = smooth(edge, 1.0, x)
                color[0] = mix(mix(0.1, 0.15, left), mix(1.0, 0.85, right), transition)
                color[1] = mix(mix(0.35, 0.85, left), mix(0.25, 0.05, right), transition)
                color[2] = mix(1.0, mix(0.3, 0.15, right), transition)
            }
            else -> {
                val stripe = 0.5 + 0.5 * sin(
                    (x * 2 + y * 0.8 + 0.08 * sin(y * 6 + phase * 2)) * 6.2831853 - phase * 3,
                )
                val amount = 0.35 * smooth(0.1, 0.9, stripe) + 0.55 * x
                color[0] = mix(0.0, 0.55, amount)
                color[1] = mix(0.55, 0.95, amount)
                color[2] = mix(0.52, 0.4, amount)
            }
        }
    }

    private fun smooth(start: Double, end: Double, value: Double): Double {
        val u = ((value - start) / (end - start)).coerceIn(0.0, 1.0)
        return u * u * (3 - 2 * u)
    }

    private fun mix(start: Double, end: Double, amount: Double): Double = start + (end - start) * amount
}
