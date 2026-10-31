package com.ngocdevv.nativecook.blocks.playground.jellygooglymascot

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class JellyGooglyMascotFlavour(val name: String, val light: Int, val deep: Int, val room: Int, val ink: Int)

internal val jellyGooglyMascotFlavours = listOf(
    JellyGooglyMascotFlavour("Lemon", 0xFFFFE45C.toInt(), 0xFFF2A100.toInt(), 0xFFFFF3D6.toInt(), 0xFF4A3500.toInt()),
    JellyGooglyMascotFlavour("Blueberry", 0xFF8FB0FF.toInt(), 0xFF3553E6.toInt(), 0xFFC3CDFF.toInt(), 0xFF14205E.toInt()),
    JellyGooglyMascotFlavour("Grape", 0xFFF2B8FF.toInt(), 0xFFA855E8.toInt(), 0xFFA98BF5.toInt(), 0xFF2B0F4F.toInt()),
)

internal fun jellyGooglyMascotMix(a: Int, b: Int, f: Double): Int = Color.rgb(
    (Color.red(a) * (1 - f) + Color.red(b) * f).roundToInt(),
    (Color.green(a) * (1 - f) + Color.green(b) * f).roundToInt(),
    (Color.blue(a) * (1 - f) + Color.blue(b) * f).roundToInt(),
)

