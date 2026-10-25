package com.ngocdevv.nativecook.blocks.buttons.matchstickstrikebutton

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
 MatchstickStrikeButton — drawing helpers in android.graphics, so the art reads like the Skia code it was ported
 from: a small pool of paints, gradients, blurred paints (Skia sigma), a grain tile and bitmaps
 baked once. Everything is drawn in dp (the canvas is scaled by the density).
*/

internal fun matchRgb(hex: Int) = (0xFF shl 24) or hex

internal fun matchRgba(r: Int, g: Int, b: Int, a: Double) =
    ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

/** A colour from 0…1 floats. */
internal fun matchRgbf(r: Double, g: Double, b: Double, a: Double = 1.0) = matchRgba(
    (r.coerceIn(0.0, 1.0) * 255).roundToInt(),
    (g.coerceIn(0.0, 1.0) * 255).roundToInt(),
    (b.coerceIn(0.0, 1.0) * 255).roundToInt(),
    a,
)

internal class MatchstickStrikeButtonGfx {
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
        /** A marker for [MatchstickStrikeButtonGrain.draw]: multiply is drawn with its dark tile, source-over. */
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
internal class MatchstickStrikeButtonGrain(wPoints: Int, hPoints: Int, cellX: Double, cellY: Double, seed: Int) {
    private val shader: BitmapShader
    /**
     * The same grain as black at alpha 1 − v. Drawn source-over at opacity a it gives
     * d · (1 − a(1 − v)), which is exactly Skia's Multiply of the grey tile. PorterDuff.Mode.MULTIPLY
     * cannot stand in for it: it multiplies alpha too, so the layer under it turned see-through and dark.
     */
    private val darkShader: BitmapShader

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
        // Two-octave value noise, with the lattice hashed once and each column's and
        // row's cell and smoothstep weight worked out once, so the per-pixel work is a few
        // multiplies (it was hashing eight lattice points per pixel, which held up the first frame).
        class Octave(val px: Int, val py: Int, s: Int) {
            val lat = DoubleArray(px * py) { hash(it % px, it / px, s) }
            val x0 = IntArray(wPx)
            val x1 = IntArray(wPx)
            val sx = DoubleArray(wPx)
            init {
                for (i in 0 until wPx) {
                    val x = (i + 0.5) / wPx * px
                    val ix = floor(x).toInt()
                    val f = x - ix
                    x0[i] = ((ix % px) + px) % px
                    x1[i] = (((ix + 1) % px) + px) % px
                    sx[i] = f * f * (3 - 2 * f)
                }
            }
        }
        val o1 = Octave(nx, ny, seed)
        val o2 = Octave(nx * 2, ny * 2, seed + 17)
        val pixels = IntArray(wPx * hPx)
        val dark = IntArray(wPx * hPx)
        for (j in 0 until hPx) {
            val v = (j + 0.5) / hPx
            val y1 = v * o1.py
            val iy1 = floor(y1).toInt()
            val f1 = y1 - iy1
            val sy1 = f1 * f1 * (3 - 2 * f1)
            val r10 = (((iy1 % o1.py) + o1.py) % o1.py) * o1.px
            val r11 = ((((iy1 + 1) % o1.py) + o1.py) % o1.py) * o1.px
            val y2 = v * o2.py
            val iy2 = floor(y2).toInt()
            val f2 = y2 - iy2
            val sy2 = f2 * f2 * (3 - 2 * f2)
            val r20 = (((iy2 % o2.py) + o2.py) % o2.py) * o2.px
            val r21 = ((((iy2 + 1) % o2.py) + o2.py) % o2.py) * o2.px
            for (i in 0 until wPx) {
                val l1 = o1.lat
                val a1 = l1[r10 + o1.x0[i]]
                val b1 = l1[r10 + o1.x1[i]]
                val c1 = l1[r11 + o1.x0[i]]
                val d1 = l1[r11 + o1.x1[i]]
                val s1 = o1.sx[i]
                val n1 = (a1 + (b1 - a1) * s1) * (1 - sy1) + (c1 + (d1 - c1) * s1) * sy1
                val l2 = o2.lat
                val a2 = l2[r20 + o2.x0[i]]
                val b2 = l2[r20 + o2.x1[i]]
                val c2 = l2[r21 + o2.x0[i]]
                val d2 = l2[r21 + o2.x1[i]]
                val s2 = o2.sx[i]
                val n2 = (a2 + (b2 - a2) * s2) * (1 - sy2) + (c2 + (d2 - c2) * s2) * sy2
                val n = n1 * 0.67 + n2 * 0.33
                val g = ((0.5 + (n - 0.5) * 1.6).coerceIn(0.0, 1.0) * 255).roundToInt()
                pixels[j * wPx + i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                dark[j * wPx + i] = (255 - g) shl 24
            }
        }
        val local = Matrix().apply { setScale(0.5f, 0.5f) }
        val bitmap = Bitmap.createBitmap(pixels, wPx, hPx, Bitmap.Config.ARGB_8888)
        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(local) }
        val darkBitmap = Bitmap.createBitmap(dark, wPx, hPx, Bitmap.Config.ARGB_8888)
        darkShader = BitmapShader(darkBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(local) }
    }

    /** Lays the grain over [rect] with [mode] (overlay by default) at [alpha]. */
    fun draw(c: Canvas, rect: RectF, alpha: Double, paint: Paint, mode: PorterDuffXfermode = MatchstickStrikeButtonGfx.overlay) {
        paint.reset()
        paint.alpha = (255 * alpha).roundToInt()
        if (mode === MatchstickStrikeButtonGfx.multiply) {
            paint.shader = darkShader
        } else {
            paint.shader = shader
            paint.xfermode = mode
        }
        c.drawRect(rect, paint)
    }
}
