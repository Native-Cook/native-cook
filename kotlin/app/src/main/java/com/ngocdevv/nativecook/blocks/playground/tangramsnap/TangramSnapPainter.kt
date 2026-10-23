package com.ngocdevv.nativecook.blocks.playground.tangramsnap

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Bevel width (dp), the gap between pieces (dp) and how much a lifted piece grows.
private const val BEVEL = 3.4
private const val GAP = 0.9
private const val LIFT_GROW = 0.05

internal const val TANGRAM_INK = 0xFFF2E9DC.toInt()
internal const val TANGRAM_MUTED = 0xFFA29483.toInt()
internal val TangramBoard = intArrayOf(0xFF2A231D.toInt(), 0xFF171310.toInt(), 0xFF0C0A08.toInt())

/** Lacquer colours, in piece order. */
private val LACQUER = arrayOf(
    intArrayOf(196, 64, 42),
    intArrayOf(44, 62, 116),
    intArrayOf(210, 154, 44),
    intArrayOf(46, 126, 98),
    intArrayOf(122, 50, 88),
    intArrayOf(42, 136, 152),
    intArrayOf(222, 200, 160),
)
private const val LIGHT_X = -0.6
private const val LIGHT_Y = -0.8

// Lacquered wood: grain rings from value noise, darkening the tint. The React Native SkSL, unchanged:
// AGSL is the same dialect (API 33+). Each piece's shader carries its own local matrix.
private const val LACQUER_AGSL = """
uniform float3 tint;
uniform float seed;
uniform float grain;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 p) {
  float2 q = p * float2(0.035, 0.008) + float2(seed, seed * 0.37);
  float n = noise(q * float2(3.0, 0.6)) * 0.65 + noise(q * float2(9.0, 1.4)) * 0.35;
  float rings = fract(q.x * 2.6 + n * 1.9);
  float g = smoothstep(0.0, 0.75, rings) * (1.0 - smoothstep(0.75, 1.0, rings));
  float shade = mix(1.0, 0.9 + 0.14 * g + 0.08 * (n - 0.5), grain);
  return half4(half3(tint * shade), 1.0);
}"""

// The same grain on the CPU, for the fallback below Android 13.
private fun fract(v: Double) = v - floor(v)
private fun hash(x: Double, y: Double) = fract(sin(x * 127.1 + y * 311.7) * 43758.5453)
private fun mixD(a: Double, b: Double, t: Double) = a + (b - a) * t
private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}
private fun noise(px: Double, py: Double): Double {
    val ix = floor(px)
    val iy = floor(py)
    val fx = px - ix
    val fy = py - iy
    val ux = fx * fx * (3 - 2 * fx)
    val uy = fy * fy * (3 - 2 * fy)
    return mixD(mixD(hash(ix, iy), hash(ix + 1, iy), ux), mixD(hash(ix, iy + 1), hash(ix + 1, iy + 1), ux), uy)
}
private fun grainShade(px: Double, py: Double, seed: Double): Double {
    val qx = px * 0.035 + seed
    val qy = py * 0.008 + seed * 0.37
    val n = noise(qx * 3.0, qy * 0.6) * 0.65 + noise(qx * 9.0, qy * 1.4) * 0.35
    val rings = fract(qx * 2.6 + n * 1.9)
    val g = smoothstep(0.0, 0.75, rings) * (1.0 - smoothstep(0.75, 1.0, rings))
    return 0.9 + 0.14 * g + 0.08 * (n - 0.5)
}

private fun argb(r: Double, g: Double, b: Double, a: Double): Int {
    val alpha = (a.coerceIn(0.0, 1.0) * 255).roundToInt()
    return (alpha shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
}

private fun argb(c: DoubleArray, a: Double) = argb(c[0], c[1], c[2], a)
private fun argb(c: IntArray, a: Double) = argb(c[0].toDouble(), c[1].toDouble(), c[2].toDouble(), a)

private fun mix(a: DoubleArray, b: DoubleArray, t: Double) = doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)
private fun rgb(c: IntArray) = doubleArrayOf(c[0].toDouble(), c[1].toDouble(), c[2].toDouble())
private val WHITE = doubleArrayOf(255.0, 255.0, 255.0)
private val BLACK = doubleArrayOf(0.0, 0.0, 0.0)

