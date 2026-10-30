package com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour

import android.content.Context
import android.graphics.*
import kotlin.math.*

/** Small native Canvas bridge. Soft masks are rasterized because hardware ignores MaskFilter. */
internal class AiFeatureTourCanvas(context: Context) {
    private lateinit var canvas: Canvas
    private val faces = listOf("Regular", "Medium", "SemiBold", "Bold").associateWith {
        Typeface.createFromAsset(context.assets, "aifeaturetour/aifeaturetour_Inter-$it.ttf")
    }
    private val colors = mutableMapOf<String, Int>()
    private val fonts = mutableMapOf<String, Paint>()
    private val masks = object : LinkedHashMap<String, Bitmap>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 96
    }
    private fun color(hex: String, alpha: Double = 1.0): Int {
        val rgb = colors.getOrPut(hex) { Color.parseColor(hex) } and 0xFFFFFF
        return ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or rgb
    }
    private fun paint(hex: String, alpha: Double = 1.0, width: Double = 0.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(hex, alpha)
        if (width > 0) {
            style = Paint.Style.STROKE
            strokeWidth = width.toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
    private fun bounds(x: Number, y: Number, w: Number, h: Number) = RectF(x.toFloat(), y.toFloat(), (x.toDouble() + w.toDouble()).toFloat(), (y.toDouble() + h.toDouble()).toFloat())
    fun draw(nativeCanvas: Canvas, block: () -> Unit) {
        canvas = nativeCanvas
        saved(block)
    }
    fun saved(block: () -> Unit) {
        val count = canvas.save()
        try { block() } finally { canvas.restoreToCount(count) }
    }
    fun translate(x: Number, y: Number) = canvas.translate(x.toFloat(), y.toFloat())
    fun scale(value: Double) = canvas.scale(value.toFloat(), value.toFloat())
    fun rotate(degrees: Number) = canvas.rotate(degrees.toFloat())
    fun layer(alpha: Double, block: () -> Unit) {
        val count = canvas.saveLayer(null, Paint().apply { this.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt() })
        try { block() } finally { canvas.restoreToCount(count) }
    }
    fun path(path: Path, hex: String, alpha: Double = 1.0, width: Double = 0.0) {
        canvas.drawPath(path, paint(hex, alpha, width))
    }
    fun round(x: Number, y: Number, w: Number, h: Number, radius: Number, hex: String,
              alpha: Double = 1.0, width: Double = 0.0, blur: Double = 0.0) {
        val rect = bounds(x, y, w, h)
        if (blur <= 0) {
            canvas.drawRoundRect(rect, radius.toFloat(), radius.toFloat(), paint(hex, alpha, width))
            return
        }
        val key = "$w:$h:$radius:$blur:$width"
        val pad = ceil(3 * blur).toInt()
        val mask = masks.getOrPut(key) {
            Bitmap.createBitmap(ceil(w.toDouble()).toInt() + 2 * pad, ceil(h.toDouble()).toInt() + 2 * pad, Bitmap.Config.ARGB_8888).also { bitmap ->
                val software = Canvas(bitmap)
                val p = paint("#FFFFFF", 1.0, width)
                // Android's radius parameter maps to Skia's sigma with this affine conversion.
                p.maskFilter = BlurMaskFilter(max(0.001, (blur - 0.5) / 0.57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
                software.drawRoundRect(bounds(pad, pad, w, h), radius.toFloat(), radius.toFloat(), p)
            }
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
            colorFilter = PorterDuffColorFilter(color(hex), PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(mask, x.toFloat() - pad, y.toFloat() - pad, p)
    }
    fun rect(x: Number, y: Number, w: Number, h: Number, hex: String) = canvas.drawRect(bounds(x, y, w, h), paint(hex))
    fun circle(x: Number, y: Number, radius: Number, hex: String, alpha: Double = 1.0, width: Double = 0.0) {
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), paint(hex, alpha, width))
    }
    fun oval(x: Number, y: Number, w: Number, h: Number, hex: String) = canvas.drawOval(bounds(x, y, w, h), paint(hex))
    fun line(x1: Number, y1: Number, x2: Number, y2: Number, hex: String, width: Number, alpha: Double = 1.0) {
        canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), paint(hex, alpha, width.toDouble()))
    }
    fun clipRound(x: Number, y: Number, w: Number, h: Number, r: Number) {
        canvas.clipPath(Path().apply { addRoundRect(bounds(x, y, w, h), r.toFloat(), r.toFloat(), Path.Direction.CW) })
    }
    fun linear(x: Number, y: Number, w: Number, h: Number, x1: Number, y1: Number, x2: Number, y2: Number,
               colors: List<String>, alpha: DoubleArray, stops: DoubleArray) {
        val p = paint("#FFFFFF").apply {
            shader = LinearGradient(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(),
                colors.indices.map { color(colors[it], alpha[it]) }.toIntArray(), stops.map { it.toFloat() }.toFloatArray(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(bounds(x, y, w, h), p)
    }
    fun radial(x: Number, y: Number, w: Number, h: Number, cx: Number, cy: Number, radius: Number, colors: List<String>, alpha: DoubleArray) {
        val p = paint("#FFFFFF").apply {
            shader = RadialGradient(cx.toFloat(), cy.toFloat(), radius.toFloat(), colors.indices.map { color(colors[it], alpha[it]) }.toIntArray(), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(bounds(x, y, w, h), p)
    }
    fun radialOval(x: Number, y: Number, w: Number, h: Number, cx: Number, cy: Number, radius: Number, first: String, last: String) {
        val p = paint("#FFFFFF").apply { shader = RadialGradient(cx.toFloat(), cy.toFloat(), radius.toFloat(), intArrayOf(color(first), color(last)), null, Shader.TileMode.CLAMP) }
        canvas.drawOval(bounds(x, y, w, h), p)
    }
    private fun font(weight: String, size: Double) = fonts.getOrPut("$weight:$size") {
        paint("#FFFFFF").apply { typeface = faces.getValue(weight); textSize = size.toFloat(); isSubpixelText = true }
    }
    fun textWidth(text: String, weight: String, size: Double) = font(weight, size).measureText(text).toDouble()
    fun text(text: String, x: Double, cy: Double, weight: String, size: Double, hex: String, alpha: Double = 1.0, ax: Double = 0.0) {
        val p = font(weight, size)
        p.color = color(hex, alpha)
        p.shader = null
        canvas.drawText(text, (x - p.measureText(text) * ax).toFloat(), (cy + size * 0.36).toFloat(), p)
    }
    fun textGradient(text: String, x: Double, cy: Double, weight: String, size: Double, x0: Double, x1: Double, alpha: Double = 1.0, ax: Double = 0.0) {
        val p = font(weight, size)
        p.shader = LinearGradient(
            x0.toFloat(), 0f, x1.toFloat(), 0f,
            intArrayOf(color("#7C68FF", 0.0), color("#7C68FF", 0.95 * alpha), color("#7C68FF", 0.0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawText(text, (x - p.measureText(text) * ax).toFloat(), (cy + size * 0.36).toFloat(), p)
        p.shader = null
    }
}
