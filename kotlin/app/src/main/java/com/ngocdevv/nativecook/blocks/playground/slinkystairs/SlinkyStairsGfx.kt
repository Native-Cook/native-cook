package com.ngocdevv.nativecook.blocks.playground.slinkystairs

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
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/*
 Slinky Stairs — drawing helpers in android.graphics, so the art reads like the Skia code it
 was ported from: colours written as in CSS ("#RRGGBB", "rgba(r,g,b,a)"), paints, gradients,
 blurred paints (Skia sigma), a grain tile and bitmaps baked once. Everything is in dp.
*/

private val SlinkyColorCache = HashMap<String, Int>()

/** "#RRGGBB" or "rgba(r,g,b,a)" as an ARGB int. */
internal fun slinkyColor(s: String): Int = SlinkyColorCache.getOrPut(s) {
    if (s.startsWith("#")) {
        (0xFF shl 24) or s.substring(1, 7).toInt(16)
    } else {
        val v = s.substringAfter("(").substringBefore(")").split(",").map { it.trim().toDouble() }
        val a = if (v.size > 3) v[3] else 1.0
        ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or (v[0].toInt() shl 16) or (v[1].toInt() shl 8) or v[2].toInt()
    }
}

internal class SlinkyGfx(private val live: Boolean) {
    private val pool = Array(16) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private var next = 0
    private val blurs = HashMap<Float, BlurMaskFilter>()
    /** Hardware canvases ignore mask filters below API 28: blurred shapes are then left out on screen. */
    private val blurOk = !live || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun paint(color: String, alpha: Double = 1.0): Paint {
        val p = pool[next]
        next = (next + 1) % pool.size
        p.reset()
        p.isAntiAlias = true
        p.isFilterBitmap = true
        p.color = slinkyColor(color)
        if (alpha < 1) p.alpha = (255 * alpha.coerceIn(0.0, 1.0)).roundToInt()
        return p
    }

    fun shaded(shader: Shader, alpha: Double = 1.0): Paint {
        val p = paint("#000000")
        p.shader = shader
        if (alpha < 1) p.alpha = (255 * alpha.coerceIn(0.0, 1.0)).roundToInt()
        return p
    }

    fun stroke(color: String, width: Float, alpha: Double = 1.0, round: Boolean = true): Paint {
        val p = paint(color, alpha)
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        if (round) p.strokeCap = Paint.Cap.ROUND
        return p
    }

    fun strokeShaded(shader: Shader, width: Float, round: Boolean = false): Paint {
        val p = shaded(shader)
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        if (round) p.strokeCap = Paint.Cap.ROUND
        return p
    }

    fun blur(sigma: Float): BlurMaskFilter {
        val q = max(0.25f, (sigma * 4).roundToInt() / 4f)
        return blurs.getOrPut(q) { BlurMaskFilter(sigmaToRadius(q), BlurMaskFilter.Blur.NORMAL) }
    }

    fun blurred(color: String, alpha: Double, sigma: Float): Paint {
        val p = paint(color, alpha)
        if (!blurOk) p.alpha = 0
        p.maskFilter = blur(sigma)
        return p
    }

    companion object {
        val overlay = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        val dstIn = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        fun colors(list: Array<String>) = IntArray(list.size) { slinkyColor(list[it]) }

        fun linear(x0: Float, y0: Float, x1: Float, y1: Float, list: Array<String>, pos: FloatArray? = null, mode: Shader.TileMode = Shader.TileMode.CLAMP) =
            LinearGradient(x0, y0, x1, y1, colors(list), pos, mode)

        fun radial(x: Float, y: Float, r: Float, list: Array<String>, pos: FloatArray? = null) =
            RadialGradient(x, y, max(0.1f, r), colors(list), pos, Shader.TileMode.CLAMP)

        fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = RectF(cx - rx, cy - ry, cx + rx, cy + ry)

        /** A bitmap of w × h dp at [scale] px per dp, drawn by [draw] in dp from (ox, oy). */
        fun bake(ox: Float, oy: Float, w: Float, h: Float, scale: Float, draw: (Canvas) -> Unit): SlinkyBaked {
            val bmp = Bitmap.createBitmap(max(1, ceil(w * scale).toInt()), max(1, ceil(h * scale).toInt()), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            c.translate(-ox, -oy)
            draw(c)
            return SlinkyBaked(bmp, RectF(ox, oy, ox + w, oy + h))
        }
    }
}

/** A baked bitmap and the dp rectangle it covers. */
internal class SlinkyBaked(val bmp: Bitmap, val rect: RectF) {
    fun draw(c: Canvas, p: Paint) = c.drawBitmap(bmp, null, rect, p)
}

/**
 * A tile of fine grey grain (an integer hash, softened by averaging with its neighbours), about a
 * dp per grain, laid over a rectangle with overlay at an opacity: the stand-in for Skia's fractal noise.
 */
internal class SlinkyGrain(seed: Int) {
    private val shader: BitmapShader

    init {
        val n = 96
        fun hash(x: Int, y: Int): Int {
            var h = x * 374_761_393 + y * 668_265_263 + seed * -2_048_144_777
            h = (h xor (h ushr 13)) * 1_274_126_177
            h = h xor (h ushr 16)
            return h and 0xFF
        }
        val px = IntArray(n * n)
        for (j in 0 until n) {
            for (i in 0 until n) {
                val v = (hash(i, j) * 2 + hash((i + 1) % n, j) + hash(i, (j + 1) % n)) / 4
                val g = (128 + (v - 128) * 1.6).roundToInt().coerceIn(0, 255)
                px[j * n + i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(0.9f, 0.9f) })
        }
    }

    fun draw(c: Canvas, rect: RectF, alpha: Double) {
        val p = Paint()
        p.shader = shader
        p.alpha = (255 * alpha).roundToInt()
        p.xfermode = SlinkyGfx.overlay
        c.drawRect(rect, p)
    }
}
