package com.ngocdevv.nativecook.blocks.cards.postcardstampcancel

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.CARD_H
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.CARD_W
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.CARD_X
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.CARD_Y
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.DESIGN_H
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.DESIGN_W
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.INK_H
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.INK_W
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.PAD_R
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.PAD_X
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.PAD_Y
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.RING_INNER
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.RING_R
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.RING_X
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.RING_Y
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.TOOL_H
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.TOOL_KNOB
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.TOOL_PAD
import com.ngocdevv.nativecook.blocks.cards.postcardstampcancel.PostcardStampCancelMotion.TOOL_W
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Everything static, drawn once off the main thread with android.graphics at [scale] px per dp. */
internal class PostcardStampCancelArtSet(
    val scale: Float,
    /** The rubber face, white on clear, INK_W × INK_H dp. */
    val mask: Bitmap,
    /** The handstamp from above, (TOOL + 2 · TOOL_PAD) dp. */
    val tool: Bitmap,
    /** The tool's shadows: a soft one at its resting and at its lifted blur, and the contact one. */
    val shadowNear: Bitmap,
    val shadowFar: Bitmap,
    val shadowContact: Bitmap,
    val shadowMargin: Float,
    /** The desk's spare card, ink pad and lid in design dp. */
    val desk: Bitmap,
    /** The card's two faces (CARD_W × CARD_H dp) and its shadow (with [cardShadowMargin]). */
    val front: Bitmap,
    val back: Bitmap,
    val cardShadow: Bitmap,
    val cardShadowMargin: Float,
    /** A repeating linen weave tile, drawn over the whole screen. */
    val weave: Bitmap,
)

internal object PostcardStampCancelArt {
    private const val PAPER = 0xFFF4ECDA.toInt()
    private const val INK = 0xFF26305A.toInt()

    private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
    private fun hex(c: Long) = c.toInt()

    private fun sigma(s: Float) = max(0.01f, (s - 0.5f) / 0.57735f)

    private fun blurred(color: Int, alpha: Float, sigmaDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.alpha = (alpha * 255).roundToInt()
        maskFilter = BlurMaskFilter(sigma(sigmaDp), BlurMaskFilter.Blur.NORMAL)
    }

