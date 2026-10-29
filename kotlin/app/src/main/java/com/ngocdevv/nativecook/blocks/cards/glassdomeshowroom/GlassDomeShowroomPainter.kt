package com.ngocdevv.nativecook.blocks.cards.glassdomeshowroom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Draws the screen in the 393-wide design space (the caller scales the canvas). */
internal class GlassDomeShowroomPainter(private val images: List<Bitmap?>) {
    private val m = GlassDomeShowroomMotion
    private val art = GlassDomeShowroomArt

    private fun font(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
        else Typeface.create("sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val headline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font(900)
        textSize = 76f
        letterSpacing = -1f / 76f
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
    }
    private val labelBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font(700)
        textSize = 20f
        setShadowLayer(5f, 0f, 1f, art.argb(0x000000, 0.22))
    }
    private val labelRegular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font(400)
        textSize = 20f
        setShadowLayer(5f, 0f, 1f, art.argb(0x000000, 0.22))
    }
    private val rect = RectF()
    private val jarPath = Path()
    private val innerPath = Path()
    private val farArc = Path()
    private val nearArc = Path()
    private val domeArc = Path()
    private val heartPath = Path()
    private val ring = Path()
    private val clipRect = RectF()

    init {
        val w = m.JAR_WIDTH.toFloat()
        val h = m.JAR_HEIGHT.toFloat()
        val r = m.JAR_RADIUS.toFloat()
        val top = 4f - h
        jarPath.addRoundRect(RectF(-w / 2, top, w / 2, 4f), floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
        innerPath.addRoundRect(RectF(-w / 2 + 4, top + 4, w / 2 - 4, 8f), floatArrayOf(r - 4, r - 4, r - 4, r - 4, 0f, 0f, 0f, 0f), Path.Direction.CW)
        for (s in 0..40) {
            val a = PI * s / 40
            val xf = (w / 2 * cos(a + PI)).toFloat()
            val yf = (4 + 12 * sin(a + PI)).toFloat()
            if (s == 0) farArc.moveTo(xf, yf) else farArc.lineTo(xf, yf)
            val xn = (w / 2 * cos(a)).toFloat()
            val yn = (4 + 12 * sin(a)).toFloat()
            if (s == 0) nearArc.moveTo(xn, yn) else nearArc.lineTo(xn, yn)
        }
        for (s in 0..24) {
            val a = (188 + 72.0 * s / 24) * PI / 180
            val x = (-w / 2 + r + (r - 6) * cos(a)).toFloat()
            val y = (top + r + (r - 6) * sin(a)).toFloat()
            if (s == 0) domeArc.moveTo(x, y) else domeArc.lineTo(x, y)
        }
    }

    /** A paint for a shader: opaque first, so it does not keep the alpha of a previous colour. */
    private fun shaded(shader: Shader): Paint {
        fill.shader = null
        fill.color = 0xFF000000.toInt()
        fill.shader = shader
        return fill
    }

    private fun solid(color: Int): Paint {
        fill.shader = null
        fill.color = color
        return fill
    }

    private fun vertical(y0: Float, y1: Float, colors: IntArray, stops: FloatArray?) =
        LinearGradient(0f, y0, 0f, y1, colors, stops, Shader.TileMode.CLAMP)

    private fun horizontal(x0: Float, x1: Float, colors: IntArray, stops: FloatArray?) =
        LinearGradient(x0, 0f, x1, 0f, colors, stops, Shader.TileMode.CLAMP)

    private fun white(a: Double) = art.argb(0xFFFFFF, a)

    /** The wash and the floor's spotlight. */
    fun drawBackground(canvas: Canvas, widthPt: Float, heightPt: Float, wash: IntArray, floorY: Double) {
        canvas.drawRect(0f, 0f, widthPt, heightPt, shaded(vertical(0f, heightPt,
            intArrayOf(art.argb(wash[0]), art.argb(wash[1]), art.argb(wash[2])), floatArrayOf(0f, 0.55f, 1f))))
        canvas.save()
        canvas.translate(m.CENTRE_X.toFloat(), floorY.toFloat() + 6)
        canvas.scale(1f, 0.22f)
        canvas.drawCircle(0f, 0f, 210f, shaded(RadialGradient(0f, 0f, 210f, white(0.16), white(0.0), Shader.TileMode.CLAMP)))
        canvas.restore()
    }

    /** The stacked MORE headline in its 393 × 236 window, rows shifted up by [shift] pt, edges faded. */
    fun drawHeadline(canvas: Canvas, shift: Double) {
        canvas.saveLayer(0f, 0f, 393f, 236f, null)
        val fm = headline.fontMetrics
        val centre = -(fm.ascent + fm.descent) / 2
        for (n in -1..4) {
            canvas.drawText("MORE", m.CENTRE_X.toFloat(), (40 + n * m.HEADLINE_PITCH - shift).toFloat() + centre, headline)
        }
        mask.shader = vertical(0f, 236f,
            intArrayOf(white(0.0), white(1.0), white(1.0), white(0.25), white(0.0)),
            floatArrayOf(0f, 14f / 236, 182f / 236, 212f / 236, 1f))
        canvas.drawRect(0f, 0f, 393f, 236f, mask)
        canvas.restore()
    }

    private fun softEllipse(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float, alpha: Double) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(1f, ry / rx)
        canvas.drawCircle(0f, 0f, rx, shaded(RadialGradient(0f, 0f, rx,
            intArrayOf(art.argb(0, alpha), art.argb(0, alpha * 0.55), art.argb(0, 0.0)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)))
        canvas.restore()
    }

    fun drawJar(canvas: Canvas, jar: GlassDomeShowroomMotion.Jar, floorY: Double, wash: IntArray, liked: Boolean, popAge: Double) {
        val car = art.cars[jar.index]
        val bitmap = images.getOrNull(jar.index)
        val aspect = if (bitmap != null && bitmap.height > 0) bitmap.width.toFloat() / bitmap.height else 2.55f
        val carW = m.CAR_WIDTH.toFloat()
        val carH = carW / aspect
        val w = m.JAR_WIDTH.toFloat()
        val h = m.JAR_HEIGHT.toFloat()
        val top = 4f - h
        val shift = (-22 * jar.d).toFloat()

        canvas.save()
        canvas.translate(jar.x.toFloat(), floorY.toFloat())
        canvas.scale(jar.scale.toFloat(), jar.scale.toFloat())

        // Mirror image on the glossy floor.
        if (bitmap != null) {
            canvas.saveLayer(-carW / 2 - 4, -2f, carW / 2 + 4, carH + 2, null)
            canvas.save()
            canvas.scale(1f, -1f)
            rect.set(-carW / 2, -carH + 1, carW / 2, 1f)
            canvas.drawBitmap(bitmap, null, rect, image)
            canvas.restore()
            mask.shader = vertical(0f, 48f, intArrayOf(art.argb(0, 0.30), art.argb(0, 0.0)), null)
            canvas.drawRect(-carW / 2 - 4, -2f, carW / 2 + 4, carH + 2, mask)
            canvas.restore()
        }

        // Back of the glass: the wash refracted lighter, frosted, brighter at the edges (Fresnel).
        canvas.save()
        canvas.clipPath(jarPath)
        canvas.drawPath(jarPath, shaded(vertical(top, 4f, intArrayOf(art.argb(wash[0], 0.45), art.argb(wash[0], 0.1)), null)))
        canvas.drawPath(jarPath, shaded(vertical(top, 4f, intArrayOf(white(0.13), white(0.05), white(0.09)), floatArrayOf(0f, 0.55f, 1f))))
        canvas.drawPath(jarPath, shaded(horizontal(-w / 2, w / 2,
            intArrayOf(white(0.22), white(0.05), white(0.0), white(0.0), white(0.05), white(0.2)),
            floatArrayOf(0f, 0.09f, 0.24f, 0.78f, 0.91f, 1f))))
        rect.set(-w / 2, 4f - 12, w / 2, 4f + 12)
        canvas.drawOval(rect, solid(white(0.06)))
        canvas.restore()
        stroke.shader = null
        stroke.strokeWidth = 1f
        stroke.color = white(0.22)
        canvas.drawPath(farArc, stroke)

        // The label behind the glass.
        val tx = -w / 2 + 24
        val ty = top + 30
        labelBold.color = white(1.0)
        labelRegular.color = white(0.92)
        val asc = -labelBold.fontMetrics.ascent
        canvas.drawText("The new", tx, ty + asc, labelBold)
        canvas.drawText(car.model, tx, ty + 24 + asc, labelBold)
        canvas.drawText(car.trim, tx, ty + 48 - labelRegular.fontMetrics.ascent, labelRegular)
        drawHeart(canvas, liked, popAge)

        // The car on its contact shadow.
        softEllipse(canvas, 0f, 0f, 130f, 15f, 0.32)
        softEllipse(canvas, 0f, -1f, 104f, 5f, 0.6)
        if (bitmap != null) {
            rect.set(-carW / 2, -carH + 1, carW / 2, 1f)
            canvas.drawBitmap(bitmap, null, rect, image)
        }

        // Front of the glass: specular streaks that slide as the jar moves, the dome's highlight.
        canvas.save()
        canvas.clipPath(jarPath)
        canvas.drawPath(jarPath, shaded(LinearGradient(-w / 2, top, w / 2, 4f, intArrayOf(white(0.07), white(0.0), white(0.0)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)))
        val broadX = -w / 2 + 30 + shift
        rect.set(broadX, top + 34, broadX + 34, top + 34 + h - 70)
        canvas.drawRoundRect(rect, 17f, 17f, shaded(horizontal(broadX, broadX + 34,
            intArrayOf(white(0.0), white(0.2), white(0.0)), floatArrayOf(0f, 0.45f, 1f))))
        rect.set(broadX + 42, top + 52, broadX + 45.5f, top + 52 + h - 120)
        canvas.drawRoundRect(rect, 1.75f, 1.75f, shaded(vertical(top + 52, top + h - 68, intArrayOf(white(0.42), white(0.06)), null)))
        val rightX = w / 2 - 26 + 0.6f * shift
        rect.set(rightX, top + 46, rightX + 10, top + 46 + h - 96)
        canvas.drawRoundRect(rect, 5f, 5f, shaded(horizontal(rightX, rightX + 10,
            intArrayOf(white(0.0), white(0.16), white(0.0)), floatArrayOf(0f, 0.5f, 1f))))
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeWidth = 10f
        stroke.color = white(0.14)
        canvas.drawPath(domeArc, stroke)
        stroke.strokeWidth = 2.4f
        stroke.color = white(0.6)
        canvas.drawPath(domeArc, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
        canvas.restore()

        // The rim: a faint chromatic fringe, the bright edge, and the glass's inner edge.
        canvas.save()
        clipRect.set(-w / 2 - 6, top - 6, w / 2 + 6, top - 6 + h + 3)
        canvas.clipRect(clipRect)
        stroke.strokeWidth = 1f
        canvas.save()
        canvas.translate(-0.9f, 0f)
        stroke.color = android.graphics.Color.argb(71, 140, 242, 255)
        canvas.drawPath(jarPath, stroke)
        canvas.restore()
        canvas.save()
        canvas.translate(0.9f, 0f)
        stroke.color = android.graphics.Color.argb(56, 255, 153, 191)
        canvas.drawPath(jarPath, stroke)
        canvas.restore()
        stroke.color = 0xFF000000.toInt()
        stroke.shader = vertical(top, 4f, intArrayOf(white(0.9), white(0.45), white(0.3)), floatArrayOf(0f, 0.5f, 1f))
        stroke.strokeWidth = 1.6f
        canvas.drawPath(jarPath, stroke)
        stroke.shader = null
        stroke.strokeWidth = 1f
        stroke.color = white(0.14)
        canvas.drawPath(innerPath, stroke)
        canvas.restore()
        // The near rim of the base, in front of the car's wheels.
        stroke.strokeWidth = 1.4f
        stroke.color = white(0.42)
        canvas.drawPath(nearArc, stroke)

        canvas.restore()
    }

    private fun drawHeart(canvas: Canvas, liked: Boolean, age: Double) {
        canvas.save()
        canvas.translate(m.HEART_X.toFloat(), m.HEART_Y.toFloat())
        stroke.shader = null
        stroke.strokeWidth = 1.5f
        stroke.color = white(0.42)
        canvas.drawCircle(0f, 0f, 19f, stroke)

        if (age >= 0 && age < 0.8) {
            // Ring and particles of the pop.
            val p = age / 0.8
            val e = 1 - (1 - min(1.0, age / 0.45)).pow(3)
            if (age < 0.45) {
                val rr = (19 + 18 * e).toFloat()
                stroke.strokeWidth = (2.2 * (1 - e) + 0.4).toFloat()
                stroke.color = art.argb(art.HEART_RED, 0.7 * (1 - e))
                canvas.drawCircle(0f, 0f, rr, stroke)
            }
            val ease = 1 - (1 - p).pow(3)
            for (i in 0 until 10) {
                val fi = i.toDouble()
                val angle = -PI / 2 + (fi - 4.5) / 4.5 * 1.25 + (art.hash(fi + 1) - 0.5) * 0.3
                val dist = 18 + (34 + 16 * art.hash(fi + 7)) * ease
                val size = (7 + 5 * art.hash(fi + 3)) * (1 - 0.45 * p)
                canvas.save()
                canvas.translate((cos(angle) * dist).toFloat(), (sin(angle) * dist - 10 * p).toFloat())
                canvas.drawPath(art.heart(size.toFloat(), ring), solid(art.argb(if (i % 2 == 0) art.HEART_RED else art.HEART_PINK, 1 - p)))
                canvas.restore()
            }
        }

        val scale = m.heartScale(age).toFloat()
        canvas.scale(scale, scale)
        art.heart(18f, heartPath)
        if (liked) {
            canvas.drawPath(heartPath, solid(art.argb(art.HEART_RED)))
        } else {
            stroke.strokeWidth = 1.6f
            stroke.strokeJoin = Paint.Join.ROUND
            stroke.color = white(0.9)
            canvas.drawPath(heartPath, stroke)
            stroke.strokeJoin = Paint.Join.MITER
        }
        canvas.restore()
    }
}
