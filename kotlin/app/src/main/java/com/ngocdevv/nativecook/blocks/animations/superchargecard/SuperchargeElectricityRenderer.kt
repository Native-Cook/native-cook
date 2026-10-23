package com.ngocdevv.nativecook.blocks.animations.superchargecard

import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class SuperchargeElectricityRenderer {
    private val path = Path()
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    private val halos =
        if (Build.VERSION.SDK_INT >= 31)
            Array(12) { RenderNode("Supercharge electrical bloom $it") }
        else null
    private var nodeIndex = 0

    fun draw(canvas: Canvas, s: SuperchargeState) {
        nodeIndex = 0
        canvas.save()
        canvas.translate(0f, s.sink.toFloat())
        weight(canvas, s.lightning.main, 0, s.lightning.level, s.lightning.firstPulse)
        weight(canvas, s.lightning.branch, 1, s.lightning.forkLevel ?: s.lightning.level)
        weight(canvas, s.lightning.twig, 2, s.lightning.forkLevel ?: s.lightning.level)
        canvas.restore()
        weight(canvas, s.lightning.ground, 1, s.lightning.level)
        for (bucket in -1..2) {
            val from =
                when (bucket) {
                    -1,
                    1 -> .22
                    0 -> .55
                    else -> 0.0
                }
            val below =
                when (bucket) {
                    -1,
                    0 -> 2.0
                    1 -> .55
                    else -> .22
                }
            path.rewind()
            for (i in s.sparks.indices step 5) {
                val life = s.sparks[i + 4]
                if (life <= 0 || life < from || life >= below) continue
                val speed = max(1.0, hypot(s.sparks[i + 2], s.sparks[i + 3]))
                val tail = max(M.spark.minLength, speed * M.spark.streak) * life / speed
                path.moveTo(s.sparks[i].toFloat(), s.sparks[i + 1].toFloat())
                path.lineTo(
                    (s.sparks[i] - s.sparks[i + 2] * tail).toFloat(),
                    (s.sparks[i + 1] - s.sparks[i + 3] * tail).toFloat(),
                )
            }
            val width =
                when (bucket) {
                    -1 -> 3f
                    0 -> 1.4f
                    1 -> 1f
                    else -> .7f
                }
            val alpha =
                when (bucket) {
                    -1 -> .5
                    0 -> 1.0
                    1 -> .75
                    else -> .4
                }
            val color =
                when (bucket) {
                    0 -> Color.WHITE
                    1 -> Color.rgb(255, 242, 122)
                    else -> Color.rgb(255, 196, 0)
                }
            stroke(canvas, width, if (bucket < 0) 4f else 0f, color, alpha)
        }
    }

    private fun weight(
        canvas: Canvas,
        lines: List<Double>,
        weight: Int,
        level: Double,
        impact: Boolean = false,
    ) {
        if (lines.isEmpty() || level <= 0) return
        path.rewind()
        for (i in lines.indices step 4) {
            path.moveTo(lines[i].toFloat(), lines[i + 1].toFloat())
            path.lineTo(lines[i + 2].toFloat(), lines[i + 3].toFloat())
        }
        val widths =
            if (impact) floatArrayOf(22f, 8f, 2.8f)
            else
                when (weight) {
                    0 -> floatArrayOf(16f, 5.5f, 2f)
                    1 -> floatArrayOf(7f, 2.2f, .9f)
                    else -> floatArrayOf(4.5f, 1.4f, .55f)
                }
        val blurs =
            when (weight) {
                0 -> floatArrayOf(12f, 3f, 0f)
                1 -> floatArrayOf(9f, 2.2f, 0f)
                else -> floatArrayOf(6f, 1.5f, 0f)
            }
        val alphas =
            when (weight) {
                0 -> doubleArrayOf(.42, .9, 1.0)
                1 -> doubleArrayOf(.24, .7, 1.0)
                else -> doubleArrayOf(.18, .6, 1.0)
            }
        val colors = intArrayOf(Color.rgb(255, 196, 0), Color.rgb(255, 242, 122), Color.WHITE)
        for (i in 0..2) stroke(canvas, widths[i], blurs[i], colors[i], alphas[i] * level)
    }

    private fun stroke(canvas: Canvas, width: Float, blur: Float, color: Int, alpha: Double) {
        paint.strokeWidth = width
        paint.color = color
        paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        if (
            blur > 0 && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated && halos != null
        ) {
            val node = halos[nodeIndex++]
            node.setPosition(-64, -64, 466, 964)
            node.setUseCompositingLayer(
                true,
                Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) },
            )
            node.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL))
            val recording = node.beginRecording(530, 1028)
            recording.translate(64f, 64f)
            recording.drawPath(path, paint)
            node.endRecording()
            canvas.drawRenderNode(node)
        } else {
            paint.maskFilter =
                if (blur > 0) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
            canvas.drawPath(path, paint)
            paint.maskFilter = null
        }
    }
}
