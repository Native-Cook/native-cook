package com.ngocdevv.nativecook.blocks.pickers.iconerascrubber

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small flat-shape rendering path for API 26–32, where RuntimeShader is unavailable. It keeps
 * the same page lights, era colours and envelope/plane silhouette without pretending to ray-march.
 */
internal class IconEraScrubberFallback {
    private val page = Paint(Paint.ANTI_ALIAS_FLAG)
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slab = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slabEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val symbol = Paint(Paint.ANTI_ALIAS_FLAG)
    private val symbolStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconRect = RectF()
    private val envelopeRect = RectF()
    private val path = Path()

    fun draw(canvas: Canvas, layout: IconEraScrubberTheme.Layout, year: Double, lean: Double, phase: Double) {
        drawPage(canvas, layout.width, layout.height, phase)

        val e = IconEraScrubberMotion.eraOf(year).toFloat().coerceIn(0f, 3f)
        val w = eraWeights(e)
        val cx = layout.iconX
        val cy = layout.iconY
        val half = IconEraScrubberTheme.IconHalf
        val radius = half * (0.34f * w[0] + 0.44f * w[1] + 0.46f * w[2] + 0.46f * w[3])
        val fold = smoothstep(2.15f, 2.85f, e)
        val yaw = 0.14f + 0.35f * lean.toFloat()

        canvas.save()
        canvas.rotate(yaw * 38f, cx, cy)
        shadow.color = argb((255f * (0.18f + 0.22f * (1f - w[1]))).toInt(), 0, 0, 0)
        canvas.drawRoundRect(RectF(cx - half + 6f, cy - half + 12f, cx + half + 6f, cy + half + 12f), radius, radius, shadow)

        iconRect.set(cx - half, cy - half, cx + half, cy + half)
        slab.shader = LinearGradient(
            cx, cy - half, cx, cy + half,
            blendColors(
                intArrayOf(rgb(0.03f, 0.22f, 0.70f), rgb(0.25f, 0.58f, 1f)),
                intArrayOf(rgb(0.07f, 0.45f, 1f), rgb(0.30f, 0.75f, 1f)),
                intArrayOf(rgb(0.10f, 0.42f, 0.98f), rgb(0.38f, 0.72f, 1f)),
                intArrayOf(rgb(0.68f, 0.78f, 0.92f), rgb(0.92f, 0.97f, 1f)),
                weights = w,
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        slab.alpha = 255
        canvas.drawRoundRect(iconRect, radius, radius, slab)
        slab.shader = null

        if (w[3] > 0.01f) {
            slabEdge.color = argb((70f + 120f * w[3]).toInt(), 220, 235, 255)
            canvas.drawRoundRect(iconRect, radius, radius, slabEdge)
        }

        val iw = half * 0.6f
        val ih = half * 0.41f
        envelopeRect.set(cx - iw, cy - ih + 2f, cx + iw, cy + ih + 2f)
        val paperColor = blendArgb(
            rgb(0.99f, 0.96f, 0.88f),
            rgb(1f, 1f, 1f),
            (w[1] + w[2] + w[3]).coerceIn(0f, 1f),
        )
        symbol.color = paperColor
        symbol.alpha = (255f * (1f - fold)).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(envelopeRect, 5f, 5f, symbol)
        symbol.shader = null
        symbolStroke.color = argb((255f * (1f - fold)).toInt().coerceIn(0, 255), 75, 105, 180)
        path.rewind()
        path.moveTo(cx - iw + 6f, cy - ih + 7f)
        path.lineTo(cx, cy + 5f)
        path.lineTo(cx + iw - 6f, cy - ih + 7f)
        canvas.drawPath(path, symbolStroke)
        if (w[0] > 0.01f) {
            symbolStroke.color = argb((170f * w[0] * (1f - fold)).toInt().coerceIn(0, 255), 210, 62, 70)
            symbolStroke.strokeWidth = 3f
            canvas.drawLine(cx - iw, cy - ih + 5f, cx - iw + 18f, cy - ih + 5f, symbolStroke)
            symbolStroke.color = argb((170f * w[0] * (1f - fold)).toInt().coerceIn(0, 255), 48, 100, 210)
            canvas.drawLine(cx + iw - 18f, cy + ih - 5f, cx + iw, cy + ih - 5f, symbolStroke)
        }
        if (w[0] > 0.01f) {
            symbol.color = rgb(0.90f, 0.25f, 0.20f)
            symbol.alpha = (220f * w[0] * (1f - fold)).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(RectF(cx + iw - 25f, cy - ih + 10f, cx + iw - 6f, cy - ih + 32f), 2f, 2f, symbol)
        }

        if (fold > 0.001f) {
            symbol.color = rgb(0.92f, 0.96f, 1f)
            symbol.alpha = (230f * fold).toInt().coerceIn(0, 255)
            path.rewind()
            path.moveTo(cx + iw, cy - ih + 3f)
            path.lineTo(cx - iw + 3f, cy - 2f)
            path.lineTo(cx - 24f, cy + ih - 5f)
            path.lineTo(cx + 1f, cy + 8f)
            path.close()
            canvas.drawPath(path, symbol)
            symbolStroke.color = argb((190f * fold).toInt().coerceIn(0, 255), 215, 232, 255)
            symbolStroke.strokeWidth = 1.8f
            canvas.drawPath(path, symbolStroke)
        }
        canvas.restore()
    }

    private fun drawPage(canvas: Canvas, width: Float, height: Float, phase: Double) {
        page.shader = null
        page.color = rgb(0.035f, 0.037f, 0.06f)
        canvas.drawRect(0f, 0f, width, height, page)
        val aX = width * (0.22f + 0.04f * sin(phase).toFloat())
        val aY = height * (0.30f + 0.03f * cos(phase).toFloat())
        val bX = width * (0.80f + 0.03f * cos(phase + 2.0).toFloat())
        val bY = height * (0.52f + 0.04f * sin(phase + 1.0).toFloat())
        light.shader = RadialGradient(aX, aY, width * 0.24f, rgb(1f, 0.45f, 0.20f, 0.45f), 0, Shader.TileMode.CLAMP)
        canvas.drawCircle(aX, aY, width * 0.24f, light)
        light.shader = RadialGradient(bX, bY, width * 0.26f, rgb(0.20f, 0.60f, 1f, 0.45f), 0, Shader.TileMode.CLAMP)
        canvas.drawCircle(bX, bY, width * 0.26f, light)
        light.shader = null
    }

    private fun eraWeights(e: Float): FloatArray {
        val w0 = 1f - smoothstep(0f, 1f, e.coerceIn(0f, 1f))
        val w3 = smoothstep(2f, 3f, e)
        val w1 = (1f - w0 - smoothstep(1f, 2f, e)).coerceAtLeast(0f)
        val w2 = (1f - w0 - w1 - w3).coerceAtLeast(0f)
        return floatArrayOf(w0, w1, w2, w3)
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val u = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return u * u * (3f - 2f * u)
    }

    private fun rgb(r: Float, g: Float, b: Float, alpha: Float = 1f): Int = argb(
        (alpha * 255f).toInt().coerceIn(0, 255),
        (r * 255f).toInt().coerceIn(0, 255),
        (g * 255f).toInt().coerceIn(0, 255),
        (b * 255f).toInt().coerceIn(0, 255),
    )

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun blendArgb(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return argb(
            ((a ushr 24) + (((b ushr 24) - (a ushr 24)) * k)).toInt(),
            (((a shr 16 and 255) + ((b shr 16 and 255) - (a shr 16 and 255)) * k)).toInt(),
            (((a shr 8 and 255) + ((b shr 8 and 255) - (a shr 8 and 255)) * k)).toInt(),
            (((a and 255) + ((b and 255) - (a and 255)) * k)).toInt(),
        )
    }

    private fun blendColors(vararg colors: IntArray, weights: FloatArray): IntArray {
        val result = IntArray(2)
        for (i in 0..1) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (j in colors.indices) {
                val weight = weights[j]
                r += (colors[j][i] shr 16 and 255) / 255f * weight
                g += (colors[j][i] shr 8 and 255) / 255f * weight
                b += (colors[j][i] and 255) / 255f * weight
            }
            result[i] = rgb(r, g, b)
        }
        return result
    }
}