/**
 * Draws the board, the figure, the pieces, the flood of colour and the figures to choose from, in
 * the order and with the paints of the React Native picture. Drawn in dp on a canvas scaled by the
 * density. The board is baked once per size; the piece shadows are blurred sprites of each piece's
 * own shape, turned and squeezed with it, so no blur is rasterised per frame.
 */
internal class TangramSnapPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(6f), BlurMaskFilter.Blur.NORMAL)
    }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(9f), BlurMaskFilter.Blur.NORMAL)
    }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val strip = Path()
    private val figure = Path()
    private val mini = Path()
    private val clip = Path()
    private val rect = RectF()
    private val matrix = Matrix()
    private val pose = SlotPose()
    private val pts = DoubleArray(8)
    private val count = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = TANGRAM_INK
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f
        color = TANGRAM_MUTED
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    // One lacquer shader per piece, so each keeps its own uniforms and local matrix.
    private val lacquer: Array<Any?> = Array(7) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(LACQUER_AGSL) else null }

    // The baked board, the shadow sprites and the CPU grain, for one layout and density.
    private var bakedFor: TangramLayout? = null
    private var bakedDensity = 0f
    private var board: Bitmap? = null
    private val shadows = arrayOfNulls<Array<Bitmap>>(7)
    private val shadowBounds = Array(7) { DoubleArray(4) }
    private val grains = arrayOfNulls<Bitmap>(7)
    private val grainBounds = Array(7) { DoubleArray(4) }

    fun draw(canvas: Canvas, e: TangramSnapEngine, insetTop: Double, density: Float) {
        val l = e.layout
        if (!e.ready || l.width <= 0 || l.height <= 0) return
        prepare(l, density)
        val unit = l.unit
        val p = e.pieces
        board?.let {
            rect.set(0f, 0f, l.width.toFloat(), l.height.toFloat())
            canvas.drawBitmap(it, null, rect, bitmapPaint)
        }

        // The figure: the union of its slots, wherever the morph has them.
        figure.rewind()
        for (k in 0 until 7) {
            val at = e.slotNow(k, pose)
            addPolygon(figure, corners(k, at.x, at.y, at.angle, at.flip, unit, 1.0, pts), SHAPES[k].size)
        }
        val fillShow = smooth01(e.fill)
        val from = rgb(SILHOUETTES[e.from].color)
        val to = rgb(SILHOUETTES[e.puzzle].color)
        val solidRGB = if (e.morph < 1) mix(from, to, easeInOut(e.morph)) else to

        // The empty outline: a faint inlay and a fine line around its outer edge only.
        val outlineA = 1 - fillShow
        if (outlineA > 0.01) {
            fill.shader = null
            fill.color = argb(255.0, 238.0, 214.0, 0.06 * outlineA)
            canvas.drawPath(figure, fill)
            canvas.saveLayerAlpha(0f, 0f, l.width.toFloat(), l.height.toFloat(), (outlineA * 255).roundToInt())
            stroke.strokeWidth = 2.6f
            stroke.strokeJoin = Paint.Join.ROUND
            stroke.shader = null
            stroke.color = argb(255.0, 232.0, 204.0, 0.55)
            canvas.drawPath(figure, stroke)
            canvas.drawPath(figure, clear)
            canvas.restore()
        }
        // The slot a held piece is being drawn into glows.
        if (e.magnetSlot >= 0) {
            val at = e.slotNow(e.magnetSlot, pose)
            path.rewind()
            addPolygon(path, corners(e.magnetSlot, at.x, at.y, at.angle, at.flip, unit, 1.0, pts), SHAPES[e.magnetSlot].size)
            glow.color = argb(LACQUER[if (e.held >= 0) e.held else 0], 0.28)
            canvas.drawPath(path, glow)
        }

        // Pieces, bottom first.
        for (o in 0 until 7) {
            val i = p.order[o]
            val grow = 1 + LIFT_GROW * p.lift[i] - (GAP * (1 - fillShow)) / unit
            corners(i, p.x[i], p.y[i], p.angle[i], p.flip[i], unit, grow, pts)
            drawPiece(canvas, i, SHAPES[i].size, p.x[i], p.y[i], p.angle[i], p.flip[i], grow, p.lift[i], if (p.slot[i] >= 0) fillShow else 0.0, solidRGB)
        }

        // A snap sends a ring of light out from the slot.
        stroke.strokeWidth = 2f
        stroke.strokeJoin = Paint.Join.ROUND
        for (k in 0 until 7) {
            if (e.pulse[k] < 0) continue
            val u = e.pulse[k] / 0.6
            val at = e.slotNow(k, pose)
            path.rewind()
            addPolygon(path, corners(k, at.x, at.y, at.angle, at.flip, unit, 1 + 0.18 * smooth01(u), pts), SHAPES[k].size)
            stroke.color = argb(255.0, 240.0, 214.0, 0.6 * (1 - u))
            canvas.drawPath(path, stroke)
        }

        // Solved: the colour floods out from the last piece and the figure becomes one shape.
        if (fillShow > 0.001) {
            val reach = max(MAX_HALF_W, MAX_HALF_H) * unit * 2.6
            canvas.save()
            if (fillShow < 1) {
                clip.rewind()
                clip.addCircle(e.revealX.toFloat(), e.revealY.toFloat(), (reach * fillShow).toFloat(), Path.Direction.CW)
                canvas.clipPath(clip)
            }
            shade.color = argb(0.0, 0.0, 0.0, 0.4 * fillShow)
            canvas.save()
            canvas.translate(0f, 6f)
            canvas.drawPath(figure, shade)
            canvas.restore()
            val a = smooth01(fillShow * 1.4)
            val x0 = (l.boardX - MAX_HALF_W * unit).toFloat()
            val y0 = (l.boardY - MAX_HALF_H * unit).toFloat()
            fill.color = 0xFF000000.toInt()
            fill.shader = LinearGradient(
                x0, y0, (l.boardX + MAX_HALF_W * unit).toFloat(), (l.boardY + MAX_HALF_H * unit).toFloat(),
                argb(mix(solidRGB, WHITE, 0.16), a), argb(mix(solidRGB, BLACK, 0.18), a), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(figure, fill)
            // A lacquer sheen over the whole figure.
            fill.shader = LinearGradient(x0, y0, l.boardX.toFloat(), l.boardY.toFloat(), argb(WHITE, 0.22), argb(WHITE, 0.0), Shader.TileMode.CLAMP)
            canvas.drawPath(figure, fill)
            fill.shader = null
            canvas.restore()
        }

        // The figures to choose from, along the bottom.
        val scale = 40 / (2 * max(MAX_HALF_W, MAX_HALF_H))
        for (f in SILHOUETTES.indices) {
            val cx = l.width / 2 + (f - 1.5) * 64
            val sil = SILHOUETTES[f]
            mini.rewind()
            for (k in 0 until 7) {
                val slot = sil.slots[k]
                corners(k, cx + slot.x * scale, l.thumbY + slot.y * scale, (slot.turns[0] * PI) / 4, slot.flips[0].toDouble(), scale, 1.0, pts)
                addPolygon(mini, pts, SHAPES[k].size)
            }
            val current = f == e.puzzle
            fill.color = if (current) argb(sil.color, 0.95) else argb(242.0, 233.0, 220.0, 0.28)
            canvas.drawPath(mini, fill)
            if (current) {
                fill.color = argb(242.0, 233.0, 220.0, 0.8)
                canvas.drawCircle(cx.toFloat(), (l.thumbY + 27).toFloat(), 2f, fill)
            }
        }

        // Pieces in place, top right.
        val value = "${e.placedCount} / 7"
        val right = (l.width - 24).toFloat()
        val readTop = (insetTop + 12).toFloat()
        canvas.drawText(value, right - count.measureText(value), readTop + 38, count)
        canvas.drawText("IN PLACE", right - small.measureText("IN PLACE"), readTop + 11, small)
    }

    private fun addPolygon(into: Path, p: DoubleArray, n: Int) {
        into.moveTo(p[0].toFloat(), p[1].toFloat())
        var i = 2
        while (i < n) {
            into.lineTo(p[i].toFloat(), p[i + 1].toFloat())
            i += 2
        }
        into.close()
    }

    /** One lacquered piece: its shadow, the wood under the lacquer, bevels, gloss and edge. */
    private fun drawPiece(
        canvas: Canvas, k: Int, n: Int, x: Double, y: Double, angle: Double, flip: Double, grow: Double, lift: Double, solid: Double, solidRGB: DoubleArray,
    ) {
        // The shadow, further and softer as the piece lifts: between the two nearest baked blurs.
        val sigma = 2.2 + 7 * lift
        val alpha = 0.5 - 0.15 * lift
        val level = ((sigma - SIGMAS[0]) / (SIGMAS[1] - SIGMAS[0])).coerceIn(0.0, (SIGMAS.size - 1).toDouble())
        val lo = min(SIGMAS.size - 2, floor(level).toInt())
        val t = level - lo
        canvas.save()
        canvas.translate((x + 2 + 8 * lift).toFloat(), (y + 3 + 13 * lift).toFloat())
        canvas.rotate(Math.toDegrees(angle).toFloat())
        canvas.scale((kotlin.math.cos(PI * flip) * grow).toFloat(), grow.toFloat())
        val sprites = shadows[k]
        val b = shadowBounds[k]
        if (sprites != null) {
            if (t < 0.999) drawShadow(canvas, sprites[lo], b, lo, alpha * (1 - t))
            if (t > 0.001) drawShadow(canvas, sprites[lo + 1], b, lo + 1, alpha * t)
        }
        canvas.restore()

        path.rewind()
        addPolygon(path, pts, n)
        val tintRGB = mix(rgb(LACQUER[k]), solidRGB, solid)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            fillLacquer(canvas, lacquer[k] as RuntimeShader, k, x, y, angle, tintRGB, solid)
        } else {
            fillFallback(canvas, k, x, y, angle, tintRGB, solid)
        }

        // Bevels: each edge's slope lit or shaded by how it faces the light.
        val fade = 1 - solid
        // A piece seen edge-on mid-flip is too thin for a bevel (the inset would fold over itself).
        if (fade > 0.01 && abs(kotlin.math.cos(PI * flip)) > 0.2) {
            val points = pts.copyOf(n)
            val inner = inset(points, BEVEL)
            val c = n / 2
            var area = 0.0
            for (i in 0 until c) {
                val j = (i + 1) % c
                area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1]
            }
            val side = if (area > 0) 1.0 else -1.0
            for (i in 0 until c) {
                val j = (i + 1) % c
                val ex = points[j * 2] - points[i * 2]
                val ey = points[j * 2 + 1] - points[i * 2 + 1]
                val len = hypot(ex, ey)
                val ln = if (len == 0.0) 1.0 else len
                // Outward normal.
                val nx = (ey / ln) * side
                val ny = (-ex / ln) * side
                val facing = nx * LIGHT_X + ny * LIGHT_Y
                strip.rewind()
                strip.moveTo(points[i * 2].toFloat(), points[i * 2 + 1].toFloat())
                strip.lineTo(points[j * 2].toFloat(), points[j * 2 + 1].toFloat())
                strip.lineTo(inner[j * 2].toFloat(), inner[j * 2 + 1].toFloat())
                strip.lineTo(inner[i * 2].toFloat(), inner[i * 2 + 1].toFloat())
                strip.close()
                fill.color = if (facing > 0) argb(255.0, 244.0, 226.0, 0.36 * facing * fade) else argb(20.0, 8.0, 2.0, 0.42 * -facing * fade)
                canvas.drawPath(strip, fill)
            }
        }
        // Gloss: the lacquer catches the light across its upper left.
        path.computeBounds(rect, true)
        fill.color = 0xFF000000.toInt()
        fill.shader = LinearGradient(
            rect.left, rect.top, rect.left + rect.width() * 0.7f, rect.top + rect.height() * 0.7f,
            intArrayOf(argb(WHITE, 0.24), argb(WHITE, 0.04), argb(WHITE, 0.0)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, fill)
        fill.shader = null
        stroke.strokeWidth = 0.8f
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.color = argb(12.0, 6.0, 2.0, 0.45 * fade + 0.1)
        canvas.drawPath(path, stroke)
    }

    private fun drawShadow(canvas: Canvas, sprite: Bitmap, b: DoubleArray, level: Int, alpha: Double) {
        val pad = SIGMAS[level] * 3 + 1
        rect.set((b[0] - pad).toFloat(), (b[1] - pad).toFloat(), (b[2] + pad).toFloat(), (b[3] + pad).toFloat())
        tint.color = argb(0.0, 0.0, 0.0, alpha)
        canvas.drawBitmap(sprite, null, rect, tint)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun fillLacquer(canvas: Canvas, shader: RuntimeShader, k: Int, x: Double, y: Double, angle: Double, tintRGB: DoubleArray, solid: Double) {
        shader.setFloatUniform("tint", (tintRGB[0] / 255).toFloat(), (tintRGB[1] / 255).toFloat(), (tintRGB[2] / 255).toFloat())
        shader.setFloatUniform("seed", (k * 3.7 + 1.3).toFloat())
        shader.setFloatUniform("grain", (1 - solid).toFloat())
        matrix.reset()
        matrix.preTranslate(x.toFloat(), y.toFloat())
        matrix.preRotate(Math.toDegrees(angle).toFloat())
        shader.setLocalMatrix(matrix)
        fill.color = 0xFF000000.toInt()
        fill.shader = shader
        canvas.drawPath(path, fill)
        fill.shader = null
    }

    /**
     * Below Android 13: the tint, then the same grain baked on the CPU in the piece's own frame,
     * multiplied onto the tint at the grain's strength: tint·mix(1, shade, grain).
     */
    private fun fillFallback(canvas: Canvas, k: Int, x: Double, y: Double, angle: Double, tintRGB: DoubleArray, solid: Double) {
        fill.shader = null
        fill.color = argb(tintRGB, 1.0)
        canvas.drawPath(path, fill)
        val grain = 1 - solid
        val bitmap = grains[k] ?: return
        if (grain < 0.01) return
        val b = grainBounds[k]
        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        matrix.reset()
        matrix.preTranslate(x.toFloat(), y.toFloat())
        matrix.preRotate(Math.toDegrees(angle).toFloat())
        matrix.preTranslate(b[0].toFloat(), b[1].toFloat())
        matrix.preScale(((b[2] - b[0]) / bitmap.width).toFloat(), ((b[3] - b[1]) / bitmap.height).toFloat())
        bitmapShader.setLocalMatrix(matrix)
        val colour = LinearGradient(0f, 0f, 1f, 0f, argb(tintRGB, 1.0), argb(tintRGB, 1.0), Shader.TileMode.CLAMP)
        fill.color = argb(0.0, 0.0, 0.0, grain)
        fill.shader = ComposeShader(bitmapShader, colour, PorterDuff.Mode.MULTIPLY)
        canvas.drawPath(path, fill)
        fill.shader = null
    }

    /** Bakes the board, the shadow sprites and (below Android 13) the grain for a layout. */
    private fun prepare(l: TangramLayout, density: Float) {
        if (l == bakedFor && density == bakedDensity) return
        bakedFor = l
        bakedDensity = density
        val res = min(density, 3f)
        board = bakeBoard(l, res)
        for (k in 0 until 7) {
            val shape = SHAPES[k]
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (i in shape.indices step 2) {
                minX = min(minX, shape[i] * l.unit)
                maxX = max(maxX, shape[i] * l.unit)
                minY = min(minY, shape[i + 1] * l.unit)
                maxY = max(maxY, shape[i + 1] * l.unit)
            }
            val b = shadowBounds[k]
            b[0] = minX
            b[1] = minY
            b[2] = maxX
            b[3] = maxY
            shadows[k] = Array(SIGMAS.size) { s -> bakeShadow(shape, l.unit, b, SIGMAS[s], res) }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // The grain is drawn rotated with the piece, so the bitmap covers its whole turn.
                val reach = max(max(-minX, maxX), max(-minY, maxY)) * 1.5 + 2
                val g = grainBounds[k]
                g[0] = -reach
                g[1] = -reach
                g[2] = reach
                g[3] = reach
                grains[k] = bakeGrain(g, k * 3.7 + 1.3)
            }
        }
    }

    private fun bakeShadow(shape: DoubleArray, unit: Double, b: DoubleArray, sigma: Double, res: Float): Bitmap {
        val pad = sigma * 3 + 1
        val w = b[2] - b[0] + pad * 2
        val h = b[3] - b[1] + pad * 2
        val bitmap = Bitmap.createBitmap(ceil(w * res).toInt(), ceil(h * res).toInt(), Bitmap.Config.ALPHA_8)
        val c = Canvas(bitmap)
        c.scale(bitmap.width / w.toFloat(), bitmap.height / h.toFloat())
        c.translate((pad - b[0]).toFloat(), (pad - b[1]).toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma.toFloat()), BlurMaskFilter.Blur.NORMAL)
        }
        val outline = Path()
        outline.moveTo((shape[0] * unit).toFloat(), (shape[1] * unit).toFloat())
        for (i in 2 until shape.size step 2) outline.lineTo((shape[i] * unit).toFloat(), (shape[i + 1] * unit).toFloat())
        outline.close()
        c.drawPath(outline, paint)
        return bitmap
    }

    /** The grain's shade (grain 1) over [g], at 2 px per dp, as grey in a bitmap. */
    private fun bakeGrain(g: DoubleArray, seed: Double): Bitmap {
        val w = ceil((g[2] - g[0]) * 2).toInt()
        val h = ceil((g[3] - g[1]) * 2).toInt()
        val pixels = IntArray(w * h)
        for (row in 0 until h) {
            val py = g[1] + (row + 0.5) / 2
            for (col in 0 until w) {
                val px = g[0] + (col + 0.5) / 2
                val v = (grainShade(px, py, seed).coerceIn(0.0, 1.0) * 255).roundToInt()
                pixels[row * w + col] = (255 shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** The board: dark lacquered wood, lit in a soft pool over the figure, and a felt-lined well. */
    private fun bakeBoard(l: TangramLayout, res: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(ceil(l.width * res).toInt(), ceil(l.height * res).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(res, res)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            l.boardX.toFloat(), l.boardY.toFloat(), (max(l.width, l.height) * 0.8).toFloat(), TangramBoard, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, l.width.toFloat(), l.height.toFloat(), paint)
        paint.shader = null
        // The well that holds the loose pieces.
        val w = ((LOOSE_HALF_W + 0.55) * l.unit).toFloat()
        val h = ((LOOSE_HALF_H + 0.4) * l.unit).toFloat()
        val x = l.looseX.toFloat()
        val y = l.looseY.toFloat()
        val well = Path().apply { addRoundRect(x - w, y - h, x + w, y + h, 22f, 22f, Path.Direction.CW) }
        paint.color = argb(0.0, 0.0, 0.0, 0.22)
        c.drawPath(well, paint)
        c.save()
        c.clipPath(well)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = argb(0.0, 0.0, 0.0, 0.35)
            maskFilter = BlurMaskFilter(sigmaToRadius(8f), BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(x - w - 4, y - h - 4, x + w + 4, y + h + 4, 26f, 26f, inner)
        c.restore()
        val lip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = argb(255.0, 236.0, 210.0, 0.07)
        }
        c.drawPath(well, lip)
        return bitmap
    }

    private companion object {
        /** The shadow blurs baked per piece (dp), from resting (2.2) to fully lifted (9.2). */
        val SIGMAS = doubleArrayOf(2.2, 2.2 + 7.0 / 3, 2.2 + 14.0 / 3, 9.2)
    }
}
