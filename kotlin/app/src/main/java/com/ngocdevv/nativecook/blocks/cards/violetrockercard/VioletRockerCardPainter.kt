package com.ngocdevv.nativecook.blocks.cards.violetrockercard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun violetRockerCardColor(day: Int, night: Int, n: Double, alpha: Double = 1.0): Int {
    val t = VioletRockerCardMath.clamp(n)
    fun channel(shift: Int): Int { val a = day shr shift and 255; val b = night shr shift and 255; return (a + (b - a) * t).roundToInt() }
    return Color.argb((alpha.coerceIn(0.0, 1.0) * 255).roundToInt(), channel(16), channel(8), channel(0))
}

internal class VioletRockerCardPainter(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path().apply { addRoundRect(RectF(0f, 0f, 100f, 260f), 50f, 50f, Path.Direction.CW) }
    private val matrix = Matrix()
    private val coefficients = FloatArray(9)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val day = face(context, "rocker-day", 22.0, 40.0, 187.0, 526.0)
    private val night = face(context, "rocker-night", 21.0, 40.0, 189.0, 525.0)
    private val broadShadow = VioletRockerCardBlurLayer(0)
    private val contactShadow = VioletRockerCardBlurLayer(0)
    private val emission = VioletRockerCardBlurLayer(2)
    private val outerGlow = VioletRockerCardBlurLayer(1)
    private val innerGlow = VioletRockerCardBlurLayer(1)
    private val font = Typeface.create("sans-serif", Typeface.NORMAL)

    private fun basic(color: Int = Color.WHITE, alpha: Double = 1.0, stroke: Double? = null) {
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true
        paint.color = color; paint.alpha = (alpha.coerceIn(0.0, 1.0) * Color.alpha(color)).roundToInt()
        paint.style = if (stroke == null) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = (stroke ?: 0.0).toFloat(); paint.textAlign = Paint.Align.CENTER
        paint.shader = null; paint.xfermode = null
        if (Build.VERSION.SDK_INT >= 29) paint.blendMode = BlendMode.SRC_OVER
    }
    private fun plus() { if (Build.VERSION.SDK_INT >= 29) paint.blendMode = BlendMode.PLUS else paint.xfermode = add }
    private fun face(context: Context, name: String, left: Double, top: Double, width: Double, height: Double): Bitmap {
        val source = context.assets.open("violetrockercard/violetrockercard_$name.png").use { BitmapFactory.decodeStream(it)!! }
        val result = Bitmap.createBitmap(400, 1040, Bitmap.Config.ARGB_8888)
        result.density = Bitmap.DENSITY_NONE
        val canvas = Canvas(result); canvas.scale(4f, 4f)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, 100f, 260f), 50f, 50f, Path.Direction.CW) }
        canvas.clipPath(clip)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val x = -left * 100 / width; val y = -top * 260 / height
        canvas.drawBitmap(source, null, RectF(x.toFloat(), y.toFloat(), (x + 231 * 100 / width).toFloat(), (y + 600 * 260 / height).toFloat()), p)
        result.prepareToDraw(); return result
    }
    private fun projected(c: Canvas, f: VioletRockerCardFrame, depth: Double, rock: Double = f.rock, draw: () -> Unit) {
        val p = VioletRockerCardProjection(f, rock, depth)
        coefficients[0] = p.a.toFloat(); coefficients[1] = p.b.toFloat(); coefficients[2] = p.tx.toFloat()
        coefficients[3] = p.d.toFloat(); coefficients[4] = p.e.toFloat(); coefficients[5] = p.ty.toFloat()
        coefficients[6] = p.g.toFloat(); coefficients[7] = p.h.toFloat(); coefficients[8] = p.w.toFloat()
        matrix.setValues(coefficients)
        c.save(); c.translate(p.originX.toFloat(), p.originY.toFloat()); c.concat(matrix); draw(); c.restore()
    }
    fun draw(c: Canvas, f: VioletRockerCardFrame) {
        val n = VioletRockerCardMath.clamp(f.n)
        groundLight(c, f, n)
        c.save(); c.translate(0f, 10f)
        projected(c, f, 0.0, 0.0) { broadShadow.draw(c, 100.0, 260.0, 22.0, Color.BLACK, 0.12 - 0.04 * n) }; c.restore()
        c.save(); c.translate(0f, 1.5f)
        projected(c, f, 0.0, 0.0) { contactShadow.draw(c, 100.0, 260.0, 3.0, Color.BLACK, 0.25) }; c.restore()
        projected(c, f, 0.0, 0.0) {
            basic(violetRockerCardColor(0x8C8C8C, 0x080809, n), stroke = 0.5); c.drawPath(path, paint)
        }
        for (index in 0..19) {
            val fraction = index / 19.0
            projected(c, f, fraction * 18, f.rock * fraction) {
                basic(alpha = 0.52 + 0.48 * fraction)
                paint.shader = LinearGradient(0f, 0f, 0f, 260f, intArrayOf(
                    violetRockerCardColor(0x45138F, 0xF7CAFF, n), violetRockerCardColor(0x8934D4, 0xDB8EFF, n),
                    violetRockerCardColor(0xC15CF1, 0x45138F, n)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawPath(path, paint)
            }
        }
        projected(c, f, 18.0) { emission.draw(c, 100.0, 260.0, 4.0, Color.rgb(179, 46, 255), 0.75 * n) }
        projected(c, f, 18.0) {
            // Isolate the premultiplied face interpolation from the already-rendered sidewalls.
            val group = c.saveLayer(RectF(-1f, -1f, 101f, 261f), null)
            basic(alpha = 1 - n); c.drawBitmap(day, null, RectF(0f, 0f, 100f, 260f), paint)
            basic(alpha = n); plus(); c.drawBitmap(night, null, RectF(0f, 0f, 100f, 260f), paint)
            c.restoreToCount(group)
        }
        projected(c, f, 18.0) {
            basic(stroke = 0.6)
            paint.shader = LinearGradient(0f, 0f, 0f, 260f, intArrayOf(
                violetRockerCardColor(0xDDD9DD, 0xF7CAFF, n, 0.28 + 0.62 * n),
                violetRockerCardColor(0xBD69E6, 0x8934D4, n, 0.18)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(path, paint)
        }
        projected(c, f, 0.0, 0.0) { markers(c, f, n) }
    }
    private fun label(c: Canvas, value: String, x: Float, centerY: Float, color: Int) {
        basic(color); paint.textAlign = Paint.Align.CENTER; paint.typeface = font; paint.textSize = 18f
        val metrics = paint.fontMetrics
        c.drawText(value, x, centerY - (metrics.ascent + metrics.descent) / 2, paint)
    }
    private fun markers(c: Canvas, f: VioletRockerCardFrame, n: Double) {
        val color = violetRockerCardColor(0x727272, 0xF1F1F1, n)
        label(c, "ER", 119f, 11f, color); label(c, "SÜ", 119f, 260f, color)
        for (index in 0..18) {
            val y = (33 + index * 197.0 / 18).toFloat()
            basic(violetRockerCardColor(0xA9ADB3, 0xF1F1F1, VioletRockerCardMath.dot(index, f)))
            c.drawCircle(119f, y, 1.2f, paint)
            basic(Color.BLACK, 0.10 * (1 - n), 0.35); c.drawCircle(119f, y, 1.2f, paint)
        }
    }
    private fun groundLight(c: Canvas, f: VioletRockerCardFrame, n: Double) {
        val p = VioletRockerCardProjection(f, f.rock, 8.0).point(0.0, 130 * (1 - 2 * n))
        val radius = (84 + 11 * f.c) * min(1.7, f.scale / 1.223)
        c.save(); c.translate((p.first - radius * 1.2).toFloat(), (p.second - radius * 0.9).toFloat())
        outerGlow.draw(c, radius * 2.4, radius * 1.8, 12 + 8 * n, violetRockerCardColor(0x8C2DE5, 0x8C2DE5, n), 0.12 + 0.83 * n, radius)
        c.restore()
        c.save(); c.translate((p.first - 45).toFloat(), (p.second - 35).toFloat())
        innerGlow.draw(c, 90.0, 70.0, 8.0, violetRockerCardColor(0xC15CF1, 0xF7CAFF, n), 0.08 + 0.65 * n, 35.0)
        c.restore()
    }
}

/** Blur is applied before the parent homography, matching Swift's modifier order. */
private class VioletRockerCardBlurLayer(private val kind: Int) {
    private data class Key(val width: Float, val height: Float, val blur: Float, val color: Int, val radius: Float)
    private val node: Any? = if (Build.VERSION.SDK_INT >= 31) RenderNode("VioletRockerCardBlur$kind").apply { setClipToBounds(false) } else null
    private var lastKey: Key? = null
    private val fallback = object : LinkedHashMap<Key, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>?) = size > 8
    }
    private val composite = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun primitive(c: Canvas, key: Key, padding: Float, software: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = key.color
        p.style = if (kind == 2) Paint.Style.STROKE else Paint.Style.FILL; p.strokeWidth = 2f
        if (software) p.maskFilter = BlurMaskFilter(((key.blur - 0.5f) * sqrt(3f)).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)
        val bounds = RectF(padding, padding, padding + key.width, padding + key.height)
        if (kind == 1) {
            p.shader = RadialGradient(bounds.centerX(), bounds.centerY(), key.radius, key.color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawOval(bounds, p)
        } else c.drawRoundRect(bounds, key.width / 2, key.width / 2, p)
    }
    fun draw(c: Canvas, width: Double, height: Double, blur: Double, color: Int, alpha: Double, radius: Double = 0.0) {
        if (alpha <= 0) return
        val key = Key(width.toFloat(), height.toFloat(), blur.toFloat(), color, radius.toFloat())
        if (Build.VERSION.SDK_INT >= 31 && c.isHardwareAccelerated && node != null) {
            val render = node as RenderNode
            if (lastKey != key) {
                val pad = ceil(blur * 3 + 4).toInt(); val w = ceil(width).toInt() + pad * 2; val h = ceil(height).toInt() + pad * 2
                render.setPosition(-pad, -pad, w - pad, h - pad)
                val recording = render.beginRecording(w, h)
                primitive(recording, key, pad.toFloat(), false)
                render.endRecording(); render.setRenderEffect(RenderEffect.createBlurEffect(key.blur, key.blur, Shader.TileMode.DECAL))
                lastKey = key
            }
            render.setAlpha(alpha.toFloat().coerceIn(0f, 1f)); c.drawRenderNode(render)
        } else {
            // Older devices use the same procedural shape/light, sampled into a2×blur atlas.
            fun quantized(x: Double) = ((x * 2).roundToInt() / 2.0).toFloat()
            val q = Key(quantized(width), quantized(height), quantized(blur), color, quantized(radius))
            val pad = ceil(q.blur * 3 + 4).toInt(); val w = ceil(q.width).toInt() + pad * 2; val h = ceil(q.height).toInt() + pad * 2
            val bitmap = fallback.getOrPut(q) {
                Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888).also { image ->
                    image.density = Bitmap.DENSITY_NONE
                    val canvas = Canvas(image); canvas.scale(2f, 2f); primitive(canvas, q, pad.toFloat(), true); image.prepareToDraw()
                }
            }
            composite.reset(); composite.isAntiAlias = true; composite.isFilterBitmap = true; composite.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
            c.drawBitmap(bitmap, null, RectF(-pad.toFloat(), -pad.toFloat(), (w - pad).toFloat(), (h - pad).toFloat()), composite)
        }
    }
}
