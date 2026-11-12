package com.ngocdevv.nativecook.blocks.cards.glasspaymorph

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.*

/** Android 8–12 fallback: identical geometry/timeline; software frost in place of the rim shader. */
internal class GlassPayMorphFallback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val frosted = Bitmap.createBitmap(604, 874, Bitmap.Config.ARGB_8888)
    private val frostCanvas = Canvas(frosted)
    private val frostFilter = BlurMaskFilter(((20f - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)

    private fun alpha(a: Double) = (255 * a.coerceIn(0.0, 1.0)).roundToInt()
    private fun mix(a: Int, b: Int, k: Double): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).roundToInt(),
    )
    private fun reset() {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
    }

    private fun page(c: Canvas, top: Float, height: Float) {
        reset()
        paint.color = Color.rgb(27, 33, 53)
        c.drawRect(0f, top, 402f, top + height, paint)
        paint.shader = RadialGradient(40f, 150f, 460f,
            intArrayOf(Color.rgb(38, 45, 65), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, top, 402f, top + height, paint)
        paint.shader = RadialGradient(402f, 874f, 520f,
            intArrayOf(Color.argb(153, 23, 26, 46), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, top, 402f, top + height, paint)
        paint.shader = null
    }

    /** Exact tangent envelope of each consecutive pair of unequal trail discs. */
    private fun trail(points: DoubleArray): Path {
        val path = Path()
        for (i in 0 until GlassPayMorphMotion.TRAIL) {
            val x = points[i * 3]
            val y = points[i * 3 + 1]
            val r = points[i * 3 + 2]
            path.addCircle(x.toFloat(), y.toFloat(), r.toFloat(), Path.Direction.CW)
            if (i == 0) continue
            val ax = points[(i - 1) * 3]
            val ay = points[(i - 1) * 3 + 1]
            val ar = points[(i - 1) * 3 + 2]
            val dx = x - ax
            val dy = y - ay
            val length = hypot(dx, dy)
            if (length <= abs(ar - r) + 1e-5) continue
            val ux = dx / length
            val uy = dy / length
            val q = (ar - r) / length
            val h = sqrt(max(0.0, 1 - q * q))
            val nx0 = q * ux - h * uy
            val ny0 = q * uy + h * ux
            val nx1 = q * ux + h * uy
            val ny1 = q * uy - h * ux
            path.moveTo((ax + ar * nx0).toFloat(), (ay + ar * ny0).toFloat())
            path.lineTo((ax + ar * nx1).toFloat(), (ay + ar * ny1).toFloat())
            path.lineTo((x + r * nx1).toFloat(), (y + r * ny1).toFloat())
            path.lineTo((x + r * nx0).toFloat(), (y + r * ny0).toFloat())
            path.close()
        }
        return path
    }

    private fun light(c: Canvas, s: GlassPayMorphMotion.Scene, amber: Boolean, opacity: Double, blurred: Boolean = false) {
        if (opacity <= 0.001) return
        val points = if (amber) s.a else s.rd
        val hi = if (amber) mix(Color.rgb(244, 187, 88), Color.rgb(232, 159, 31), s.dock)
        else mix(Color.rgb(229, 40, 79), Color.rgb(214, 0, 43), s.dock)
        val lo = if (amber) mix(Color.rgb(238, 166, 53), Color.rgb(232, 159, 31), s.dock)
        else mix(Color.rgb(214, 4, 51), Color.rgb(222, 11, 58), s.dock)
        reset()
        val radius = points[2].toFloat()
        val dx = if (amber) 0.7071f * radius else radius * (0.7071 + 0.2929 * s.dock).toFloat()
        val dy = if (amber) 0.7071f * radius else radius * (0.7071 * (1 - s.dock)).toFloat()
        paint.shader = LinearGradient(points[0].toFloat() - dx, points[1].toFloat() - dy,
            points[0].toFloat() + dx, points[1].toFloat() + dy, hi, lo, Shader.TileMode.CLAMP)
        paint.alpha = alpha(opacity)
        paint.maskFilter = if (blurred) frostFilter else null
        c.drawPath(trail(points), paint)
        paint.maskFilter = null
    }

    private fun pools(c: Canvas, s: GlassPayMorphMotion.Scene) {
        for (amber in listOf(false, true)) {
            val points = if (amber) s.a else s.rd
            reset()
            val color = if (amber) Color.argb(14, 244, 187, 88) else Color.argb(14, 229, 40, 79)
            paint.shader = RadialGradient(points[0].toFloat(), points[1].toFloat(), (points[2] * 2.2).toFloat(),
                intArrayOf(color, Color.TRANSPARENT), floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(points[0].toFloat(), points[1].toFloat(), (points[2] * 2.2).toFloat(), paint)
        }
    }

    fun background(c: Canvas, s: GlassPayMorphMotion.Scene, top: Float, height: Float) {
        page(c, top, height)
        pools(c, s)
        light(c, s, false, s.frostR)
        light(c, s, true, s.frostA)
        val glass = Path().apply {
            addRoundRect(RectF(0f, 0f, s.w.toFloat(), s.h.toFloat()), s.r.toFloat(), s.r.toFloat(), Path.Direction.CW)
        }
        matrix.setValues(GlassPayMorphMotion.cardMatrix(s).map { it.toFloat() }.toFloatArray())
        glass.transform(matrix)
        c.save()
        c.clipPath(glass)
        page(c, top, height)
        pools(c, s)
        frosted.eraseColor(Color.TRANSPARENT)
        frostCanvas.save()
        frostCanvas.translate(100f, 0f)
        light(frostCanvas, s, false, s.frostR * 0.86, true)
        light(frostCanvas, s, true, s.frostA * 0.8, true)
        frostCanvas.restore()
        reset()
        c.drawBitmap(frosted, -100f, 0f, paint)
        paint.color = Color.WHITE
        paint.alpha = alpha(s.tint)
        c.drawPath(glass, paint)
        if (s.sheen >= 0) {
            c.save()
            c.concat(matrix)
            val x = s.w / 2 + (s.w / 2 + 60) * (1 - 2 * s.sheen)
            reset()
            paint.shader = LinearGradient((x - 20).toFloat(), 0f, (x + 20).toFloat(), 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(alpha(0.22 * sin(PI * s.sheen)), 255, 255, 255), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, s.w.toFloat(), s.h.toFloat(), paint)
            c.restore()
        }
        c.restore()
        reset()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f
        paint.shader = LinearGradient(70f, 320f, 330f, 500f,
            intArrayOf(Color.argb(125, 255, 255, 255), Color.argb(68, 255, 255, 255), Color.argb(153, 255, 255, 255)),
            null, Shader.TileMode.CLAMP)
        c.drawPath(glass, paint)
    }

    fun overlay(c: Canvas, s: GlassPayMorphMotion.Scene) {
        light(c, s, false, 1 - s.frostR)
        light(c, s, true, 1 - s.frostA)
        val both = (1 - s.frostA) * (1 - s.frostR)
        if (both <= 0.001) return
        val overlap = trail(s.a)
        overlap.op(trail(s.rd), Path.Op.INTERSECT)
        reset()
        paint.color = mix(Color.rgb(242, 148, 45), Color.rgb(255, 178, 58), (0.35 + s.flash).coerceIn(0.0, 1.0))
        paint.alpha = alpha(both)
        c.drawPath(overlap, paint)
    }
}
