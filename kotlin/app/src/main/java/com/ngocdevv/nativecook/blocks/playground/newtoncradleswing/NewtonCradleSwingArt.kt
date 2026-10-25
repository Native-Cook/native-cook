package com.ngocdevv.nativecook.blocks.playground.newtoncradleswing

import android.graphics.Bitmap
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
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Everything drawn on the cradle's canvas, in dp. The room (wall, walnut desk, the base, the back
 posts and rail), the front posts and rail, and one chrome ball are rendered once into bitmaps;
 the ball bitmap is drawn five times. Per frame only the strings, the balls' positions, their
 contact shadows, the neighbour reflections, the eyelets and the fingertip are drawn.

 Light: a lamp off screen at the upper left.
 */

internal class CradleGeo(val w: Float, val h: Float) {
    // Big chrome balls; the strings and the pull angle are chosen so the outer ball, at the
    // furthest pull, still clears the front posts on a narrow screen.
    val r = min(max(w * 0.066f, 22f), 30f)
    val l = min(max(w * 0.5f, 170f), 200f)
    val baseTop = h * 0.68f
    val restY = baseTop - 30f - r
    val pivotY = restY - l
    val yF = pivotY + 7f
    val cx = w / 2f
    val xs = FloatArray(CRADLE_BALLS) { cx + (it - 2) * 2 * r }
    val yB = yF - 14f
    val frontX = 8f
    val backX = 22f
    val baseFront = baseTop + 16f
    val baseBottom = baseTop + 30f
    val deskY = baseTop - 22f
}

private fun argb(hex: Int, a: Float = 1f): Int = ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or (hex and 0xFFFFFF)

