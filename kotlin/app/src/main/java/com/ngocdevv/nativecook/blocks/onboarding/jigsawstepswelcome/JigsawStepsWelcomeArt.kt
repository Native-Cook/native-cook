package com.ngocdevv.nativecook.blocks.onboarding.jigsawstepswelcome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/*
 Jigsaw Steps Welcome — layout, the twelve piece outlines, where each piece lies when scattered,
 and the static art (a port of jigsawGeometry.ts). The board is 4 × 3 cells over a 4:3 photo.
 Every inner edge carries one classic knob whose side is chosen by a seeded hash; the knob is four
 cubic Béziers over the edge. Paths are built in screen space at the piece's home, so drawing a
 piece elsewhere is only a transform. Baked once, off the main thread: the paper with the board's
 recessed tray and its printed cut lines, and per piece three soft silhouettes (the ambient shadow
 at σ 3 and σ 12, cross-faded by lift, and the contact shadow) so no blur runs per frame.
 */

internal const val JIGSAW_TOP_BAR = 44f
internal const val JIGSAW_TEXT_H = 104f
internal const val JIGSAW_CTA_H = 52f

private fun jigsawHash(n: Float): Float {
    val v = sin(n * 12.9898f + 4.1414f) * 43758.5453f
    return v - floor(v)
}

internal class JigsawLayout(val W: Float, val H: Float, top: Float, bottom: Float) {
    val board: RectF
    val cellW: Float
    val cellH: Float
    val home: Array<FloatArray>
    val scatter: Array<FloatArray>
    val textY: Float
    val ctaY: Float

    init {
        val bx = 20f
        val bw = W - 40f
        val bh = bw * 0.75f
        val by = top + 8f + JIGSAW_TOP_BAR + 12f
        ctaY = H - bottom - 16f - JIGSAW_CTA_H
        textY = ctaY - 20f - JIGSAW_TEXT_H
        cellW = bw / JigsawMotion.COLS
        cellH = bh / JigsawMotion.ROWS
        board = RectF(bx, by, bx + bw, by + bh)
        home = Array(JigsawMotion.COUNT) { i ->
            val r = i / JigsawMotion.COLS
            val c = i % JigsawMotion.COLS
            floatArrayOf(bx + (c + 0.5f) * cellW, by + (r + 0.5f) * cellH)
        }
        // The pile: a loose 4 × 3 grid under the board, shuffled so no piece waits under its own column.
        val zx0 = bx + cellW * 0.42f
        val zx1 = bx + bw - cellW * 0.42f
        val zy0 = by + bh + cellH * 0.18f
        val zy1 = max(zy0 + cellH * 0.6f, textY - cellH * 0.42f)
        val spot = intArrayOf(7, 2, 10, 0, 5, 11, 3, 8, 1, 9, 4, 6)
        scatter = Array(JigsawMotion.COUNT) { i ->
            val s = spot[i]
            val gc = s % 4
            val gr = s / 4
            floatArrayOf(
                zx0 + (zx1 - zx0) * gc / 3f + (jigsawHash(i + 1f) - 0.5f) * 18f,
                zy0 + (zy1 - zy0) * gr / 2f + (jigsawHash(i + 21f) - 0.5f) * 14f,
                (jigsawHash(i + 41f) - 0.5f) * 0.9f,
            )
        }
    }
}

// The knob along a unit edge: x along it, y outwards. Four cubics after the start point.
private val KNOB = arrayOf(
    floatArrayOf(0.3f, 0.0f, 0.42f, -0.04f, 0.39f, 0.08f),
    floatArrayOf(0.36f, 0.2f, 0.41f, 0.27f, 0.5f, 0.27f),
    floatArrayOf(0.59f, 0.27f, 0.64f, 0.2f, 0.61f, 0.08f),
    floatArrayOf(0.58f, -0.04f, 0.7f, 0.0f, 1.0f, 0.0f),
)

private fun edge(b: Path, ax: Float, ay: Float, bx: Float, by: Float, sign: Int, jitter: Float, back: Boolean) {
    if (sign == 0) { b.lineTo(bx, by); return }
    val dx = bx - ax
    val dy = by - ay
    val nx = dy
    val ny = -dx
    val shift = (jitter - 0.5f) * 0.08f * (if (back) -1f else 1f)
    fun px(u: Float, v: Float) = ax + dx * (u + if (u > 0.05f && u < 0.95f) shift else 0f) + nx * v * sign
    fun py(u: Float, v: Float) = ay + dy * (u + if (u > 0.05f && u < 0.95f) shift else 0f) + ny * v * sign
    for (k in KNOB) b.cubicTo(px(k[0], k[1]), py(k[0], k[1]), px(k[2], k[3]), py(k[2], k[3]), px(k[4], k[5]), py(k[4], k[5]))
}