/** Geometry stays in dp, like the reference's Skia picture in points. */
internal class JellyGooglyMascotPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val eye = Path()
    private val points = FloatArray(96)
    // Hardware Canvas ignores mask filters. Static soft ovals are cached. Moving soft layers
    // use GPU RenderNodes on API 31+, with reusable software masks on older Android versions.
    private val shadow = FloorShadow()
    private val bounce = SoftOval(128f, 128f * 0.3f, 18f, Color.argb(89, 255, 255, 255))
    private val specular = SoftOval(128f * 0.42f, 128f * 0.22f, 9f, Color.argb(204, 255, 255, 255))
    private val rimExtent = 210f
    private val rimNode = if (Build.VERSION.SDK_INT >= 31) SoftNode(420f, 420f, 14f) else null
    private val rimBitmap by lazy { Bitmap.createBitmap(630, 630, Bitmap.Config.ARGB_8888) }
    private val rimCanvas by lazy { Canvas(rimBitmap).apply { scale(1.5f, 1.5f); translate(rimExtent, rimExtent) } }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 26f
    }

    private fun fill(color: Int) {
        paint.shader = null
        paint.maskFilter = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = Color.alpha(color)
    }

    fun draw(c: Canvas, t: Double, cx: Float, cy: Float, target: JellyGooglyMascotTarget, poke: JellyGooglyMascotPoke, ghost: Boolean) {
        val r = 128f
        val base = floor(poke.flavour).toInt()
        val fraction = poke.flavour - base
        val a = jellyGooglyMascotFlavours[base % 3]
        val b = jellyGooglyMascotFlavours[(base + 1) % 3]
        val light = jellyGooglyMascotMix(a.light, b.light, fraction)
        val deep = jellyGooglyMascotMix(a.deep, b.deep, fraction)
        val distance = hypot(target.x, target.y)
        val lean = min(18.0, distance * 0.09)
        val ux = if (distance > 0.001) target.x / distance else 0.0
        val uy = if (distance > 0.001) target.y / distance else 0.0
        val ox = cx + (ux * lean).toFloat()
        val oy = cy + (uy * lean).toFloat()
        val stretch = min(0.08, distance / 2400)
        val squash = poke.squash
        val w = 2 * PI / LOOP
        fill(Color.WHITE)
        shadow.draw(c, cx, cy + r * 0.92f, (r * (0.85 + squash)).toFloat(), paint)
        for (i in 0 until 48) {
            val theta = i / 48.0 * 2 * PI
            val radius = r * (1 + 0.045 * sin(3 * theta + 2 * w * t) + 0.03 * sin(5 * theta - 3 * w * t) + 0.02 * sin(2 * theta + w * t))
            var x = cos(theta) * radius
            var y = sin(theta) * radius
            val along = x * ux + y * uy
            x += ux * along * stretch
            y += uy * along * stretch
            points[i * 2] = (x * (1 + squash)).toFloat()
            points[i * 2 + 1] = (y * (1 - squash) + r * squash).toFloat()
        }
        path.rewind()
        path.moveTo((points[0] + points[2]) / 2, (points[1] + points[3]) / 2)
        for (i in 1..48) {
            val p = i % 48
            val next = (i + 1) % 48
            path.quadTo(points[p * 2], points[p * 2 + 1], (points[p * 2] + points[next * 2]) / 2, (points[p * 2 + 1] + points[next * 2 + 1]) / 2)
        }
        path.close()
        c.save()
        c.translate(ox, oy)
        fill(Color.WHITE)
        paint.shader = RadialGradient(-r * 0.35f, -r * 0.4f, r * 1.7f, intArrayOf(light, deep), floatArrayOf(0.1f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, paint)
        fill(Color.WHITE)
        rimPaint.color = deep
        rimPaint.alpha = 89
        c.save()
        c.clipPath(path)
        if (Build.VERSION.SDK_INT >= 31 && rimNode != null && c.isHardwareAccelerated) {
            rimNode.record { layer ->
                layer.translate(rimExtent, rimExtent)
                layer.drawPath(path, rimPaint)
            }
            rimNode.draw(c, -rimExtent, -rimExtent)
        } else {
            rimBitmap.eraseColor(Color.TRANSPARENT)
            rimPaint.maskFilter = blur(14f)
            rimCanvas.drawPath(path, rimPaint)
            rimPaint.maskFilter = null
            c.drawBitmap(rimBitmap, null, RectF(-rimExtent, -rimExtent, rimExtent, rimExtent), paint)
        }
        bounce.draw(c, -r * 0.5f, r * 0.62f, paint)
        c.restore()
        c.save()
        c.rotate(-28f, -r * 0.42f, -r * 0.5f)
        specular.draw(c, -r * 0.62f, -r * 0.64f, paint)
        c.restore()
        c.restore()
        val ex = ox + (ux * lean * 0.6).toFloat()
        val ey = oy - r * 0.12f + (uy * lean * 0.6 + r * squash * 0.8).toFloat()
        for (side in intArrayOf(-1, 1)) {
            val eyeX = ex + side * r * 0.4f
            val rx = (r * 0.27 * (1 + squash)).toFloat()
            val ry = (r * 0.31 * (1 - squash) * (1 - 0.92 * poke.blink)).toFloat()
            eye.rewind()
            eye.addOval(eyeX - rx, ey - ry, eyeX + rx, ey + ry, Path.Direction.CW)
            fill(Color.WHITE)
            c.drawPath(eye, paint)
            val dx = target.px - (eyeX - cx)
            val dy = target.py - (ey - cy)
            val d = hypot(dx, dy).coerceAtLeast(1.0)
            val reach = min(1.0, d / 120)
            val qx = eyeX + (dx / d * rx * 0.42 * reach).toFloat()
            val qy = ey + (dy / d * ry * 0.42 * reach).toFloat()
            val pr = r * 0.155f
            c.save()
            c.clipPath(eye)
            fill(0xFF0B0B0F.toInt())
            c.drawOval(qx - pr, qy - pr * 1.1f, qx + pr, qy + pr * 1.1f, paint)
            fill(Color.WHITE)
            c.drawCircle(qx - pr * 0.35f, qy - pr * 0.45f, pr * 0.22f, paint)
            c.restore()
        }
        fill(0xFF0B0B0F.toInt())
        val mouthTop = ey + r * 0.3f - (4 * (1 - squash)).toFloat()
        c.drawOval(ex - 6f, mouthTop, ex + 6f, mouthTop + (9 * (1 - squash) + 2).toFloat(), paint)
        if (ghost) {
            val x = cx + target.x.toFloat()
            val y = cy + target.y.toFloat()
            fill(Color.argb(140, 255, 255, 255))
            c.drawCircle(x, y, 16f, paint)
            fill(Color.argb(230, 255, 255, 255))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            c.drawCircle(x, y, 16f, paint)
        }
    }

    private class FloorShadow {
        private val node = if (Build.VERSION.SDK_INT >= 31) SoftNode(360f, 130f, 16f) else null
        private val bitmap by lazy { Bitmap.createBitmap(720, 260, Bitmap.Config.ARGB_8888) }
        private val canvas by lazy { Canvas(bitmap).apply { scale(2f, 2f); translate(180f, 48f) } }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(56, 40, 20, 60)
        }
        private val bounds = RectF()
        fun draw(target: Canvas, cx: Float, y: Float, radiusX: Float, bitmapPaint: Paint) {
            if (Build.VERSION.SDK_INT >= 31 && node != null && target.isHardwareAccelerated) {
                node.record { layer ->
                    layer.translate(180f, 48f)
                    layer.drawOval(-radiusX, 0f, radiusX, 34f, paint)
                }
                node.draw(target, cx - 180f, y - 48f)
                return
            }
            bitmap.eraseColor(Color.TRANSPARENT)
            paint.maskFilter = blur(16f)
            canvas.drawOval(-radiusX, 0f, radiusX, 34f, paint)
            bounds.set(cx - 180f, y - 48f, cx + 180f, y + 82f)
            target.drawBitmap(bitmap, null, bounds, bitmapPaint)
        }
    }

    /** Soft masks render at 1 px/dp; the sharp silhouette and eyes keep full display density. */
    @RequiresApi(31)
    private class SoftNode(width: Float, height: Float, sigma: Float) {
        private val node = RenderNode("JellyGooglyMascot soft layer").apply {
            setPosition(0, 0, ceil(width).toInt(), ceil(height).toInt())
            val radius = radiusForSigma(sigma)
            setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL))
        }
        fun record(draw: (Canvas) -> Unit) {
            val canvas = node.beginRecording()
            draw(canvas)
            node.endRecording()
        }
        fun draw(canvas: Canvas, x: Float, y: Float) {
            canvas.save()
            canvas.translate(x, y)
            canvas.drawRenderNode(node)
            canvas.restore()
        }
    }

    private class SoftOval(width: Float, height: Float, sigma: Float, color: Int) {
        private val pad = ceil(3 * sigma)
        private val scale = 2f
        private val bitmap = Bitmap.createBitmap(ceil((width + 2 * pad) * scale).toInt(), ceil((height + 2 * pad) * scale).toInt(), Bitmap.Config.ARGB_8888)
        private val bounds = RectF(0f, 0f, bitmap.width / scale, bitmap.height / scale)
        init {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; maskFilter = blur(sigma) }
            Canvas(bitmap).apply { scale(scale, scale); drawOval(pad, pad, pad + width, pad + height, p) }
        }
        fun draw(canvas: Canvas, x: Float, y: Float, paint: Paint) {
            paint.shader = null
            paint.maskFilter = null
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = 255
            canvas.save()
            canvas.translate(x - pad, y - pad)
            canvas.drawBitmap(bitmap, null, bounds, paint)
            canvas.restore()
        }
    }

    companion object {
        private fun radiusForSigma(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
        private fun blur(sigma: Float) = BlurMaskFilter(radiusForSigma(sigma), BlurMaskFilter.Blur.NORMAL)
    }
}
