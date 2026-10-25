package com.ngocdevv.nativecook.blocks.inputs.chalkboardsearch

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Chalkboard Search — the board's artwork and the chalk (a port of chalkboardSearchArt.ts).

 The slate (dark green with old eraser swirls, grain and a vignette), the oak frame and ledge,
 the café's name and "What's cooking?" in chalk, the magnifier, the search line's underline and a
 small doodle are drawn once into a bitmap, off the main thread. Chalk is a glyph outline filled
 with the chalk texture: an off-white whose coverage breaks up with two noises (one stretched along
 the stroke, like chalk dragged over slate) and a fine grain. The texture is CHALK_SKSL evaluated
 once on the CPU into a tile (it works the same on every Android version, and costs nothing per
 frame); a seed shifts the tile. The slate is SLATE_SKSL evaluated on the CPU the same way.
*/

// ---- The noises of the two shaders, in doubles ---------------------------------------------------

private fun fract(x: Double) = x - floor(x)
/**
 * A lattice hash in [0, 1). Every caller passes whole numbers (lattice corners and grain cells), so
 * an integer hash stands in for the shaders' sin-hash: same character, without millions of `sin`
 * calls, which made the board take seconds to bake on a device.
 */
private fun hash21(x: Double, y: Double): Double {
    var h = x.toInt() * 374761393 + y.toInt() * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216.0
}
private fun sstep(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}
private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
private fun vnoise(x: Double, y: Double): Double {
    val ix = floor(x)
    val iy = floor(y)
    var fx = x - ix
    var fy = y - iy
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    return mix(mix(hash21(ix, iy), hash21(ix + 1, iy), fx), mix(hash21(ix, iy + 1), hash21(ix + 1, iy + 1), fx), fy)
}
private fun fbm(x0: Double, y0: Double): Double {
    var s = 0.0
    var a = 0.5
    var x = x0
    var y = y0
    for (i in 0 until 3) { s += a * vnoise(x, y); x = x * 2.03 + 7.1; y = y * 2.03 + 7.1; a *= 0.5 }
    return s
}

