package com.ngocdevv.nativecook.blocks.pickers.dicerollpicker

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Small drawing helpers shared by the dice table's baked art and its per-frame layer: the Skia
 * helpers of the React Native version (fill, stroke, gradients, tracked text, grain, bake) on
 * android.graphics. Paints come from a pool that is rewound once per frame, so a frame allocates
 * no Paint objects; every paint is reset (opaque, no shader) before it is handed out.
 */
internal class DiceKit {
    private val pool = ArrayList<Paint>()
    /** Scratch path, second path and rect for per-frame shapes (reset before use). */
    val path = Path()
    val path2 = Path()
    val rect = RectF()
    private var next = 0

    /** Rewinds the pool: paints handed out before are reused from here on. */
    fun begin() {
        next = 0
    }

    private fun paint(): Paint {
        if (next == pool.size) pool.add(Paint())
        val p = pool[next++]
        p.reset()
        p.isAntiAlias = true
        p.isDither = true
        return p
    }

    fun fill(hex: Int, a: Float = 1f, blur: Float = 0f): Paint {
        val p = paint()
        p.color = diceArgb(hex, a)
        if (blur > 0f) p.maskFilter = diceBlur(blur)
        return p
    }

    fun stroke(hex: Int, width: Float, a: Float = 1f, blur: Float = 0f): Paint {
        val p = fill(hex, a, blur)
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        return p
    }

    fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, stops: FloatArray?): Paint {
        val p = paint()
        p.shader = LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP)
        return p
    }

    fun radial(x: Float, y: Float, r: Float, colors: IntArray, stops: FloatArray?): Paint {
        val p = paint()
        p.shader = RadialGradient(x, y, max(r, 0.01f), colors, stops, Shader.TileMode.CLAMP)
        return p
    }

    fun sweep(x: Float, y: Float, colors: IntArray, stops: FloatArray?): Paint {
        val p = paint()
        p.shader = SweepGradient(x, y, colors, stops)
        return p
    }

    /** Draws a string with its baseline at y in the font's typeface and size. */
    fun text(canvas: Canvas, str: String, x: Float, y: Float, font: Paint, hex: Int, a: Float = 1f) {
        font.color = diceArgb(hex, a)
        canvas.drawText(str, x, y, font)
    }

    /** Text with extra tracking; align 0 = left, 0.5 = centre, 1 = right. */
    fun spaced(canvas: Canvas, str: String, x: Float, y: Float, font: Paint, hex: Int, a: Float, track: Float, align: Float = 0f): Float {
        var total = 0f
        for (i in str.indices) total += font.measureText(str, i, i + 1) + if (i < str.length - 1) track else 0f
        var cx = x - total * align
        font.color = diceArgb(hex, a)
        for (i in str.indices) {
            canvas.drawText(str, i, i + 1, cx, y, font)
            cx += font.measureText(str, i, i + 1) + track
        }
        return total
    }

    /** Faint static grain over a rect, soft-light (overlay below Android 10). */
    fun grain(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, alpha: Float, seed: Int) {
        val p = paint()
        p.shader = BitmapShader(diceNoise(seed), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            p.blendMode = BlendMode.SOFT_LIGHT
            p.alpha = (alpha * 255).roundToInt()
        } else {
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            p.alpha = (alpha * 0.6f * 255).roundToInt()
        }
        canvas.drawRect(x, y, x + w, y + h, p)
    }

    /** Seeded grey noise over a rect, normal blend (Skia's fractal noise drawn at an alpha). */
    fun noise(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, alpha: Float, seed: Int) {
        val p = paint()
        p.shader = BitmapShader(diceNoise(seed), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        p.alpha = (alpha * 255).roundToInt()
        canvas.drawRect(x, y, x + w, y + h, p)
    }

    /** Brushed-metal streaks along x over a rect (normal blend), like Skia's stretched fractal noise. */
    fun brushed(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, alpha: Float, seed: Int) {
        val p = paint()
        p.shader = BitmapShader(diceStreaks(seed), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        p.alpha = (alpha * 255).roundToInt()
        canvas.drawRect(x, y, x + w, y + h, p)
    }
}

internal fun diceArgb(hex: Int, a: Float = 1f): Int =
    ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (hex and 0xFFFFFF)

internal fun diceColors(vararg c: Pair<Int, Float>): IntArray = IntArray(c.size) { diceArgb(c[it].first, c[it].second) }

private val blurCache = HashMap<Int, BlurMaskFilter>()

/** A Gaussian mask blur of Skia's `sigma` (Android takes a radius). */
internal fun diceBlur(sigma: Float): BlurMaskFilter = synchronized(blurCache) {
    val key = (sigma * 100).roundToInt()
    blurCache.getOrPut(key) { BlurMaskFilter(max(0.01f, (sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL) }
}

private val noiseCache = HashMap<Int, Bitmap>()

/** A 128 px tile of seeded grey noise around mid-grey, one pixel per point. */
private fun diceNoise(seed: Int): Bitmap = synchronized(noiseCache) {
    noiseCache.getOrPut(seed) {
        val n = 128
        val px = IntArray(n * n)
        var s = seed * 7919 + 17
        for (i in px.indices) {
            s = s * 1103515245 + 12345
            val r = ((s ushr 16) and 0x7fff) / 32767f
            s = s * 1103515245 + 12345
            val r2 = ((s ushr 16) and 0x7fff) / 32767f
            val v = (((r + r2) * 0.5f) * 255f).roundToInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }
}

private val streakCache = HashMap<Int, Bitmap>()

/** A tile of grey streaks: a fresh value every pixel row, drifting slowly along x. */
private fun diceStreaks(seed: Int): Bitmap = synchronized(streakCache) {
    streakCache.getOrPut(seed) {
        val w = 256
        val h = 96
        val px = IntArray(w * h)
        var s = seed * 4931 + 7
        fun rnd(): Float {
            s = s * 1103515245 + 12345
            return ((s ushr 16) and 0x7fff) / 32767f
        }
        for (row in 0 until h) {
            // Four control values along the row (the last equals the first so the tile repeats).
            val k = FloatArray(5) { rnd() }
            k[4] = k[0]
            for (col in 0 until w) {
                val u = col / w.toFloat() * 4
                val i = u.toInt().coerceAtMost(3)
                val f = u - i
                val sm = f * f * (3 - 2 * f)
                val v = ((k[i] + (k[i + 1] - k[i]) * sm) * 255f).roundToInt().coerceIn(0, 255)
                px[row * w + col] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}

/** A typeface and size, as a Paint used only for text. */
internal fun diceFont(serif: Boolean, size: Float, weight: Int): Paint {
    val family = if (serif) Typeface.SERIF else Typeface.SANS_SERIF
    val face = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(family, weight, false)
    } else {
        Typeface.create(family, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    return Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        isSubpixelText = true
    }
}

/** Draws into a bitmap once (off the main thread), at `scale` pixels per point. */
internal fun diceBake(width: Float, height: Float, scale: Float, draw: (Canvas) -> Unit): Bitmap {
    val bmp = Bitmap.createBitmap(max(1, ceil(width * scale).toInt()), max(1, ceil(height * scale).toInt()), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.scale(scale, scale)
    draw(canvas)
    return bmp
}
