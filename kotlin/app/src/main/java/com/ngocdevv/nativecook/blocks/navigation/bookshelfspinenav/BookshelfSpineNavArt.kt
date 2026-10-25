package com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.BOARD_Y
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.DESIGN_W
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Static art, baked once off the main thread at [scale] px per dp. */
internal class BookshelfArtSet(
    val scale: Float,
    /** The shelf (back panel, shelf above, board, uprights) over SHELF_BOX in design dp. */
    val shelf: Bitmap,
    /** A soft black square (blurred, SOFT_MARGIN around SOFT_SIZE), stretched under the pulled cover. */
    val soft: Bitmap,
    /** A wrapping fine-grain tile for the wall. */
    val grain: Bitmap,
)

internal object BookshelfSpineNavArt {
    const val SHELF_TOP = 462f
    const val BOARD_H = 30f
    const val SHELF_BOX_X = -16f
    const val SHELF_BOX_Y = SHELF_TOP - 34f
    const val SHELF_BOX_W = DESIGN_W + 32f
    const val SHELF_BOX_H = BOARD_Y + BOARD_H + 34f - SHELF_BOX_Y
    const val SOFT_SIZE = 100f
    const val SOFT_MARGIN = 40f

    private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
    private fun hex(c: Long) = c.toInt()
    private fun sigma(s: Float) = max(0.01f, (s - 0.5f) / 0.57735f)
    private fun lin(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray? = null) =
        LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP)
    private fun fill(shader: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    private fun blurred(alpha: Float, sigmaDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb(alpha, 0, 0, 0)
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

    private fun tiled(bmp: Bitmap, s: Float) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1f / s, 1f / s) })
        }
    }

    /** The shelf's back panel, the shelf above, the board the books stand on and the uprights. */
    private suspend fun bakeShelf(s: Float): Bitmap {
        val bmp = Bitmap.createBitmap(ceil(SHELF_BOX_W * s).toInt(), ceil(SHELF_BOX_H * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        c.translate(-SHELF_BOX_X, -SHELF_BOX_Y)
        // Wood grain runs along the boards: long in x, fine in y.
        val wood = tile(512, 128, max(1, (512 * 0.005f / s).roundToInt()), max(1, (128 * 0.25f / s).roundToInt()), 9, 0.35f)
        currentCoroutineContext().ensureActive()
        val left = 4f
        val right = DESIGN_W - 4f
        c.drawRect(left - 6f, SHELF_TOP - 4f, right + 6f, BOARD_Y + BOARD_H + 18f, blurred(0.55f, 12f))
        val panel = RectF(left, SHELF_TOP, right, BOARD_Y)
        c.drawRect(panel, fill(lin(0f, SHELF_TOP, 0f, BOARD_Y, intArrayOf(hex(0xFF140D08), hex(0xFF24170E), hex(0xFF2D1D12)), floatArrayOf(0f, 0.45f, 1f))))
        c.drawRect(panel, tiled(wood, s))
        c.drawRect(panel, fill(RadialGradient(DESIGN_W * 0.55f, BOARD_Y - 20f, 260f, argb(0.12f, 255, 190, 120), argb(0f, 255, 190, 120), Shader.TileMode.CLAMP)))
        c.drawRect(
            left, SHELF_TOP, right, SHELF_TOP + 70f,
            fill(lin(0f, SHELF_TOP, 0f, SHELF_TOP + 70f, intArrayOf(argb(0.75f, 0, 0, 0), argb(0.3f, 0, 0, 0), argb(0f, 0, 0, 0)))),
        )
        val above = RectF(left - 4f, SHELF_TOP - 16f, right + 4f, SHELF_TOP)
        c.drawRect(above, fill(lin(0f, SHELF_TOP - 16f, 0f, SHELF_TOP, intArrayOf(hex(0xFF6B4A2F), hex(0xFF553823), hex(0xFF3A2617)))))
        c.drawRect(above, tiled(wood, s))
        c.drawRect(left - 4f, SHELF_TOP - 16f, right + 4f, SHELF_TOP - 15f, Paint().apply { color = argb(0.35f, 0xE2, 0xB1, 0x7E) })

        c.drawRect(left - 8f, BOARD_Y + 6f, right + 8f, BOARD_Y + 6f + BOARD_H + 4f, blurred(0.6f, 8f))
        val board = RectF(left - 8f, BOARD_Y, right + 8f, BOARD_Y + BOARD_H)
        c.drawRect(
            board,
            fill(lin(0f, BOARD_Y, 0f, BOARD_Y + BOARD_H, intArrayOf(hex(0xFF9A6C45), hex(0xFF7A5233), hex(0xFF5A3B23), hex(0xFF40291A)), floatArrayOf(0f, 0.18f, 0.7f, 1f))),
        )
        c.drawRect(board, tiled(wood, s))
        c.drawRect(left - 8f, BOARD_Y + 5f, right + 8f, BOARD_Y + 6f, Paint().apply { color = argb(0.45f, 0xF2, 0xC7, 0x96) })
        c.drawRect(left - 8f, BOARD_Y, right + 8f, BOARD_Y + 5f, fill(lin(0f, BOARD_Y, 0f, BOARD_Y + 5f, intArrayOf(hex(0xFFC08A5A), hex(0xFF8E6240)))))
        c.drawRect(
            left, BOARD_Y - 2f, right, BOARD_Y + 4f,
            fill(lin(0f, BOARD_Y - 2f, 0f, BOARD_Y + 4f, intArrayOf(argb(0f, 10, 5, 2), argb(0.55f, 10, 5, 2), argb(0f, 10, 5, 2)))),
        )
        for (x in floatArrayOf(left - 10f, right)) {
            c.drawRect(
                x, SHELF_TOP - 16f, x + 10f, BOARD_Y + BOARD_H,
                fill(lin(x, 0f, x + 10f, 0f, intArrayOf(hex(0xFF4A3220), hex(0xFF6E4B30), hex(0xFF3A2717)))),
            )
        }
        c.drawRect(left - 8f, BOARD_Y + BOARD_H - 1f, right + 8f, BOARD_Y + BOARD_H, Paint().apply { color = argb(0.4f, 0, 0, 0) })
        return bmp
    }

    private fun bakeSoft(s: Float): Bitmap {
        val total = SOFT_SIZE + SOFT_MARGIN * 2
        val bmp = Bitmap.createBitmap(ceil(total * s).toInt(), ceil(total * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        c.drawRoundRect(RectF(SOFT_MARGIN, SOFT_MARGIN, SOFT_MARGIN + SOFT_SIZE, SOFT_MARGIN + SOFT_SIZE), 4f, 4f, blurred(1f, 12f))
        return bmp
    }

    suspend fun bake(density: Float): BookshelfArtSet {
        val s = min(2f, density)
        val shelf = bakeShelf(s)
        val soft = bakeSoft(s)
        val grain = tile(160, 160, (160 * 0.8f / s).roundToInt(), (160 * 0.8f / s).roundToInt(), 4, 0.1f)
        return BookshelfArtSet(s, shelf, soft, grain)
    }
}