internal fun chalkHash(i: Float): Float {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** The chalk texture: CHALK_SKSL at seed 0 over TILE dp, TILE_PX pixels per dp. */
private const val TILE = 256
private const val TILE_PX = 2

internal class ChalkTexture(val tile: Bitmap) {
    /** A fill paint of chalk at `alpha`, its noise shifted by `seed`. */
    fun paint(seed: Float, alpha: Float = 1f, stroke: Float = 0f): Paint {
        val sh = BitmapShader(tile, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        val m = Matrix()
        m.setScale(1f / TILE_PX, 1f / TILE_PX)
        m.postTranslate(chalkHash(seed * 3.1f) * TILE, chalkHash(seed * 5.7f + 1f) * TILE)
        sh.setLocalMatrix(m)
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = sh
            this.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            if (stroke > 0f) {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        }
    }
}

internal fun chalkMakeTexture(): ChalkTexture {
    val n = TILE * TILE_PX
    val px = IntArray(n * n)
    for (y in 0 until n) {
        for (x in 0 until n) {
            val p0 = (x + 0.5) / TILE_PX
            val p1 = (y + 0.5) / TILE_PX
            val n1 = vnoise(p0 * 0.5, p1 * 0.85)
            val n2 = vnoise(p0 * 2.2, p1 * 0.6)
            val g = hash21(floor(p0 * 2.0), floor(p1 * 2.0))
            var a = sstep(0.2, 0.66, n1 * 0.5 + n2 * 0.32 + g * 0.34)
            a = 0.3 + 0.7 * a
            val ai = (a * 255 + 0.5).toInt().coerceIn(0, 255)
            px[y * n + x] = (ai shl 24) or (241 shl 16) or (240 shl 8) or 231
        }
    }
    return ChalkTexture(Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888))
}

// ---- Layout ----------------------------------------------------------------------------------------

internal const val CHALK_FRAME = 14f

internal class ChalkGeo(
    val W: Float, val H: Float,
    val boardX: Float, val boardY: Float, val boardW: Float, val boardH: Float,
    val slateX: Float, val slateY: Float, val slateW: Float, val slateH: Float,
    val qx: Float, val qy: Float, val sx: Float, val sy: FloatArray, val doodle: Boolean,
)

internal fun chalkMakeGeo(W: Float, H: Float, insetTop: Float): ChalkGeo {
    val boardX = 12f
    val boardY = insetTop + 4f
    val boardW = W - 24f
    val boardH = max(400f, min(520f, (H * 0.58f).roundToInt().toFloat()))
    val slateX = boardX + CHALK_FRAME
    val slateY = boardY + CHALK_FRAME
    val slateW = boardW - CHALK_FRAME * 2
    val slateH = boardH - CHALK_FRAME * 2 - 10f
    return ChalkGeo(
        W, H, boardX, boardY, boardW, boardH, slateX, slateY, slateW, slateH,
        qx = slateX + 52f, qy = slateY + 146f, sx = slateX + 36f,
        sy = floatArrayOf(slateY + 212f, slateY + 256f, slateY + 300f),
        doodle = boardH >= 470f,
    )
}

/** The board's hand: Chalkboard SE on iOS, the platform's casual face here. */
internal val ChalkFace: Typeface = Typeface.create("casual", Typeface.NORMAL)
internal val ChalkFaceBold: Typeface = Typeface.create("casual", Typeface.BOLD)

internal fun chalkFont(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = if (bold) ChalkFaceBold else ChalkFace
    textSize = size
}

internal class ChalkLine(val path: Path, val lefts: FloatArray, val rights: FloatArray, val end: Float)

/** A line of text as one chalk outline, each glyph nudged off the baseline and tilted a little. */
internal fun chalkLine(text: String, font: Paint, x: Float, baseline: Float, seed: Int): ChalkLine {
    val path = Path()
    val widths = FloatArray(text.length)
    font.getTextWidths(text, widths)
    val lefts = FloatArray(text.length)
    val rights = FloatArray(text.length)
    val glyph = Path()
    val m = Matrix()
    var pen = x
    for (i in text.indices) {
        val w = widths[i]
        lefts[i] = pen
        rights[i] = pen + w
        if (text[i] != ' ') {
            glyph.rewind()
            font.getTextPath(text, i, i + 1, 0f, 0f, glyph)
            val dy = (chalkHash(seed * 13 + i * 7.1f) - 0.5f) * 2.4f
            val rot = (chalkHash(seed * 7 + i * 3.3f) - 0.5f) * 6f
            m.setTranslate(-w / 2, 0f)
            m.postRotate(rot)
            m.postTranslate(pen + w / 2, baseline + dy)
            path.addPath(glyph, m)
        }
        pen += w * 1.02f
    }
    return ChalkLine(path, lefts, rights, pen)
}

private fun sigmaRadius(sigma: Float) = max(0.5f, (sigma - 0.5f) / 0.57735f)

/** Fills a path with chalk: a soft haze, the chalk itself, and a rough second pass along the edge. */
internal fun chalkDraw(c: Canvas, path: Path, tex: ChalkTexture, seed: Float, alpha: Float = 1f) {
    val haze = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEF0E8.toInt()
        this.alpha = (0.07f * alpha * 255).roundToInt()
        maskFilter = BlurMaskFilter(sigmaRadius(3f), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawPath(path, haze)
    c.drawPath(path, tex.paint(seed, alpha))
    c.save()
    c.translate(0.6f, 0.5f)
    c.drawPath(path, tex.paint(seed + 5f, 0.45f * alpha, stroke = 0.9f))
    c.restore()
}

/** A hand-drawn chalk stroke (for the underline, icons and the doodle). */
private fun chalkStroke(c: Canvas, path: Path, tex: ChalkTexture, width: Float, seed: Float) {
    for (k in 0 until 2) {
        c.save()
        c.translate(k * 0.7f, k * -0.5f)
        c.drawPath(path, tex.paint(seed + k * 9, if (k == 0) 0.85f else 0.5f, stroke = width * (if (k == 0) 1f else 0.6f)))
        c.restore()
    }
}

private fun paint(color: Int, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    this.alpha = (alpha * 255).roundToInt()
}

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP)
}

/**
 * SLATE_SKSL on the CPU, over the slate's own w × h dp at `px` pixels per dp. The smudges, swirls
 * and vignette change over tens of dp, so they are evaluated on a grid every GRID dp and
 * interpolated; only the fine grain is evaluated per pixel. `check` is called once per row so a
 * cancelled bake stops at once instead of holding a worker thread.
 */
private const val GRID = 2.0

