package com.ngocdevv.nativecook.blocks.navigation.compassneedlenav

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/*
 CompassNeedleNav — drawing helpers in android.graphics, so the art reads like the Skia code it was ported
 from: a small pool of paints, gradients, blurred paints (Skia sigma), a grain tile and bitmaps
 baked once. Everything is drawn in dp (the canvas is scaled by the density).
*/

internal fun compassRgb(hex: Int) = (0xFF shl 24) or hex

internal fun compassRgba(r: Int, g: Int, b: Int, a: Double) =
    ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

/** A colour from 0…1 floats. */
internal fun compassRgbf(r: Double, g: Double, b: Double, a: Double = 1.0) = compassRgba(
    (r.coerceIn(0.0, 1.0) * 255).roundToInt(),
    (g.coerceIn(0.0, 1.0) * 255).roundToInt(),
    (b.coerceIn(0.0, 1.0) * 255).roundToInt(),
    a,
)

internal class CompassNeedleNavGfx {
    private val pool = Array(12) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private var next = 0
    private val blurs = HashMap<Float, BlurMaskFilter>()
    /** Hardware canvases ignore mask filters below API 28: blurred shapes are then left out. */
    var liveBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun paint(color: Int, alpha: Double = 1.0): Paint {
        val p = pool[next]
        next = (next + 1) % pool.size
        p.reset()
        p.isAntiAlias = true
        p.color = color
        if (alpha < 1) p.alpha = (p.alpha * alpha.coerceIn(0.0, 1.0)).roundToInt()
        return p
    }

    fun shaded(shader: Shader, alpha: Double = 1.0): Paint {
        val p = paint(0xFF000000.toInt())
        p.shader = shader
        if (alpha < 1) p.alpha = (255 * alpha.coerceIn(0.0, 1.0)).roundToInt()
        return p
    }

    fun blur(sigma: Float): BlurMaskFilter {
        // Quantised to a quarter dp so a sigma that moves with the pose keeps a small cache.
        val q = max(0.25f, (sigma * 4).roundToInt() / 4f)
        return blurs.getOrPut(q) { BlurMaskFilter(sigmaToRadius(q), BlurMaskFilter.Blur.NORMAL) }
    }

    fun blurred(color: Int, alpha: Double, sigma: Float): Paint {
        val p = paint(color, alpha)
        if (!liveBlur) p.alpha = 0
        p.maskFilter = blur(sigma)
        return p
    }

    fun stroke(p: Paint, width: Float, round: Boolean = false): Paint {
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        if (round) p.strokeCap = Paint.Cap.ROUND
        return p
    }

    fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray? = null) =
        LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP)

    fun radial(x: Float, y: Float, r: Float, colors: IntArray, pos: FloatArray? = null) =
        RadialGradient(x, y, max(0.01f, r), colors, pos, Shader.TileMode.CLAMP)

    fun rr(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, p: Paint) = c.drawRoundRect(x, y, x + w, y + h, r, r, p)

    companion object {
        val overlay = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        val multiply = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        val plus = PorterDuffXfermode(PorterDuff.Mode.ADD)

        fun typeface(base: Typeface, weight: Int): Typeface =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, false)
            else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

        fun font(base: Typeface, weight: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(base, weight)
            textSize = size
        }

        /** A bitmap of w × h dp at [scale] px per dp, drawn by [draw] in dp from (ox, oy). */
        fun bake(w: Float, h: Float, scale: Float, ox: Float = 0f, oy: Float = 0f, draw: (Canvas) -> Unit): Bitmap {
            val bmp = Bitmap.createBitmap(max(1, ceil(w * scale).toInt()), max(1, ceil(h * scale).toInt()), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            c.translate(-ox, -oy)
            draw(c)
            return bmp
        }
    }
}

/** A tile of two-octave periodic value noise, grey around 0.5, at 2 px per dp (as in the Swift port). */
internal class CompassNeedleNavGrain(wPoints: Int, hPoints: Int, cellX: Double, cellY: Double, seed: Int) {
    private val shader: BitmapShader

    init {
        val wPx = wPoints * 2
        val hPx = hPoints * 2
        val nx = max(1, (wPoints / cellX).roundToInt())
        val ny = max(1, (hPoints / cellY).roundToInt())
        fun hash(x: Int, y: Int, s: Int): Double {
            var h = x * 374_761_393 + y * 668_265_263 + s * -2_048_144_777
            h = (h xor (h ushr 13)) * 1_274_126_177
            h = h xor (h ushr 16)
            return (h and 0xFFFF) / 65535.0
        }
        fun noise(u: Double, v: Double, px: Int, py: Int, s: Int): Double {
            val x = u * px
            val y = v * py
            val ix = floor(x).toInt()
            val iy = floor(y).toInt()
            val fx = x - ix
            val fy = y - iy
            val sx = fx * fx * (3 - 2 * fx)
            val sy = fy * fy * (3 - 2 * fy)
            val x0 = ((ix % px) + px) % px
            val x1 = (((ix + 1) % px) + px) % px
            val y0 = ((iy % py) + py) % py
            val y1 = (((iy + 1) % py) + py) % py
            val a = hash(x0, y0, s)
            val b = hash(x1, y0, s)
            val c = hash(x0, y1, s)
            val d = hash(x1, y1, s)
            return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy
        }
        val pixels = IntArray(wPx * hPx)
        for (j in 0 until hPx) {
            val v = (j + 0.5) / hPx
            for (i in 0 until wPx) {
                val u = (i + 0.5) / wPx
                val n = noise(u, v, nx, ny, seed) * 0.67 + noise(u, v, nx * 2, ny * 2, seed + 17) * 0.33
                val g = ((0.5 + (n - 0.5) * 1.6).coerceIn(0.0, 1.0) * 255).roundToInt()
                pixels[j * wPx + i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, wPx, hPx, Bitmap.Config.ARGB_8888)
        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
        }
    }

    /** Lays the grain over [rect] with [mode] (overlay by default) at [alpha]. */
    fun draw(c: Canvas, rect: RectF, alpha: Double, paint: Paint, mode: PorterDuffXfermode = CompassNeedleNavGfx.overlay) {
        paint.reset()
        paint.shader = shader
        paint.alpha = (255 * alpha).roundToInt()
        paint.xfermode = mode
        c.drawRect(rect, paint)
    }
}