    private fun lin(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray? = null) =
        LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP)

    private fun rad(x: Float, y: Float, r: Float, colors: IntArray, pos: FloatArray? = null) =
        RadialGradient(x, y, r, colors, pos, Shader.TileMode.CLAMP)

    private fun fill(shader: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }

    private fun surface(wDp: Float, hDp: Float, s: Float): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(ceil(wDp * s).toInt().coerceAtLeast(1), ceil(hDp * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        return bmp to c
    }

    // Seeded value noise with integer hashes (no sin), optionally periodic so a tile wraps.
    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun vnoise(x: Float, y: Float, seed: Int, px: Int = 0, py: Int = 0): Float {
        val fx = floor(x)
        val fy = floor(y)
        val ix = fx.toInt()
        val iy = fy.toInt()
        val ux = x - fx
        val uy = y - fy
        val sx = ux * ux * (3 - 2 * ux)
        val sy = uy * uy * (3 - 2 * uy)
        fun w(i: Int, p: Int) = if (p > 0) ((i % p) + p) % p else i
        val x0 = w(ix, px); val x1 = w(ix + 1, px)
        val y0 = w(iy, py); val y1 = w(iy + 1, py)
        val a = hash(x0, y0, seed); val b = hash(x1, y0, seed)
        val c = hash(x0, y1, seed); val d = hash(x1, y1, seed)
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy
    }

    /**
     * Grey fractal noise as a signed overlay: each pixel is white (lighter than mid-grey) or black
     * (darker) with alpha |v − 0.5| · 2 · [strength], drawn source-over. Frequencies are cycles
     * per px; [periodic] makes the tile wrap.
     */
    private suspend fun grain(wPx: Int, hPx: Int, fx: Float, fy: Float, octaves: Int, seed: Int, strength: Float, periodic: Boolean): Bitmap {
        val pixels = IntArray(wPx * hPx)
        for (y in 0 until hPx) {
            currentCoroutineContext().ensureActive()
            for (x in 0 until wPx) {
                var v = 0f
                var amp = 0.5f
                var total = 0f
                var f = 1f
                for (o in 0 until octaves) {
                    val px = if (periodic) (wPx * fx * f).roundToInt().coerceAtLeast(1) else 0
                    val py = if (periodic) (hPx * fy * f).roundToInt().coerceAtLeast(1) else 0
                    val nx = if (periodic) x * px / wPx.toFloat() else x * fx * f
                    val ny = if (periodic) y * py / hPx.toFloat() else y * fy * f
                    v += amp * vnoise(nx, ny, seed + o * 31, px, py)
                    total += amp
                    amp *= 0.5f
                    f *= 2f
                }
                v /= total
                val d = (v - 0.5f) * 2f
                val a = (abs(d) * strength * 255f).roundToInt().coerceIn(0, 255)
                pixels[y * wPx + x] = (a shl 24) or (if (d > 0) 0xFFFFFF else 0x000000)
            }
        }
        return Bitmap.createBitmap(pixels, wPx, hPx, Bitmap.Config.ARGB_8888)
    }

    /** Glyphs set on a circle, centred on [mid] (radians, screen angles), upright for the reader. */
    private fun ringText(c: Canvas, text: String, cx: Float, cy: Float, r: Float, mid: Float, paint: Paint, bottom: Boolean) {
        val widths = FloatArray(text.length)
        paint.getTextWidths(text, widths)
        val track = 1.4f
        var total = 0f
        for (w in widths) total += w + track
        total -= track
        val dir = if (bottom) -1f else 1f
        var a = mid - dir * total / 2f / r
        for (i in text.indices) {
            val w = widths[i]
            val at = a + dir * w / 2f / r
            c.save()
            c.translate(cx + cos(at) * r, cy + sin(at) * r)
            c.rotate(((at + if (bottom) -PI.toFloat() / 2f else PI.toFloat() / 2f) * 180f / PI.toFloat()))
            c.drawText(text[i].toString(), -w / 2f, paint.textSize * 0.36f, paint)
            c.restore()
            a += dir * (w + track) / r
        }
    }

    /** The postmark's rubber face: a date ring "LISBOA · 24 OCT" and five wavy bars, white on clear. */
    private fun bakeMask(s: Float): Bitmap {
        val (bmp, c) = surface(INK_W, INK_H, s)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        c.drawCircle(RING_X, RING_Y, RING_R, stroke)
        stroke.strokeWidth = 1.5f
        c.drawCircle(RING_X, RING_Y, RING_INNER, stroke)
        val ringFont = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textSize = 8.4f
        }
        val mid = (RING_R + RING_INNER) / 2f
        ringText(c, "LISBOA", RING_X, RING_Y, mid, -PI.toFloat() / 2f, ringFont, false)
        ringText(c, "24 · OCT · 26", RING_X, RING_Y, mid, PI.toFloat() / 2f, ringFont, true)
        for (side in intArrayOf(-1, 1)) {
            val sx = RING_X + side * mid
            val sy = RING_Y
            val star = Path()
            for (k in 0 until 10) {
                val rr = if (k % 2 == 0) 2.6f else 1.1f
                val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
                if (k == 0) star.moveTo(sx + cos(a) * rr, sy + sin(a) * rr) else star.lineTo(sx + cos(a) * rr, sy + sin(a) * rr)
            }
            star.close()
            c.drawPath(star, ink)
        }
        val hour = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textSize = 10f
        }
        val tw = hour.measureText("13·40")
        c.drawText("13·40", RING_X - tw / 2f, RING_Y + 3.6f, hour)
        val bars = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2.3f
        }
        for (i in 0 until 5) {
            val y0 = RING_Y + (i - 2) * 9.4f
            val reach = sqrt(max(0f, RING_R * RING_R - (y0 - RING_Y) * (y0 - RING_Y)))
            val x0 = RING_X + reach + 4f
            val path = Path()
            var x = x0
            var first = true
            while (x <= INK_W - 4f) {
                val y = y0 + sin((x - x0) / 18f * PI.toFloat() * 2f) * 2.6f
                if (first) path.moveTo(x, y) else path.lineTo(x, y)
                first = false
                x += 1.5f
            }
            c.drawPath(path, bars)
        }
        return bmp
    }

    /** The handstamp from above: a rubber-faced block on a walnut mount, a turned knob, its edges lit. */
    private suspend fun bakeTool(s: Float): Bitmap {
        val pad = TOOL_PAD
        val (bmp, c) = surface(TOOL_W + pad * 2, TOOL_H + pad * 2, s)
        c.translate(pad, pad)
        val rubber = RectF(0f, 0f, TOOL_W, TOOL_H)
        c.drawRoundRect(rubber, 12f, 12f, fill(lin(0f, 0f, 0f, TOOL_H, intArrayOf(hex(0xFF2B2E3A), hex(0xFF1B1D26), hex(0xFF11131A)))))
        val mount = RectF(4f, 3f, TOOL_W - 4f, TOOL_H - 5f)
        c.drawRoundRect(
            mount, 10f, 10f,
            fill(lin(0f, 0f, TOOL_W, TOOL_H, intArrayOf(hex(0xFF9A6740), hex(0xFF7A4D2D), hex(0xFF5E3A21), hex(0xFF7C5132)), floatArrayOf(0f, 0.4f, 0.75f, 1f))),
        )
        c.save()
        val clip = Path().apply { addRoundRect(mount, 10f, 10f, Path.Direction.CW) }
        c.clipPath(clip)
        // Wood grain: noise long along the block, fine across it.
        val gw = ceil(TOOL_W * s).toInt()
        val gh = ceil(TOOL_H * s).toInt()
        val wood = grain(gw, gh, 0.006f / s, 0.28f / s, 3, 17, 0.4f, false)
        c.drawBitmap(wood, null, RectF(0f, 0f, TOOL_W, TOOL_H), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawRect(0f, 3f, TOOL_W, 14f, fill(lin(0f, 3f, 0f, 14f, intArrayOf(argb(0.45f, 255, 226, 190), argb(0f, 255, 226, 190)))))
        c.restore()
        c.drawRoundRect(
            RectF(12f, TOOL_H - 26f, 48f, TOOL_H - 14f), 2f, 2f,
            fill(lin(12f, TOOL_H - 24f, 52f, TOOL_H - 12f, intArrayOf(hex(0xFFF1D592), hex(0xFFB98A3E), hex(0xFFE3C27A)))),
        )
        val kx = TOOL_W / 2f + 6f
        val ky = TOOL_H / 2f - 2f
        c.drawCircle(kx + 2f, ky + 4f, TOOL_KNOB, blurred(0xFF000000.toInt(), 0.5f, 5f))
        c.drawCircle(
            kx, ky, TOOL_KNOB,
            fill(rad(kx - 9f, ky - 11f, TOOL_KNOB * 1.5f, intArrayOf(hex(0xFFE3B07A), hex(0xFFA86D3E), hex(0xFF6E4223), hex(0xFF4A2B16)), floatArrayOf(0f, 0.35f, 0.75f, 1f))),
        )
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = argb(0.45f, 0x3A, 0x22, 0x12)
        }
        for (rr in floatArrayOf(TOOL_KNOB * 0.42f, TOOL_KNOB * 0.7f, TOOL_KNOB - 1.5f)) c.drawCircle(kx, ky, rr, ring)
        c.drawCircle(kx - 10f, ky - 12f, 12f, fill(rad(kx - 10f, ky - 12f, 12f, intArrayOf(argb(0.75f, 255, 240, 215), argb(0f, 255, 240, 215)))))
        return bmp
    }

    /** A black rounded rect of the tool's size, blurred by [sigmaDp], with [margin] dp around it. */
    private fun bakeToolShadow(s: Float, sigmaDp: Float, radius: Float, margin: Float): Bitmap {
        val (bmp, c) = surface(TOOL_W + margin * 2, TOOL_H + margin * 2, s)
        val p = if (sigmaDp > 0f) blurred(0xFF05070C.toInt(), 1f, sigmaDp) else Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF05070C.toInt() }
        c.drawRoundRect(RectF(margin, margin, margin + TOOL_W, margin + TOOL_H), radius, radius, p)
        return bmp
    }

    /** The desk's still life in design dp: a spare card under the postcard, and the ink pad in its tin with its lid. */
    private suspend fun bakeDesk(s: Float): Bitmap {
        val (bmp, c) = surface(DESIGN_W, DESIGN_H, s)
        c.save()
        c.translate(CARD_X + CARD_W / 2f, CARD_Y + CARD_H / 2f + 10f)
        c.rotate(-3.2f)
        c.drawRoundRect(RectF(-CARD_W / 2f + 2f, -CARD_H / 2f + 8f, CARD_W / 2f + 2f, CARD_H / 2f + 8f), 8f, 8f, blurred(0xFF000000.toInt(), 0.45f, 10f))
        c.drawRoundRect(
            RectF(-CARD_W / 2f, -CARD_H / 2f, CARD_W / 2f, CARD_H / 2f), 6f, 6f,
            fill(lin(0f, -CARD_H / 2f, 0f, CARD_H / 2f, intArrayOf(hex(0xFFE7DCC4), hex(0xFFDCCDB0)))),
        )
        c.restore()

        val x = PAD_X
        val y = PAD_Y
        val r = PAD_R
        c.drawCircle(x + 64f, y + 52f, r - 2f, blurred(0xFF000000.toInt(), 0.5f, 9f))
        c.drawCircle(x + 62f, y + 48f, r - 4f, fill(rad(x + 44f, y + 30f, r * 1.3f, intArrayOf(hex(0xFFD9DEE6), hex(0xFF8E97A6), hex(0xFF566072)), floatArrayOf(0f, 0.55f, 1f))))
        c.drawCircle(x + 62f, y + 48f, r - 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = argb(0.35f, 255, 255, 255)
        })
        c.drawCircle(x + 3f, y + 8f, r + 2f, blurred(0xFF000000.toInt(), 0.55f, 10f))
        c.drawCircle(x, y + 1.5f, r + 1f, blurred(0xFF000000.toInt(), 0.6f, 1.5f))
        val rimColors = intArrayOf(hex(0xFFC8CFDA), hex(0xFF6E7889), hex(0xFFE8ECF2), hex(0xFF596374), hex(0xFFC8CFDA))
        c.drawCircle(x, y, r, fill(SweepGradient(x, y, rimColors, null)))
        c.drawCircle(x, y, r - 7f, fill(rad(x - 14f, y - 16f, r * 1.1f, intArrayOf(hex(0xFF34479A), hex(0xFF22306E), hex(0xFF151D45)), floatArrayOf(0f, 0.55f, 1f))))
        c.save()
        c.clipPath(Path().apply { addCircle(x, y, r - 7f, Path.Direction.CW) })
        val fw = ceil(r * 2 * s).toInt()
        val fibres = grain(fw, fw, 0.7f / s, 0.7f / s, 3, 41, 0.22f, false)
        c.drawBitmap(fibres, null, RectF(x - r, y - r, x + r, y + r), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawCircle(x - 18f, y - 22f, 26f, fill(rad(x - 18f, y - 22f, 26f, intArrayOf(argb(0.35f, 190, 205, 255), argb(0f, 190, 205, 255)))))
        c.drawCircle(x, y, r - 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = argb(0.45f, 0, 0, 0)
            maskFilter = BlurMaskFilter(sigma(3f), BlurMaskFilter.Blur.NORMAL)
        })
        c.restore()
        return bmp
    }

    /** The card's shadow: a dark rounded rect with two soft CSS shadows, [margin] dp around it. */
    private fun bakeCardShadow(s: Float, margin: Float): Bitmap {
        val w = CARD_W - 12f
        val h = CARD_H - 8f
        val (bmp, c) = surface(w + margin * 2, h + margin * 2, s)
        val box = RectF(margin, margin, margin + w, margin + h)
        c.save()
        c.translate(0f, 14f)
        c.drawRoundRect(box, 10f, 10f, blurred(argb(1f, 3, 6, 14), 0.55f, 15f))
        c.restore()
        c.save()
        c.translate(0f, 2f)
        c.drawRoundRect(box, 10f, 10f, blurred(argb(1f, 3, 6, 14), 0.45f, 2f))
        c.restore()
        c.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.35f, 4, 8, 18) })
        return bmp
    }

    private fun serif(style: Int, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, style)
        textSize = size
        this.color = color
    }

    /** A colourful invented stamp: a yellow tram climbing past tiles at sunset, with perforations. */
    private fun drawStamp(c: Canvas) {
        val w = 70f
        val h = 84f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = hex(0xFFFBF7EE)
        c.drawRect(0f, 0f, w, h, p)
        p.color = PAPER
        var hx = 3.5f
        while (hx < w) {
            c.drawCircle(hx, 0f, 2.5f, p); c.drawCircle(hx, h, 2.5f, p); hx += 7f
        }
        var hy = 3.5f
        while (hy < h) {
            c.drawCircle(0f, hy, 2.5f, p); c.drawCircle(w, hy, 2.5f, p); hy += 7f
        }
        c.save()
        c.clipRect(6f, 6f, w - 6f, h - 6f)
        c.drawRect(6f, 6f, w - 6f, h - 6f, fill(lin(0f, 6f, 0f, h - 6f, intArrayOf(hex(0xFFF6CE7A), hex(0xFFEE8B50), hex(0xFFC4523F)), floatArrayOf(0f, 0.55f, 1f))))
        p.color = argb(0.9f, 0xFF, 0xE3, 0xA0)
        c.drawCircle(47f, 30f, 9f, p)
        fun poly(vararg pts: Float, color: Int) {
            val path = Path()
            path.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
            path.close()
            p.color = color
            c.drawPath(path, p)
        }
        poly(6f, 52f, 16f, 44f, 24f, 49f, 33f, 41f, 43f, 48f, 52f, 43f, 64f, 50f, 64f, 62f, 6f, 62f, color = hex(0xFFB4463A))
        poly(6f, 56f, 18f, 50f, 30f, 55f, 42f, 49f, 64f, 56f, 64f, 64f, 6f, 64f, color = hex(0xFF8E3530))
        c.drawLine(40f, 37f, 33f, 47f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0xFF3A2A2A); strokeWidth = 0.8f; style = Paint.Style.STROKE })
        p.color = hex(0xFFF2C230)
        c.drawRoundRect(RectF(17f, 47f, 49f, 62f), 3f, 3f, p)
        p.color = hex(0xFFE3A51E)
        c.drawRect(17f, 56f, 49f, 58.2f, p)
        p.color = hex(0xFFFFF4D6)
        for (x in intArrayOf(20, 27, 34, 41)) c.drawRoundRect(RectF(x.toFloat(), 49.5f, x + 5f, 54.5f), 1f, 1f, p)
        p.color = hex(0xFF2C5AA6)
        c.drawRect(6f, 64f, w - 6f, 78f, p)
        for (x in intArrayOf(9, 21, 33, 45, 57)) {
            val fx = x.toFloat()
            p.color = hex(0xFFF4F1E8); c.drawRect(fx, 66f, fx + 9f, 75f, p)
            p.color = hex(0xFF2C5AA6); c.drawCircle(fx + 4.5f, 70.5f, 3f, p)
            p.color = hex(0xFFF4F1E8); c.drawCircle(fx + 4.5f, 70.5f, 1.2f, p)
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = hex(0xFFFFF6E2)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 6.2f
            letterSpacing = 0.6f / 6.2f
        }
        c.drawText("POSTA LUSA", 9f, 15f, label)
        val price = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = hex(0xFFFFF6E2)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }
        c.drawText("0,85 €", w - 9f, 42f, price)
        c.restore()
    }

    /** The photo, cropped to fill [dst] (dp), like a cover fit. */
    private fun drawCover(c: Canvas, photo: Bitmap, dst: RectF) {
        val sw = photo.width.toFloat()
        val sh = photo.height.toFloat()
        val k = max(dst.width() / sw, dst.height() / sh)
        val cw = dst.width() / k
        val ch = dst.height() / k
        val sx = (sw - cw) / 2f
        val sy = (sh - ch) / 2f
        c.drawBitmap(photo, Rect(sx.roundToInt(), sy.roundToInt(), (sx + cw).roundToInt(), (sy + ch).roundToInt()), dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    /** Writing side: note, address, stamp, the printed line and the dog-eared corner. */
    private fun bakeFront(s: Float, photo: Bitmap): Bitmap {
        val (bmp, c) = surface(CARD_W, CARD_H, s)
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, CARD_W, CARD_H), 6f, 6f, Path.Direction.CW) })
        c.drawRect(0f, 0f, CARD_W, CARD_H, fill(lin(0f, 0f, CARD_W, CARD_H, intArrayOf(hex(0xFFF8F2E4), PAPER, hex(0xFFEADFC6)), floatArrayOf(0f, 0.55f, 1f))))

        val note = serif(Typeface.ITALIC, 13f, INK)
        val text = "Day four. Rode the yellow tram all the way up to the castle, then got lost in Alfama on purpose. Custard tarts still warm — I have had three."
        val fm = note.fontMetrics
        val natural = fm.descent - fm.ascent
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, note, 160)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(20f - natural, 1f)
            .setIncludePad(false)
            .build()
        c.save()
        c.translate(16f, 16f + (20f - natural) / 2f)
        layout.draw(c)
        c.restore()
        val sign = serif(Typeface.ITALIC, 15f, INK)
        c.drawText("— J.", 112f, 186f - sign.fontMetrics.ascent, sign)

        c.drawRect(188f, 22f, 189f, CARD_H - 22f, Paint().apply { color = argb(0.28f, 90, 70, 50) })

        // The stamp with its little shadow.
        c.save()
        c.translate(262f, 14f)
        c.drawRect(0f, 1f, 70f, 85f, blurred(argb(1f, 40, 25, 10), 0.35f, 0.75f))
        drawStamp(c)
        c.restore()

        val addr = serif(Typeface.ITALIC, 14f, INK)
        val afm = addr.fontMetrics
        val rule = Paint().apply { color = argb(0.25f, 90, 70, 50) }
        listOf("Ines Marlowe", "14 Wren Lane", "Bristol BS8 4QT", "United Kingdom").forEachIndexed { i, line ->
            val top = 120f + i * 25f
            c.drawRect(200f, top + 21f, CARD_W - 14f, top + 22f, rule)
            // A 20 dp line box sitting on the rule.
            val boxTop = top + 1f
            val baseline = boxTop + (20f - (afm.descent - afm.ascent)) / 2f - afm.ascent
            c.drawText(line, 204f, baseline, addr)
        }
        val printed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(0.5f, 90, 70, 50)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 7f
            letterSpacing = 1.4f / 7f
        }
        c.drawText("POST CARD · CARTE POSTALE", 200f, CARD_H - 10f - printed.fontMetrics.descent, printed)

        // The dog-ear: the bottom-right corner folded back to show the photo side.
        val es = 34f
        c.save()
        c.translate(CARD_W - es, CARD_H - es)
        val tri = Path().apply { moveTo(es, 0f); lineTo(es, es); lineTo(0f, es); close() }
        c.clipPath(tri)
        c.drawColor(hex(0xFF1E3150))
        drawCover(c, photo, RectF(-60f, -30f, 50f, 44f))
        val crease = Path().apply { moveTo(es, 0f); lineTo(0f, es); lineTo(4f, es); lineTo(es, 4f); close() }
        c.drawPath(
            crease,
            fill(lin(0f, es, es, 0f, intArrayOf(argb(0f, 0, 0, 0), argb(0.35f, 0, 0, 0), argb(0f, 0, 0, 0)), floatArrayOf(0.42f, 0.5f, 0.56f))),
        )
        c.drawLine(es, 0f, 0f, es, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.7f, 255, 255, 255); strokeWidth = 0.8f })
        c.restore()
        return bmp
    }

    /** Photo side, unmirrored (the card draws it mirrored while it is turned). */
    private fun bakeBack(s: Float, photo: Bitmap): Bitmap {
        val (bmp, c) = surface(CARD_W, CARD_H, s)
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, CARD_W, CARD_H), 6f, 6f, Path.Direction.CW) })
        c.drawColor(hex(0xFFFBF8F1))
        val inner = RectF(8f, 8f, CARD_W - 8f, CARD_H - 8f)
        c.save()
        c.clipPath(Path().apply { addRoundRect(inner, 2f, 2f, Path.Direction.CW) })
        drawCover(c, photo, inner)
        val shadeTop = CARD_H - 8f - 110f
        c.drawRect(
            8f, shadeTop, CARD_W - 8f, CARD_H - 8f,
            fill(lin(0f, shadeTop + 110f * 0.45f, 0f, CARD_H - 8f, intArrayOf(argb(0f, 20, 24, 40), argb(0.55f, 20, 24, 40)))),
        )
        c.restore()
        val greet = serif(Typeface.BOLD_ITALIC, 34f, hex(0xFFFFF7E8)).apply { setShadowLayer(3f, 0f, 1f, argb(0.45f, 0, 0, 0)) }
        c.drawText("Lisboa", 22f, CARD_H - 34f - greet.fontMetrics.descent, greet)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(0.85f, 255, 247, 232)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 9f
            letterSpacing = 1.6f / 9f
        }
        c.drawText("ALFAMA ROOFTOPS · TEJO", 24f, CARD_H - 20f - sub.fontMetrics.descent, sub)
        return bmp
    }

    fun loadPhoto(assets: AssetManager): Bitmap =
        assets.open("postcardstampcancel/postcardstampcancel_lisbon.jpg").use { stream ->
            checkNotNull(BitmapFactory.decodeStream(stream)) { "Cannot decode the bundled Lisbon photo" }
        }

    /** Bakes the whole set; checks for cancellation between pieces and per row in pixel loops. */
    suspend fun bake(assets: AssetManager, density: Float): PostcardStampCancelArtSet {
        val s = minOf(2f, density)
        val photo = loadPhoto(assets)
        currentCoroutineContext().ensureActive()
        val front = bakeFront(s, photo)
        currentCoroutineContext().ensureActive()
        val mask = bakeMask(s)
        val tool = bakeTool(s)
        val margin = 40f
        val near = bakeToolShadow(s, 5f, 14f, margin)
        val far = bakeToolShadow(s, 19f, 14f, margin)
        val contact = bakeToolShadow(s, 2f, 12f, margin)
        currentCoroutineContext().ensureActive()
        val back = bakeBack(s, photo)
        val cardShadowMargin = 48f
        val cardShadow = bakeCardShadow(s, cardShadowMargin)
        val desk = bakeDesk(s)
        // The weave: fine vertical warp plus horizontal weft, a tile that wraps.
        val tile = 192
        val warp = grain(tile, tile, 0.9f / s, 0.08f / s, 2, 3, 0.16f, true)
        val weft = grain(tile, tile, 0.08f / s, 0.9f / s, 2, 5, 0.16f, true)
        val weave = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888)
        Canvas(weave).apply {
            drawBitmap(warp, 0f, 0f, null)
            drawBitmap(weft, 0f, 0f, null)
        }
        return PostcardStampCancelArtSet(s, mask, tool, near, far, contact, margin, desk, front, back, cardShadow, cardShadowMargin, weave)
    }
}