private fun dimmed(hex: Int, dim: Float): Int {
    val r = (((hex shr 16) and 0xff) * dim).roundToInt()
    val g = (((hex shr 8) and 0xff) * dim).roundToInt()
    val b = ((hex and 0xff) * dim).roundToInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun hash(i: Int): Float {
    val s = sin(i * 12.9898) * 43758.5453
    return (s - floor(s)).toFloat()
}

private fun sigmaRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

private fun blurPaint(color: Int, sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    maskFilter = BlurMaskFilter(sigmaRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun gradient(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP) }

private fun bitmapFor(g: CradleGeo, density: Float): Pair<Bitmap, Canvas> {
    val bmp = Bitmap.createBitmap((g.w * density).roundToInt().coerceAtLeast(1), (g.h * density).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    return bmp to c
}

/** A brushed-steel bar: a cylinder lit from the upper left, along x (horizontal) or y (vertical). */
private fun steelBar(c: Canvas, x: Float, y: Float, w: Float, h: Float, vertical: Boolean, dim: Float) {
    val colors = intArrayOf(0x3c4047, 0xdfe3e8, 0xa7adb5, 0x6b7078, 0x2c2f34).map { dimmed(it, dim) }.toIntArray()
    val pos = floatArrayOf(0f, 0.28f, 0.5f, 0.78f, 1f)
    val p = if (vertical) gradient(x, 0f, x + w, 0f, colors, pos) else gradient(0f, y, 0f, y + h, colors, pos)
    val r = min(w, h) / 2
    c.drawRoundRect(RectF(x, y, x + w, y + h), r, r, p)
    // Brushing: a few faint lines along the bar.
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f }
    for (k in 0 until 3) {
        line.color = argb(0xffffff, 0.06f + 0.04f * k)
        val f = 0.3f + k * 0.18f
        if (vertical) c.drawLine(x + w * f, y + 2, x + w * f, y + h - 2, line)
        else c.drawLine(x + 2, y + h * f, x + w - 2, y + h * f, line)
    }
}

private fun joint(c: Canvas, x: Float, y: Float, r: Float, dim: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(x - r * 0.4f, y - r * 0.45f, r * 1.6f, intArrayOf(dimmed(0xf4f6f8, dim), dimmed(0x9aa0a8, dim), dimmed(0x2a2d32, dim)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawCircle(x, y, r, p)
}

/** Grey noise for the film grain (Skia's fractal noise, approximated). */
private fun noiseShader(seed: Int): BitmapShader {
    val size = 96
    val px = IntArray(size * size)
    var s = seed
    for (i in px.indices) {
        s = s * 1103515245 + 12345
        val v = (s ushr 16) and 0xff
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return BitmapShader(Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setScale(1.1f, 1.1f) })
    }
}

/** The wall, the desk, the base, and the back half of the frame. */
internal fun cradleRenderBack(g: CradleGeo, density: Float): Bitmap {
    val (bmp, c) = bitmapFor(g, density)
    val w = g.w
    val h = g.h
    val deskY = g.deskY

    // Wall: warm charcoal, lit from the upper left.
    c.drawRect(0f, 0f, w, deskY, gradient(0f, 0f, 0f, deskY, intArrayOf(argb(0x221b16), argb(0x18130f), argb(0x100d0b)), floatArrayOf(0f, 0.55f, 1f)))
    c.drawRect(0f, 0f, w, deskY, Paint().apply { shader = RadialGradient(w * 0.06f, h * 0.1f, w * 1.05f, intArrayOf(argb(0xffc184, 0.2f), argb(0xffa860, 0.07f), argb(0xff9a50, 0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP) })

    // Walnut desk: darker far away, grain lines spreading out toward the viewer.
    c.drawRect(0f, deskY, w, h, gradient(0f, deskY, 0f, h, intArrayOf(argb(0x24160e), argb(0x3a2417), argb(0x4a2e1c), argb(0x3b2416), argb(0x1f130c)), floatArrayOf(0f, 0.18f, 0.45f, 0.75f, 1f)))
    val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val lines = 72
    for (k in 0 until lines) {
        val f = (k.toFloat() / lines).pow(1.55f)
        val y0 = deskY + 3 + f * (h - deskY)
        val a = hash(k + 1) * 6.28f
        val b = hash(k + 31) * 6.28f
        val path = Path()
        var x = -10f
        while (x <= w + 10) {
            val y = y0 + sin(x * 0.012f + a) * (1 + f * 2.5f) + sin(x * 0.047f + b) * (0.4f + f)
            if (x == -10f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 14f
        }
        grain.strokeWidth = 0.5f + f * 1.4f * hash(k + 7)
        grain.color = if (k % 3 == 0) argb(0xb07a4c, 0.08f + 0.05f * f) else argb(0x120904, 0.22f + 0.18f * hash(k + 3))
        c.drawPath(path, grain)
    }
    // The lamp's pool on the desk, and the shade where the desk meets the wall.
    c.drawRect(0f, deskY, w, h, Paint().apply { shader = RadialGradient(w * 0.22f, deskY + 40, w * 0.9f, intArrayOf(argb(0xffc28a, 0.17f), argb(0xffb070, 0.05f), argb(0xffb070, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) })
    c.drawRect(0f, deskY, w, deskY + 18, gradient(0f, deskY, 0f, deskY + 18, intArrayOf(argb(0, 0.4f), argb(0, 0f)), floatArrayOf(0f, 1f)))

    // Base: a smoked-steel slab with a soft shadow on the desk.
    val left = 6f
    val right = w - 6f
    c.drawRoundRect(RectF(left + 6, g.baseBottom - 6, right - 6, g.baseBottom + 10), 8f, 8f, blurPaint(argb(0, 0.55f), 9f))
    c.drawRoundRect(RectF(left + 2, g.baseBottom - 3, right - 2, g.baseBottom + 2), 3f, 3f, blurPaint(argb(0, 0.6f), 2f))
    c.drawRoundRect(
        RectF(left, g.baseTop, right, g.baseBottom), 5f, 5f,
        gradient(0f, g.baseTop, 0f, g.baseBottom, intArrayOf(argb(0x2b2d31), argb(0x3a3d43), argb(0x33363b), argb(0x1a1b1e), argb(0x121315)), floatArrayOf(0f, 0.4f, 0.53f, 0.56f, 1f)),
    )
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = argb(0xffffff, 0.16f) }
    c.drawLine(left + 4, g.baseFront + 0.5f, right - 4, g.baseFront + 0.5f, edge)
    edge.color = argb(0xffffff, 0.08f)
    c.drawLine(left + 5, g.baseTop + 0.5f, right - 5, g.baseTop + 0.5f, edge)

    // Back posts and rail (dimmer: further from the lamp).
    for (x in floatArrayOf(g.backX, w - g.backX)) steelBar(c, x - 2.5f, g.yB, 5f, g.baseTop + 4 - g.yB, true, 0.72f)
    steelBar(c, g.backX - 2.5f, g.yB - 2.5f, w - 2 * g.backX + 5, 5f, false, 0.72f)
    joint(c, g.backX, g.yB, 3.6f, 0.72f)
    joint(c, w - g.backX, g.yB, 3.6f, 0.72f)

    // Film grain over everything (4.5 %), then the vignette.
    c.drawRect(0f, 0f, w, h, Paint().apply {
        shader = noiseShader(7)
        alpha = (0.045f * 255).roundToInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    })
    c.drawRect(0f, 0f, w, h, Paint().apply { shader = RadialGradient(w * 0.45f, h * 0.45f, hypot(w, h) * 0.62f, intArrayOf(argb(0, 0f), argb(0, 0f), argb(0, 0.45f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP) })
    return bmp
}

/** The front posts and rail, drawn over the strings. */
internal fun cradleRenderFront(g: CradleGeo, density: Float): Bitmap {
    val (bmp, c) = bitmapFor(g, density)
    for (x in floatArrayOf(g.frontX, g.w - g.frontX)) {
        // Foot plate and post.
        c.drawOval(RectF(x - 7, g.baseFront - 4, x + 7, g.baseFront + 2), blurPaint(argb(0, 0.45f), 1.5f))
        steelBar(c, x - 3, g.yF, 6f, g.baseFront - g.yF, true, 1f)
    }
    steelBar(c, g.frontX - 3, g.yF - 3, g.w - 2 * g.frontX + 6, 6f, false, 1f)
    joint(c, g.frontX, g.yF, 4.4f, 1f)
    joint(c, g.w - g.frontX, g.yF, 4.4f, 1f)
    return bmp
}

/** One chrome ball, a bitmap (2R + 8) dp square centred on the ball. */
internal fun cradleRenderBall(r: Float, density: Float): Bitmap {
    val side = ((2 * r + 8) * density).roundToInt()
    val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    c.translate(r + 4, r + 4)
    // The room reflected: lit ceiling and wall above, the dark horizon, the walnut desk below.
    c.drawCircle(0f, 0f, r, gradient(0f, -r, 0f, r, intArrayOf(argb(0xf3f5f7), argb(0xd2d7dd), argb(0x8d939b), argb(0x3e3d3c), argb(0x221c18)), floatArrayOf(0f, 0.2f, 0.38f, 0.47f, 0.52f)))
    c.save()
    c.clipPath(Path().apply { addCircle(0f, 0f, r, Path.Direction.CW) })
    // The desk's reflection is bounded by a curved horizon (a sphere bends straight lines).
    c.drawOval(
        RectF(-r * 1.3f, r * 0.02f, r * 1.3f, r * 2.12f),
        gradient(0f, r * 0.02f, 0f, r, intArrayOf(argb(0x2a1b12), argb(0x6c4630), argb(0x8a5a3a), argb(0x3a2416), argb(0x150d08)), floatArrayOf(0f, 0.14f, 0.32f, 0.7f, 1f)),
    )
    // Rim: the reflection compresses and darkens toward the edge.
    c.drawCircle(0f, 0f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(-r * 0.08f, -r * 0.1f, r * 1.08f, intArrayOf(argb(0, 0f), argb(0, 0f), argb(0x0a0806, 0.42f), argb(0x050403, 0.7f)), floatArrayOf(0f, 0.68f, 0.9f, 1f), Shader.TileMode.CLAMP)
    })
    // Warm bounce from the desk along the lower right rim.
    val bounce = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = r * 0.13f
        color = argb(0xffbf8a, 0.32f)
        maskFilter = BlurMaskFilter(sigmaRadius(max(0.6f, r * 0.06f)), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawArc(RectF(-r * 0.9f, -r * 0.9f, r * 0.9f, r * 0.9f), 10f, 70f, false, bounce)
    c.restore()
    // The window: a soft glow, then four sharp panes, tilted with the sphere's curvature.
    c.save()
    c.translate(-r * 0.36f, -r * 0.44f)
    c.rotate(-26f)
    c.drawRoundRect(RectF(-r * 0.3f, -r * 0.2f, r * 0.3f, r * 0.22f), r * 0.12f, r * 0.12f, blurPaint(argb(0xffffff, 0.42f), r * 0.14f))
    val pane = blurPaint(argb(0xfffdf8, 0.95f), 0.6f)
    val pw = r * 0.2f
    val ph = r * 0.14f
    for (row in 0 until 2) for (col in 0 until 2) {
        val x = -r * 0.22f + col * (pw + r * 0.04f)
        val y = -r * 0.16f + row * (ph + r * 0.04f)
        c.drawRoundRect(RectF(x, y, x + pw, y + ph), r * 0.03f, r * 0.03f, pane)
    }
    c.restore()
    // A pin-point of the lamp itself.
    c.drawCircle(-r * 0.12f, -r * 0.66f, r * 0.05f, blurPaint(argb(0xffffff, 0.85f), 0.6f))
    // A hairline where the ball meets the dark.
    c.drawCircle(0f, 0f, r - 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = argb(0, 0.55f) })
    return bmp
}

/** The per-frame drawing, with its paints, paths and arrays made once. */
internal class CradlePainter(private val g: CradleGeo) {
    private val liveBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    private val bx = FloatArray(CRADLE_BALLS)
    private val by = FloatArray(CRADLE_BALLS)
    private val attach = FloatArray(CRADLE_BALLS * 2)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowBlurs = HashMap<Int, BlurMaskFilter>()
    private val str = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f }
    private val near = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spot = Paint(Paint.ANTI_ALIAS_FLAG).apply { if (liveBlur) maskFilter = BlurMaskFilter(sigmaRadius(0.6f), BlurMaskFilter.Blur.NORMAL) }
    private val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-2.5f, 0f, 2.5f, 0f, intArrayOf(argb(0x5a5f66), argb(0xeef0f3), argb(0x7d838b)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }
    private val tipFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tipRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f }
    private val ballPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clip = Path().apply { addCircle(0f, 0f, g.r * 0.97f, Path.Direction.CW) }
    private val rect = RectF()
    private val capRect = RectF(-g.r * 0.11f, -g.r - g.r * 0.2f, g.r * 0.11f, -g.r + g.r * 0.06f)

    /**
     * Draws one frame over the back bitmap and under nothing: `angles` are the five balls' angles
     * from the vertical (radians); `finger` (0–1) shows the scripted fingertip on ball 1.
     */
    fun draw(c: Canvas, angles: DoubleArray, finger: Float, ball: Bitmap, front: Bitmap, density: Float) {
        val r = g.r
        for (i in 0 until CRADLE_BALLS) {
            bx[i] = g.xs[i] + g.l * sin(angles[i]).toFloat()
            by[i] = g.pivotY + g.l * cos(angles[i]).toFloat()
        }

        // Contact shadows on the base's top face: softer and fainter the higher the ball.
        for (i in 0 until CRADLE_BALLS) {
            val lift = max(0f, g.baseTop - (by[i] + r))
            val alpha = 0.5f * min(1f, 34f / (lift + 4))
            shadow.color = argb(0, alpha)
            if (liveBlur) {
                // Sigma 2.2 + lift · 0.09, in steps of 0.25 so filters are reused.
                val key = ((2.2f + lift * 0.09f) * 4).roundToInt()
                shadow.maskFilter = shadowBlurs.getOrPut(key) { BlurMaskFilter(sigmaRadius(key / 4f), BlurMaskFilter.Blur.NORMAL) }
            }
            val x0 = bx[i] - r * 1.02f + lift * 0.25f
            rect.set(x0, g.baseTop + 4, x0 + r * 2.04f, g.baseTop + 12)
            c.drawOval(rect, shadow)
        }

        // Strings: two per ball, to the back rail (behind) and the front rail (in front).
        for (i in 0 until CRADLE_BALLS) {
            val ux = g.xs[i] - bx[i]
            val uy = g.pivotY - by[i]
            val ul = hypot(ux, uy)
            attach[2 * i] = bx[i] + ux / ul * (r + 2)
            attach[2 * i + 1] = by[i] + uy / ul * (r + 2)
        }
        str.color = argb(0xb9b2a6, 0.42f)
        for (i in 0 until CRADLE_BALLS) c.drawLine(attach[2 * i], attach[2 * i + 1], g.cx + (g.xs[i] - g.cx) * 0.93f, g.yB, str)

        // Balls, with a dark mirrored neighbour where another ball is close, and the string's eyelet.
        val inv = 1f / density
        for (i in 0 until CRADLE_BALLS) {
            c.save()
            c.translate(bx[i], by[i])
            c.save()
            c.translate(-r - 4, -r - 4)
            c.scale(inv, inv)
            c.drawBitmap(ball, 0f, 0f, ballPaint)
            c.restore()
            c.save()
            c.clipPath(clip)
            for (j in intArrayOf(i - 1, i + 1)) {
                if (j < 0 || j >= CRADLE_BALLS) continue
                val dx = bx[j] - bx[i]
                val dy = by[j] - by[i]
                val d = hypot(dx, dy)
                val k = ((3.4f * r - d) / (1.4f * r)).coerceIn(0f, 1f)
                if (k <= 0.01f) continue
                c.save()
                c.rotate((atan2(dy, dx) * 180f / PI.toFloat()))
                near.color = argb(0x14100d, 0.72f * k)
                rect.set(r * 0.68f, -r * 0.34f, r * 0.98f, r * 0.34f)
                c.drawOval(rect, near)
                spot.color = argb(0xffffff, 0.55f * k)
                c.drawCircle(r * 0.8f, -r * 0.12f, r * 0.045f, spot)
                c.restore()
            }
            c.restore()
            // Eyelet, along the string: rotate so the local −y axis points at the pivot.
            val ang = atan2(g.xs[i] - bx[i], by[i] - g.pivotY)
            c.rotate(ang * 180f / PI.toFloat())
            c.drawRoundRect(capRect, r * 0.06f, r * 0.06f, cap)
            c.restore()
        }

        str.color = argb(0xd9d2c6, 0.58f)
        for (i in 0 until CRADLE_BALLS) c.drawLine(attach[2 * i], attach[2 * i + 1], g.xs[i], g.yF, str)
        c.save()
        c.scale(inv, inv)
        c.drawBitmap(front, 0f, 0f, ballPaint)
        c.restore()

        // The scripted fingertip on ball 1.
        if (finger > 0.001f) {
            val tr = g.r * 1.1f * (1.12f - 0.12f * finger)
            tipFill.color = argb(0xfff1e2, 0.16f * finger)
            c.drawCircle(bx[0] - 2, by[0], tr, tipFill)
            tipRing.color = argb(0xfff1e2, 0.5f * finger)
            c.drawCircle(bx[0] - 2, by[0], tr, tipRing)
        }
    }

    /** Ball i's current centre, for picking it up. */
    fun ballX(i: Int) = bx[i]
    fun ballY(i: Int) = by[i]
}
