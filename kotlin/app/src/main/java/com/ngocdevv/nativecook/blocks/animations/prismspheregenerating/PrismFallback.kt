package com.ngocdevv.nativecook.blocks.animations.prismspheregenerating

import android.graphics.*
import kotlin.math.*

/** API 26–32: a still, projected pleated shell with the same layout and touch glow. */
internal class PrismFallback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val pose = PrismPose.at(0.58)

    fun backdrop(canvas: Canvas) {
        paint.shader = RadialGradient(201f, 654f, 244f, intArrayOf(0xFF210A46.toInt(), Color.BLACK), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 284f, 402f, 874f, paint)
        paint.shader = null
    }

    fun sphere(canvas: Canvas, flash: Double) {
        paint.shader = RadialGradient(179f, 402f, 191f,
            intArrayOf(0xFFE8D9F8.toInt(), 0xFF725091.toInt(), 0xFF3B168B.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(201f, 472f, 124f, paint)
        paint.shader = null
        canvas.save()
        path.reset()
        path.addCircle(201f, 472f, 124f, Path.Direction.CW)
        canvas.clipPath(path)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        for (rib in 0..59) {
            val az = -pose.fan / 2 + rib * pose.fan / 59
            path.reset()
            var started = false
            for (step in 0..90) {
                val polar = step * PI / 90
                val x = sin(polar) * sin(az)
                val y = -cos(polar)
                val z = -sin(polar) * cos(az)
                val m = pose.rotation
                val viewZ = m[6] * x + m[7] * y + m[8] * z
                if (viewZ < 0) {
                    started = false
                    continue
                }
                val px = (201 + 123 * (m[0] * x + m[1] * y + m[2] * z)).toFloat()
                val py = (472 + 123 * (m[3] * x + m[4] * y + m[5] * z)).toFloat()
                if (!started) path.moveTo(px, py) else path.lineTo(px, py)
                started = true
            }
            paint.color = 0x66DAC7FF
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = (flash * 155).toInt().coerceIn(0, 255)
        canvas.drawCircle(201f, 472f, 124f, paint)
        paint.alpha = 255
        canvas.restore()
    }
}