/** The twelve outlines at their homes. */
internal fun jigsawPiecePaths(L: JigsawLayout): List<Path> {
    val rows = JigsawMotion.ROWS
    val cols = JigsawMotion.COLS
    val hE = Array(rows) { r -> IntArray(cols) { c -> if (jigsawHash(r * 7f + c * 3f + 1f) > 0.5f) 1 else -1 } }
    val vE = Array(rows) { r -> IntArray(cols) { c -> if (jigsawHash(r * 5f + c * 11f + 2f) > 0.5f) 1 else -1 } }
    val out = ArrayList<Path>()
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val x0 = L.board.left + c * L.cellW
            val y0 = L.board.top + r * L.cellH
            val x1 = x0 + L.cellW
            val y1 = y0 + L.cellH
            val top = if (r == 0) 0 else -hE[r - 1][c]
            val right = if (c == cols - 1) 0 else vE[r][c]
            val bottom = if (r == rows - 1) 0 else hE[r][c]
            val left = if (c == 0) 0 else -vE[r][c - 1]
            val b = Path()
            b.moveTo(x0, y0)
            edge(b, x0, y0, x1, y0, top, jigsawHash(r * 13f + c), false)
            edge(b, x1, y0, x1, y1, right, jigsawHash(r * 17f + c + 5f), false)
            edge(b, x1, y1, x0, y1, bottom, jigsawHash(r * 13f + c + 13f), true)
            edge(b, x0, y1, x0, y0, left, jigsawHash(r * 17f + c + 4f), true)
            b.close()
            out.add(b)
        }
    }
    return out
}

internal fun jigsawArgb(r: Int, g: Int, b: Int, a: Float = 1f) =
    ((a * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

internal fun jigsawHex(hex: Long) = (0xFF000000 or hex).toInt()

/** A piece's silhouette, blurred, in its own box (home space offset by `x`, `y`), at 1 px per dp. */
internal class JigsawSoft(val bitmap: Bitmap, val x: Float, val y: Float)

private fun soft(path: Path, sigma: Float, color: Int): JigsawSoft {
    val bounds = RectF()
    path.computeBounds(bounds, true)
    val pad = sigma * 3f + 2f
    val k = 1.5f
    val bmp = Bitmap.createBitmap(ceil((bounds.width() + pad * 2) * k).toInt(), ceil((bounds.height() + pad * 2) * k).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(k, k)
    c.translate(pad - bounds.left, pad - bounds.top)
    c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) })
    return JigsawSoft(bmp, bounds.left - pad, bounds.top - pad)
}

internal class JigsawArt(
    val photo: Bitmap,
    val paper: Bitmap,
    val soft3: List<JigsawSoft>,
    val soft12: List<JigsawSoft>,
    val contact: List<JigsawSoft>,
    /** For hit tests: each outline at 4 × its size. */
    val regions: List<Region>,
)

internal fun jigsawBake(context: Context, L: JigsawLayout, paths: List<Path>, scale: Float): JigsawArt {
    val photo = context.assets.open("jigsawstepswelcome/jigsawstepswelcome_beach.jpg").use { s ->
        BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })!!
    }
    val regions = paths.map { p ->
        val big = Path(p).apply { transform(Matrix().apply { setScale(4f, 4f) }) }
        Region().apply { setPath(big, Region(-10, -10, (L.W * 4).toInt() + 10, (L.H * 4).toInt() + 10)) }
    }
    return JigsawArt(
        photo = photo,
        paper = bakePaper(L, paths, scale),
        soft3 = paths.map { soft(it, 3f, jigsawHex(0x3B2614)) },
        soft12 = paths.map { soft(it, 12f, jigsawHex(0x3B2614)) },
        contact = paths.map { soft(it, 1.4f, jigsawHex(0x2A1A0E)) },
        regions = regions,
    )
}

/** Cream paper, grain and the board's recessed tray with the faint cut lines printed in it. */
private fun bakePaper(L: JigsawLayout, outlines: List<Path>, scale: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(L.W * scale).toInt(), ceil(L.H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val W = L.W
    val H = L.H
    val B = L.board
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, W * 0.4f, H, intArrayOf(jigsawHex(0xF7F1E7), jigsawHex(0xF1E8DA), jigsawHex(0xE9DDCB)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, p)
    p.shader = RadialGradient(W * 0.15f, H * 0.05f, W * 1.1f, intArrayOf(jigsawArgb(255, 214, 170, 0.35f), jigsawArgb(255, 214, 170, 0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, p)
    val tray = RectF(B.left - 6f, B.top - 6f, B.right + 6f, B.bottom + 6f)
    p.shader = LinearGradient(0f, B.top, 0f, B.bottom, intArrayOf(jigsawHex(0xE2D5C1), jigsawHex(0xDCCDB6), jigsawHex(0xD6C6AD)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(tray, 16f, 16f, p)
    p.shader = null
    // Recess: a dark inner edge at the top, a light lip at the bottom.
    c.save()
    c.clipPath(Path().apply { addRoundRect(tray, 16f, 16f, Path.Direction.CW) })
    c.drawRoundRect(B.left - 6f, B.top - 9f, B.right + 6f, B.bottom + 3f, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = jigsawArgb(90, 64, 40, 0.22f)
        maskFilter = BlurMaskFilter(sigmaToRadius(6f), BlurMaskFilter.Blur.NORMAL)
    })
    val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = jigsawArgb(110, 82, 56, 0.24f) }
    for (o in outlines) c.drawPath(o, cut)
    c.restore()
    c.drawRoundRect(B.left - 6.5f, B.top - 5.5f, B.right + 6.5f, B.bottom + 7f, 16.5f, 16.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = jigsawArgb(255, 252, 246, 0.9f) })
    val n = 160
    val rnd = Random(9)
    val px = IntArray(n * n) {
        // A darkening tile (black, alpha 1 − v) drawn SRC_ATOP: an exact multiply by the grey grain that
        // keeps the layer's alpha (PorterDuff MULTIPLY multiplies alpha too and washed the board out).
        val v = (rnd.nextFloat() * 255f).toInt()
        (255 - v) shl 24
    }
    c.drawRect(0f, 0f, W, H, Paint(Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        alpha = (0.07f * 255).toInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    })
    return bmp
}
