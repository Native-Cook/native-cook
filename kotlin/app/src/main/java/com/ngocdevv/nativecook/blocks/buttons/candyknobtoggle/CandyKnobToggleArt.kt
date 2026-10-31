package com.ngocdevv.nativecook.blocks.buttons.candyknobtoggle

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal const val CANDY_ASSETS = "candyknobtoggle/candyknobtoggle_"

internal fun candyMix(a: Int, b: Int, u: Double): Int {
    fun channel(shift: Int): Int {
        val first = (a ushr shift) and 255
        val last = (b ushr shift) and 255
        return (first + (last - first) * u).roundToInt().coerceIn(0, 255)
    }
    return Color.rgb(channel(16), channel(8), channel(0))
}

/** All blurred masks are rasterized once. Android's hardware canvas ignores mask filters. */
internal class CandyKnobToggleArt(assets: AssetManager, private val density: Float) {
    private class Layer(val bitmap: Bitmap, val bounds: RectF)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val donut = assets.open(CANDY_ASSETS + "knob-donut.png").use { BitmapFactory.decodeStream(it)!! }
    private val gummy = assets.open(CANDY_ASSETS + "knob-gummy.png").use { BitmapFactory.decodeStream(it)!! }
    private val rainbow = intArrayOf(
        Color.rgb(255, 138, 216), Color.rgb(255, 212, 138),
        Color.rgb(138, 242, 255), Color.rgb(180, 138, 255),
    )
    private val track = RectF(0f, 0f, 300f, 128f)
    private val clip = Path().apply { addRoundRect(track, 64f, 64f, Path.Direction.CW) }
    private val destination = RectF(-56f, -56f, 56f, 56f)

    // Skia's MakeBlur(1, sigma, true) uses Solid: original coverage plus its exterior blur.
    private fun mask(sigma: Float) = BlurMaskFilter(
        ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.SOLID,
    )

    private fun layer(bounds: RectF, draw: (Canvas, Paint) -> Unit): Layer {
        val b = Bitmap.createBitmap(
            ceil(bounds.width() * density).toInt(), ceil(bounds.height() * density).toInt(), Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(b)
        c.scale(density, density)
        c.translate(-bounds.left, -bounds.top)
        draw(c, Paint(Paint.ANTI_ALIAS_FLAG))
        return Layer(b, bounds)
    }

    private val trackShadow = layer(RectF(-54f, -38f, 354f, 210f)) { c, p ->
        p.color = Color.argb(46, 40, 20, 60)
        p.maskFilter = mask(18f)
        c.drawRoundRect(6f, 22f, 294f, 150f, 64f, 64f, p)
    }
    private fun smear(width: Float, opacity: Float) = layer(RectF(-width / 2 - 50, -144f, width / 2 + 50, 144f)) { c, p ->
        p.color = Color.WHITE
        p.alpha = (255 * opacity).roundToInt()
        p.maskFilter = mask(14f)
        p.shader = LinearGradient(-width / 2, 0f, width / 2, 0f, rainbow, null, Shader.TileMode.CLAMP)
        c.drawRect(-width / 2, -94f, width / 2, 94f, p)
    }
    private val wideSmear = smear(64f, 0.55f)
    private val narrowSmear = smear(34f, 0.35f)
    private val knobShadow = layer(RectF(-82f, -24f, 82f, 46f)) { c, p ->
        p.color = Color.argb(71, 60, 20, 50)
        p.maskFilter = mask(8f)
        c.drawOval(-52f, 0f, 52f, 22f, p)
    }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, 64f, intArrayOf(Color.argb(140, 255, 255, 255), Color.argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        shader = LinearGradient(0f, 0f, 300f, 128f, intArrayOf(
            Color.argb(242, 255, 255, 255), Color.argb(179, 180, 230, 255),
            Color.argb(204, 255, 180, 230), Color.argb(242, 255, 255, 255),
        ), null, Shader.TileMode.CLAMP)
    }
    // Rasterize the edge's blurred coverage once; its gradient translates on the hardware canvas.
    private val lipMask = layer(RectF(36f, 111f, 264f, 127f)) { c, p ->
        p.strokeWidth = 2.5f
        p.maskFilter = mask(1.5f)
        p.color = Color.WHITE
        c.drawLine(44f, 119f, 256f, 119f, p)
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val lipPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun draw(c: Canvas, layer: Layer) {
        imagePaint.alpha = 255
        c.drawBitmap(layer.bitmap, null, layer.bounds, imagePaint)
    }

    fun draw(c: Canvas, f: CandyKnobToggleFrame, x: Float, y: Float) {
        c.save()
        c.translate(x, y)
        draw(c, trackShadow)
        paint.shader = null
        paint.color = Color.argb(56, 255, 255, 255)
        c.drawRoundRect(track, 64f, 64f, paint)
        c.save()
        c.clipPath(clip)
        val position = f.position.toFloat()
        val drift = (0.5f - position) * 70
        fun rainbow(layer: Layer, cx: Float) {
            c.save()
            c.translate(cx, 64f)
            c.rotate(18f)
            draw(c, layer)
            c.restore()
        }
        rainbow(wideSmear, 108f + drift)
        rainbow(narrowSmear, 186f + drift * 0.6f)

        val kx = 64f + 172f * position
        val stretch = (1 + 0.14 * sin(PI * f.moving) + f.squash).toFloat()
        val sy = (1 / sqrt(stretch) - f.squash * 0.5).toFloat()
        c.save()
        c.translate(kx, 64f + 112f * 0.28f)
        c.scale((112 * stretch - 8) / 104f, 1f)
        draw(c, knobShadow)
        c.restore()

        val donutWeight = candyCubic(candySegment(f.position, 0.33, 0.67))
        fun candy(bitmap: Bitmap, alpha: Double) {
            if (alpha <= 0.001) return
            imagePaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            c.save()
            c.translate(kx, 64f)
            c.scale(stretch, sy)
            c.rotate((f.position * CANDY_TRAVEL / 56 * 180 / PI).toFloat())
            c.drawBitmap(bitmap, null, destination, imagePaint)
            c.restore()
        }
        candy(donut, donutWeight)
        candy(gummy, 1 - donutWeight)
        c.restore()

        c.drawRoundRect(14f, 6f, 286f, 6f + 128f * 0.42f, 38.4f, 38.4f, sheen)
        c.drawRoundRect(track, 64f, 64f, rim)
        lipPaint.shader = LinearGradient(40f - drift, 0f, 260f - drift, 0f, intArrayOf(
            Color.argb(0, 255, 140, 220), Color.rgb(255, 138, 216),
            Color.rgb(138, 242, 255), Color.argb(0, 138, 242, 255),
        ), null, Shader.TileMode.CLAMP)
        c.saveLayer(lipMask.bounds, null)
        c.drawRect(lipMask.bounds, lipPaint)
        c.drawBitmap(lipMask.bitmap, null, lipMask.bounds, maskPaint)
        c.restore()
        c.restore()
    }
}
