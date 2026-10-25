package com.ngocdevv.nativecook.blocks.lists.cardcataloglist

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DESIGN_W
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DRAWER_BOTTOM
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DRAWER_W
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DRAWER_X
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.DRAWER_Y
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.FRONT_H
import com.ngocdevv.nativecook.blocks.lists.cardcataloglist.CardCatalogListMotion.FRONT_W
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Static art, baked once off the main thread at [scale] px per dp. */
internal class CardCatalogArtSet(
    val scale: Float,
    /** The drawer's inside with its shadow, DRAWER_BOX in design dp. */
    val drawer: Bitmap,
    /** The front panel (FRONT_W + 12) × (FRONT_H + 24) dp. */
    val front: Bitmap,
    /** Wrapping wood-grain tiles for the desk: long stretched grain and fine fibre. */
    val grain: Bitmap,
    val fine: Bitmap,
)

internal object CardCatalogListArt {
    /** The drawer bitmap's box in design dp (room for its shadow). */
    const val DRAWER_BOX_X = 0f
    const val DRAWER_BOX_Y = DRAWER_Y - 24f
    const val DRAWER_BOX_H = DRAWER_BOTTOM - DRAWER_Y + 64f

    private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
    private fun hex(c: Long) = c.toInt()
    private fun sigma(s: Float) = max(0.01f, (s - 0.5f) / 0.57735f)
    private fun lin(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray? = null) =
        LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP)
    private fun fill(shader: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    private fun blurred(alpha: Float, sigmaDp: Float, color: Int = 0xFF000000.toInt()) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.alpha = (alpha * 255f).roundToInt()
        maskFilter = BlurMaskFilter(sigma(sigmaDp), BlurMaskFilter.Blur.NORMAL)
    }

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    /** A wrapping tile of fractal value noise as a signed overlay (white lighter, black darker). */
    private suspend fun tile(w: Int, h: Int, cx: Int, cy: Int, seed: Int, strength: Float): Bitmap {
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            for (x in 0 until w) {
                var v = 0f
                var amp = 0.5f
                var total = 0f
                var px = cx
                var py = cy
                for (o in 0 until 3) {
                    val fx = x * px / w.toFloat()
                    val fy = y * py / h.toFloat()
                    val ix = floor(fx).toInt()
                    val iy = floor(fy).toInt()
                    val ux = fx - ix
                    val uy = fy - iy
                    val sx = ux * ux * (3 - 2 * ux)
                    val sy = uy * uy * (3 - 2 * uy)
                    val a = hash(ix % px, iy % py, seed + o)
                    val b = hash((ix + 1) % px, iy % py, seed + o)
                    val c = hash(ix % px, (iy + 1) % py, seed + o)
                    val d = hash((ix + 1) % px, (iy + 1) % py, seed + o)
                    val top = a + (b - a) * sx
                    val bottom = c + (d - c) * sx
                    v += amp * (top + (bottom - top) * sy)
                    total += amp
                    amp *= 0.5f
                    px *= 2
                    py *= 2
                }
                val dv = (v / total - 0.5f) * 2f
                val a = (abs(dv) * strength * 255f).roundToInt().coerceIn(0, 255)
                pixels[y * w + x] = (a shl 24) or (if (dv > 0) 0xFFFFFF else 0)
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun tiled(bmp: Bitmap, s: Float, alpha: Float) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1f / s, 1f / s) })
        }
        this.alpha = (alpha * 255f).roundToInt()
    }

    /** Wood: a three-stop base with the long grain and the fine fibre over it. */
    private fun wood(c: Canvas, r: RectF, base: IntArray, grain: Bitmap, fine: Bitmap, s: Float, grainAlpha: Float) {
        c.drawRect(r, fill(lin(r.left, r.top, r.left, r.bottom, base, floatArrayOf(0f, 0.55f, 1f))))
        c.drawRect(r, tiled(grain, s, grainAlpha))
        c.drawRect(r, tiled(fine, s, 1f))
    }

    /** The drawer's inside: a deep box, darkest at the back, its walls lit from the top left. */
    private fun bakeDrawer(s: Float, grain: Bitmap, fine: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(ceil(DESIGN_W * s).toInt(), ceil(DRAWER_BOX_H * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        c.translate(-DRAWER_BOX_X, -DRAWER_BOX_Y)
        val d = RectF(DRAWER_X, DRAWER_Y, DRAWER_X + DRAWER_W, DRAWER_BOTTOM)
        c.drawRoundRect(RectF(d.left - 4f, d.top + 6f, d.right + 4f, d.bottom + 6f), 14f, 14f, blurred(0.7f, 14f, hex(0xFF0A0604)))
        c.drawRoundRect(d, 10f, 10f, fill(lin(0f, d.top, 0f, d.bottom, intArrayOf(hex(0xFF140C07), hex(0xFF22150D), hex(0xFF2E1D12)), floatArrayOf(0f, 0.6f, 1f))))
        c.save()
        c.clipPath(Path().apply { addRoundRect(d, 10f, 10f, Path.Direction.CW) })
        val wall = 12f
        wood(c, RectF(d.left, d.top, d.left + wall, d.bottom), intArrayOf(hex(0xFF5A3F2B), hex(0xFF4A3323), hex(0xFF3B281B)), grain, fine, s, 0.9f)
        wood(c, RectF(d.right - wall, d.top, d.right, d.bottom), intArrayOf(hex(0xFF4A3323), hex(0xFF3B281B), hex(0xFF2E1F15)), grain, fine, s, 0.9f)
        c.drawRect(
            d.left, d.top, d.right, d.top + 90f,
            fill(lin(0f, d.top, 0f, d.top + 90f, intArrayOf(argb(0.85f, 4, 2, 1), argb(0.35f, 4, 2, 1), argb(0f, 4, 2, 1)))),
        )
        c.drawRoundRect(d, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = argb(0.35f, 0, 0, 0)
            maskFilter = BlurMaskFilter(sigma(8f), BlurMaskFilter.Blur.NORMAL)
        })
        c.restore()
        c.drawRoundRect(RectF(d.left + 0.5f, d.top + 0.5f, d.right - 0.5f, d.bottom - 0.5f), 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = argb(0.35f, 0xC8, 0x9A, 0x6A)
        })
        return bmp
    }

    private fun brass(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float) {
        c.drawRoundRect(RectF(x, y + 2f, x + w, y + 2f + h), r, r, blurred(0.45f, 3f))
        c.drawRoundRect(
            RectF(x, y, x + w, y + h), r, r,
            fill(lin(x, y, x + w * 0.4f, y + h, intArrayOf(hex(0xFFF6DC98), hex(0xFFC89A45), hex(0xFF8C6326), hex(0xFFD7AE5C)), floatArrayOf(0f, 0.35f, 0.7f, 1f))),
        )
        c.drawLine(x + r, y + 0.8f, x + w - r, y + 0.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0.6f, 0xFF, 0xF2, 0xC8); strokeWidth = 1f })
    }

    private fun rivet(c: Canvas, x: Float, y: Float) {
        c.drawCircle(
            x, y, 3.5f,
            fill(RadialGradient(x - 1f, y - 1.5f, 4.5f, intArrayOf(hex(0xFFFFF0C0), hex(0xFFB88A3C), hex(0xFF5E3F14)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)),
        )
    }

    /** The drawer's front: a bevelled oak panel, a brass label holder and a brass pull and knob. */
    private fun bakeFront(s: Float, grain: Bitmap, fine: Bitmap): Bitmap {
        val fw = FRONT_W
        val fh = FRONT_H
        val bmp = Bitmap.createBitmap(ceil((fw + 12f) * s).toInt(), ceil((fh + 24f) * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        val panel = RectF(0f, 0f, fw, fh)
        c.drawRoundRect(RectF(4f, 12f, fw - 4f, 12f + fh), 12f, 12f, blurred(0.45f, 12f))
        c.drawRoundRect(RectF(1f, 2f, fw - 1f, 2f + fh), 10f, 10f, blurred(0.5f, 2f))
        c.save()
        c.clipPath(Path().apply { addRoundRect(panel, 10f, 10f, Path.Direction.CW) })
        wood(c, panel, intArrayOf(hex(0xFF8A5E3B), hex(0xFF6E4A2E), hex(0xFF55381F)), grain, fine, s, 1f)
        c.drawRoundRect(RectF(14.5f, 12.5f, fw - 14.5f, fh - 12.5f), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = argb(0.35f, 0xE2, 0xB0, 0x7C)
        })
        c.drawRoundRect(RectF(13.5f, 13.5f, fw - 13.5f, fh - 13.5f), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = argb(0.55f, 0x2A, 0x18, 0x0C)
        })
        c.drawRect(0f, 0f, fw, 6f, fill(lin(0f, 0f, 0f, 6f, intArrayOf(argb(0.45f, 255, 214, 170), argb(0f, 255, 214, 170)))))
        c.drawRect(0f, fh - 8f, fw, fh, fill(lin(0f, fh - 8f, 0f, fh, intArrayOf(argb(0f, 20, 10, 4), argb(0.5f, 20, 10, 4)))))
        c.restore()

        val lw = 172f
        val lh = 46f
        val lx = (fw - lw) / 2f
        val ly = 18f
        brass(c, lx, ly, lw, lh, 5f)
        val card = RectF(lx + 6f, ly + 6f, lx + lw - 6f, ly + lh - 6f)
        c.drawRoundRect(card, 2f, 2f, fill(lin(0f, ly + 6f, 0f, ly + lh - 6f, intArrayOf(hex(0xFFF4ECD8), hex(0xFFEADFC4), hex(0xFFDCCDAA)))))
        c.drawRoundRect(card, 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = argb(0.6f, 0x5C, 0x43, 0x20) })
        rivet(c, lx - 10f, ly + lh / 2f)
        rivet(c, lx + lw + 10f, ly + lh / 2f)
        val pullY = ly + lh + 12f
        val pull = Path().apply {
            moveTo(fw / 2f - 34f, pullY)
            cubicTo(fw / 2f - 30f, pullY + 22f, fw / 2f + 30f, pullY + 22f, fw / 2f + 34f, pullY)
            close()
        }
        c.save(); c.translate(0f, 3f); c.drawPath(pull, blurred(0.45f, 3f)); c.restore()
        c.drawPath(pull, fill(lin(fw / 2f - 34f, pullY, fw / 2f + 34f, pullY + 18f, intArrayOf(hex(0xFFF3D58C), hex(0xFFB98B3A), hex(0xFF7A5520), hex(0xFFC79B4B)), floatArrayOf(0f, 0.45f, 0.75f, 1f))))
        val kx = fw - 34f
        val ky = fh / 2f
        c.drawCircle(kx + 1f, ky + 3f, 9f, blurred(0.5f, 3f))
        c.drawCircle(kx, ky, 9f, fill(RadialGradient(kx - 3f, ky - 4f, 12f, intArrayOf(hex(0xFFFBE7B0), hex(0xFFC3933F), hex(0xFF6E4A1A)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)))
        c.drawCircle(kx, ky, 2.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0xFF3A2610) })
        return bmp
    }

    suspend fun bake(density: Float): CardCatalogArtSet {
        val s = min(2f, density)
        // Grain runs left to right: noise stretched sixty times along x (0.004 by 0.24 per dp in Skia's terms).
        val grain = tile(512, 128, max(1, (512 * 0.004f / s).roundToInt()), max(1, (128 * 0.24f / s).roundToInt()), 3, 0.32f)
        val fine = tile(128, 128, (128 * 0.9f / s).roundToInt(), (128 * 0.9f / s).roundToInt(), 10, 0.06f)
        currentCoroutineContext().ensureActive()
        val drawer = bakeDrawer(s, grain, fine)
        val front = bakeFront(s, grain, fine)
        return CardCatalogArtSet(s, drawer, front, grain, fine)
    }
}