private fun slate(w: Float, h: Float, px: Float, check: () -> Unit): Bitmap {
    val bw = max(1, ceil(w * px).toInt())
    val bh = max(1, ceil(h * px).toInt())
    val out = IntArray(bw * bh)
    val rx = w.toDouble()
    val ry = h.toDouble()
    // Low-frequency terms on the grid: k (smudge + swirl) and v (vignette).
    val gw = ceil(rx / GRID).toInt() + 2
    val gh = ceil(ry / GRID).toInt() + 2
    val kg = DoubleArray(gw * gh)
    val vg = DoubleArray(gw * gh)
    for (j in 0 until gh) {
        check()
        for (i in 0 until gw) {
            val p0 = i * GRID
            val p1 = j * GRID
            val wv = fbm(p0 * 0.008, p1 * 0.008)
            val ax = p0 - rx * 0.3
            val ay = p1 - ry * 0.7
            val arc = sin(sqrt(ax * ax + ay * ay) * 0.045 + wv * 5.0)
            val swirl = sstep(0.55, 1.0, arc) * sstep(0.35, 0.75, fbm(p0 * 0.004 + 3.0, p1 * 0.004 + 3.0))
            val smudge = sstep(0.42, 0.85, wv)
            kg[j * gw + i] = smudge * 0.32 + swirl * 0.22
            val ux = (p0 / rx - 0.5) * 1.1
            val uy = (p1 / ry - 0.5)
            vg[j * gw + i] = mix(0.62, 1.0, sstep(1.0, 0.25, sqrt(ux * ux + uy * uy)))
        }
    }
    // The grain's hash per 1.5-per-dp cell, cached for the current row of cells.
    var cellY = Int.MIN_VALUE
    val cells = DoubleArray(floor(rx * 1.5).toInt() + 2)
    for (y in 0 until bh) {
        check()
        val p1 = (y + 0.5) / px
        val gy = p1 / GRID
        val j0 = min(gh - 2, floor(gy).toInt())
        val fy = gy - j0
        val cy = floor(p1 * 1.5).toInt()
        if (cy != cellY) {
            cellY = cy
            for (cx in cells.indices) cells[cx] = hash21(cx.toDouble(), cy.toDouble())
        }
        for (x in 0 until bw) {
            val p0 = (x + 0.5) / px
            val gx = p0 / GRID
            val i0 = min(gw - 2, floor(gx).toInt())
            val fx = gx - i0
            val a = j0 * gw + i0
            val k = mix(mix(kg[a], kg[a + 1], fx), mix(kg[a + gw], kg[a + gw + 1], fx), fy)
            val v = mix(mix(vg[a], vg[a + 1], fx), mix(vg[a + gw], vg[a + gw + 1], fx), fy)
            val cx = min(cells.size - 1, floor(p0 * 1.5).toInt())
            val gr = 1.0 + (cells[cx] - 0.5) * 0.07
            val r = mix(0.118, 0.22, k) * gr * v
            val g = mix(0.169, 0.27, k) * gr * v
            val b = mix(0.145, 0.245, k) * gr * v
            out[y * bw + x] = (0xFF shl 24) or ((r.coerceIn(0.0, 1.0) * 255 + 0.5).toInt() shl 16) or
                ((g.coerceIn(0.0, 1.0) * 255 + 0.5).toInt() shl 8) or (b.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
        }
    }
    return Bitmap.createBitmap(out, bw, bh, Bitmap.Config.ARGB_8888)
}

/** The board rendered once at `px` pixels per dp, covering W × (boardY + boardH + 24) dp. */
internal fun chalkBakeBoard(g: ChalkGeo, tex: ChalkTexture, px: Float, check: () -> Unit = {}): Bitmap {
    val w = ceil(g.W * px).toInt()
    val h = ceil((g.boardY + g.boardH + 24f) * px).toInt()
    val bmp = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(px, px)

    // Frame: a shadow on the wall, then oak with grain.
    val outer = RectF(g.boardX, g.boardY, g.boardX + g.boardW, g.boardY + g.boardH)
    val amb = paint(0xFF000000.toInt(), 0.45f).apply { maskFilter = BlurMaskFilter(sigmaRadius(18f), BlurMaskFilter.Blur.NORMAL) }
    c.save(); c.translate(0f, 14f); c.drawRoundRect(outer, 10f, 10f, amb); c.restore()
    c.drawRoundRect(
        outer, 10f, 10f,
        linear(g.boardX, 0f, g.boardX + g.boardW, g.boardH, intArrayOf(0xFF7A4C2A.toInt(), 0xFFA8723F.toInt(), 0xFF8A5A33.toInt(), 0xFF5E3A1F.toInt()), floatArrayOf(0f, 0.3f, 0.7f, 1f)),
    )
    val grain = paint(0xFF2F1B0C.toInt(), 0.18f).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f }
    val gp = Path()
    for (i in 0 until 40) {
        val y = g.boardY + chalkHash(i * 2.7f) * g.boardH
        gp.moveTo(g.boardX, y)
        gp.cubicTo(g.boardX + g.boardW * 0.3f, y + (chalkHash(i + 3f) - 0.5f) * 8f, g.boardX + g.boardW * 0.7f, y + (chalkHash(i + 5f) - 0.5f) * 8f, g.boardX + g.boardW, y)
    }
    c.save(); c.clipRect(outer); c.drawPath(gp, grain); c.restore()
    c.drawRoundRect(RectF(g.boardX + 1f, g.boardY + 1f, g.boardX + g.boardW - 1f, g.boardY + 2.2f), 1f, 1f, paint(0xFFFFE2B8.toInt(), 0.35f))

    // Slate, inset into the frame.
    val slateRect = RectF(g.slateX, g.slateY, g.slateX + g.slateW, g.slateY + g.slateH)
    val sb = slate(g.slateW, g.slateH, 1f, check)
    check()
    c.save()
    val clip = Path().apply { addRoundRect(slateRect, 4f, 4f, Path.Direction.CW) }
    c.clipPath(clip)
    c.drawBitmap(sb, null, slateRect, Paint(Paint.FILTER_BITMAP_FLAG))
    val inner = paint(0xFF000000.toInt(), 0.55f).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        maskFilter = BlurMaskFilter(sigmaRadius(3f), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawRect(slateRect, inner)
    c.restore()

    // Ledge with a stick of chalk.
    val ly = g.slateY + g.slateH
    c.drawRect(RectF(g.slateX - 4f, ly, g.slateX + g.slateW + 4f, ly + 12f), linear(0f, ly, 0f, ly + 12f, intArrayOf(0xFF5A3519.toInt(), 0xFFB37B45.toInt(), 0xFF6B4325.toInt())))
    val stickX = g.slateX + g.slateW * 0.66f
    c.drawRoundRect(RectF(stickX, ly - 3f, stickX + 34f, ly + 3f), 3f, 3f, linear(0f, ly - 3f, 0f, ly + 3f, intArrayOf(0xFFFBFAF4.toInt(), 0xFFE2E0D6.toInt(), 0xFFBDBAB0.toInt())))

    // Chalk copy.
    chalkDraw(c, chalkLine("Corner Café", chalkFont(16f), g.slateX + 24f, g.slateY + 38f, 2).path, tex, 3f, 0.85f)
    chalkDraw(c, chalkLine("What's cooking?", chalkFont(32f, bold = true), g.slateX + 24f, g.slateY + 84f, 4).path, tex, 5f)

    // Magnifier and the search line.
    val mx = g.slateX + 34f
    val my = g.qy - 10f
    val glass = Path().apply {
        addCircle(mx, my, 8.5f, Path.Direction.CW)
        moveTo(mx + 6.2f, my + 6.2f)
        lineTo(mx + 13f, my + 13f)
    }
    chalkStroke(c, glass, tex, 2.4f, 11f)
    val under = Path().apply {
        moveTo(g.slateX + 22f, g.qy + 14f)
        cubicTo(g.slateX + g.slateW * 0.35f, g.qy + 12f, g.slateX + g.slateW * 0.65f, g.qy + 17f, g.slateX + g.slateW - 22f, g.qy + 13f)
    }
    chalkStroke(c, under, tex, 1.6f, 13f)
    chalkDraw(c, chalkLine("Today: rhubarb crumble · \$6", chalkFont(14f), g.slateX + 24f, g.slateY + g.slateH - 22f, 6).path, tex, 7f, 0.7f)

    if (g.doodle) {
        // A cup of coffee with steam, bottom right.
        val dx = g.slateX + g.slateW - 76f
        val dy = g.slateY + g.slateH - 82f
        val cup = Path().apply {
            moveTo(dx, dy)
            lineTo(dx + 4f, dy + 30f)
            cubicTo(dx + 6f, dy + 36f, dx + 30f, dy + 36f, dx + 32f, dy + 30f)
            lineTo(dx + 36f, dy)
            close()
            moveTo(dx + 35f, dy + 7f)
            cubicTo(dx + 48f, dy + 6f, dx + 48f, dy + 22f, dx + 33f, dy + 22f)
            moveTo(dx - 8f, dy + 40f)
            cubicTo(dx + 8f, dy + 44f, dx + 30f, dy + 44f, dx + 46f, dy + 40f)
            for (k in 0 until 3) {
                val sxk = dx + 9f + k * 9f
                moveTo(sxk, dy - 6f)
                cubicTo(sxk - 5f, dy - 12f, sxk + 5f, dy - 18f, sxk, dy - 26f)
            }
        }
        chalkStroke(c, cup, tex, 2f, 17f)
    }
    bmp.prepareToDraw()
    return bmp
}

/**
 * The board without its textures, drawn straight onto the screen until the baked one arrives: the
 * oak frame, a flat slate, the ledge and the header in plain chalk. All vector, no bake.
 */
internal class ChalkPlainBoard(g: ChalkGeo) {
    private val outer = RectF(g.boardX, g.boardY, g.boardX + g.boardW, g.boardY + g.boardH)
    private val oak = linear(g.boardX, 0f, g.boardX + g.boardW, g.boardH, intArrayOf(0xFF7A4C2A.toInt(), 0xFFA8723F.toInt(), 0xFF8A5A33.toInt(), 0xFF5E3A1F.toInt()), floatArrayOf(0f, 0.3f, 0.7f, 1f))
    private val slateRect = RectF(g.slateX, g.slateY, g.slateX + g.slateW, g.slateY + g.slateH)
    private val slatePaint = paint(0xFF1B2722.toInt())
    private val ly = g.slateY + g.slateH
    private val ledgeRect = RectF(g.slateX - 4f, ly, g.slateX + g.slateW + 4f, ly + 12f)
    private val ledge = linear(0f, ly, 0f, ly + 12f, intArrayOf(0xFF5A3519.toInt(), 0xFFB37B45.toInt(), 0xFF6B4325.toInt()))
    private val header = Path().apply {
        addPath(chalkLine("Corner Café", chalkFont(16f), g.slateX + 24f, g.slateY + 38f, 2).path)
        addPath(chalkLine("What's cooking?", chalkFont(32f, bold = true), g.slateX + 24f, g.slateY + 84f, 4).path)
    }
    private val chalk = paint(0xFFF1F0E7.toInt(), 0.8f)

    fun draw(c: Canvas, alpha: Float) {
        val a = (alpha * 255).roundToInt().coerceIn(0, 255)
        if (a == 0) return
        if (a < 255) c.saveLayerAlpha(null, a)
        c.drawRoundRect(outer, 10f, 10f, oak)
        c.drawRoundRect(slateRect, 4f, 4f, slatePaint)
        c.drawRect(ledgeRect, ledge)
        c.drawPath(header, chalk)
        if (a < 255) c.restore()
    }
}

/** The felt eraser, centred on (0, 0), pre-rendered with its shadow. */
internal class ChalkSprite(val bitmap: Bitmap, val rect: RectF)

internal fun chalkMakeEraser(px: Float): ChalkSprite {
    val rect = RectF(-44f, -64f, 56f, 74f)
    val bw = ceil(rect.width() * px).toInt()
    val bh = ceil(rect.height() * px).toInt()
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(px, px)
    c.translate(-rect.left, -rect.top)
    val sh = paint(0xFF000000.toInt(), 0.35f).apply { maskFilter = BlurMaskFilter(sigmaRadius(8f), BlurMaskFilter.Blur.NORMAL) }
    c.drawRoundRect(RectF(-6f, -22f, 28f, 48f), 7f, 7f, sh)
    c.drawRoundRect(RectF(-15f, -32f, 15f, 32f), 6f, 6f, paint(0xFFD9D3C5.toInt()))
    c.drawRoundRect(RectF(-11f, -32f, 11f, 32f), 5f, 5f, paint(0xFF8E5C33.toInt()))
    c.drawRoundRect(RectF(-9f, -30f, -5f, 30f), 2f, 2f, paint(0x73FFE2B8))
    c.drawRoundRect(RectF(5f, -30f, 8f, 30f), 1.5f, 1.5f, paint(0x59281408))
    bmp.prepareToDraw()
    return ChalkSprite(bmp, RectF(rect.left, rect.top, rect.left + bw / px, rect.top + bh / px))
}

/** The live chalk lines (query and menu lines, or the placeholder) rendered into one bitmap over the search area. */
internal fun chalkRenderLines(g: ChalkGeo, tex: ChalkTexture, px: Float, lines: List<Pair<Path, Float>>): ChalkSprite {
    val rect = RectF(g.slateX - 10f, g.qy - 52f, g.slateX + g.slateW + 10f, g.sy[2] + 22f)
    val bw = ceil(rect.width() * px).toInt()
    val bh = ceil(rect.height() * px).toInt()
    val bmp = Bitmap.createBitmap(max(1, bw), max(1, bh), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(px, px)
    c.translate(-rect.left, -rect.top)
    for ((path, seed) in lines) chalkDraw(c, path, tex, seed)
    bmp.prepareToDraw()
    return ChalkSprite(bmp, RectF(rect.left, rect.top, rect.left + bw / px, rect.top + bh / px))
}
