package com.ngocdevv.nativecook.blocks.progress.walkingstepsbuddy

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Everything of Walking Steps Buddy drawn in code: the parallax street, Bao the bun buddy, the goal
 * bar and the small icons, on a native canvas in the 393-pt-wide design space.
 */
internal object WalkingStepsBuddyArt {
    private val m = WalkingStepsBuddyMotion

    const val PAGE = 0xFCFCFA
    const val INK = 0x1D2733
    const val LABEL = 0x4A5A6C
    const val MUTED = 0x8A95A3
    const val ORANGE = 0xFF6A1F
    const val ORANGE_LIGHT = 0xFF9A4D

    // MARK: Colour and paint

    fun argb(hex: Int, a: Double = 1.0): Int = ((m.clamp(a) * 255).roundToInt() shl 24) or (hex and 0xFFFFFF)

    fun mix(a: Int, b: Int, f: Double, alpha: Double = 1.0): Int {
        val g = m.clamp(f)
        fun ch(h: Int, s: Int) = (h shr s) and 255
        fun lerp(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * g).roundToInt()
        return argb((lerp(16) shl 16) or (lerp(8) shl 8) or lerp(0), alpha)
    }

    private val Double.f get() = toFloat()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.WHITE
    }
    private val rect = RectF()

    private fun solid(color: Int): Paint = fillPaint.apply { shader = null; this.color = color }
    /** A gradient fill; the paint is made opaque first so it never keeps an earlier colour's alpha. */
    private fun shaded(shader: Shader): Paint = fillPaint.apply { color = 0xFF000000.toInt(); this.shader = shader }
    private fun line(color: Int, width: Double, round: Boolean = true): Paint = strokePaint.apply {
        shader = null
        this.color = color
        strokeWidth = width.f
        strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        strokeJoin = Paint.Join.ROUND
    }

    private fun lin(x0: Double, y0: Double, x1: Double, y1: Double, colors: IntArray, stops: FloatArray? = null) =
        LinearGradient(x0.f, y0.f, x1.f, y1.f, colors, stops, Shader.TileMode.CLAMP)

    private fun rad(cx: Double, cy: Double, r: Double, colors: IntArray, stops: FloatArray? = null) =
        RadialGradient(cx.f, cy.f, max(0.01, r).f, colors, stops, Shader.TileMode.CLAMP)

    private fun oval(cx: Double, cy: Double, w: Double, h: Double): RectF {
        rect.set((cx - w / 2).f, (cy - h / 2).f, (cx + w / 2).f, (cy + h / 2).f)
        return rect
    }

    private fun box(x: Double, y: Double, w: Double, h: Double): RectF {
        rect.set(x.f, y.f, (x + w).f, (y + h).f)
        return rect
    }

    private fun Canvas.ellipse(cx: Double, cy: Double, w: Double, h: Double, p: Paint) = drawOval(oval(cx, cy, w, h), p)
    private fun Canvas.round(x: Double, y: Double, w: Double, h: Double, r: Double, p: Paint) = drawRoundRect(box(x, y, w, h), r.f, r.f, p)
    private fun Path.mv(x: Double, y: Double) = moveTo(x.f, y.f)
    private fun Path.ln(x: Double, y: Double) = lineTo(x.f, y.f)
    private fun Path.qd(cx: Double, cy: Double, x: Double, y: Double) = quadTo(cx.f, cy.f, x.f, y.f)
    private fun Path.cb(c1x: Double, c1y: Double, c2x: Double, c2y: Double, x: Double, y: Double) =
        cubicTo(c1x.f, c1y.f, c2x.f, c2y.f, x.f, y.f)

    private inline fun Canvas.layer(block: Canvas.() -> Unit) {
        val n = save()
        block()
        restoreToCount(n)
    }

    // MARK: Helpers

    private fun blob(c: Canvas, cx: Double, cy: Double, r: Double, hi: Int, base: Int, shade: Int) {
        c.ellipse(cx, cy, 2 * r, 2 * r, shaded(rad(cx - 0.35 * r, cy - 0.45 * r, 1.35 * r,
            intArrayOf(argb(hi), argb(base), argb(shade)), floatArrayOf(0f, 0.5f, 1f))))
    }

    private fun softShadow(c: Canvas, cx: Double, cy: Double, w: Double, h: Double, alpha: Double, hex: Int = 0x6B5A3E) {
        c.layer {
            translate(cx.f, cy.f)
            scale(1f, (h / w).f)
            ellipse(0.0, 0.0, w, w, shaded(rad(0.0, 0.0, w / 2,
                intArrayOf(argb(hex, alpha), argb(hex, alpha * 0.6), argb(hex, 0.0)), floatArrayOf(0f, 0.5f, 1f))))
        }
    }

    private fun tuft(c: Canvas, x: Double, base: Double, h: Double, dark: Int = 0x6CBE4C, light: Int = 0xA8E07E) {
        val p = Path()
        for ((dx, dh, lean) in listOf(Triple(-5.0, 0.72, -5.0), Triple(5.0, 0.8, 6.0), Triple(0.0, 1.0, 1.0))) {
            val bx = x + dx * h / 20
            val w = 7 * h / 20
            val top = base - h * dh
            p.mv(bx - w / 2, base)
            p.qd(bx - w / 4 + lean * h / 40, base - h * dh * 0.55, bx + lean * h / 20, top)
            p.qd(bx + w / 4 + lean * h / 40, base - h * dh * 0.5, bx + w / 2, base)
            p.close()
        }
        c.drawPath(p, shaded(lin(x, base - h, x, base, intArrayOf(argb(light), argb(dark)))))
    }

    private inline fun tiled(distance: Double, factor: Double, width: Double, draw: (Double) -> Unit) {
        val off = m.mod(distance * factor, width)
        var x = -off
        while (x < 393 + 140) {
            if (x + width > -140) draw(x)
            x += width
        }
    }

    // MARK: Scene

    fun drawScene(c: Canvas, d: Double, t: Double, g: Double, pose: WalkingStepsBuddyMotion.Pose, happy: Boolean) {
        val dusk = m.dusk(t)
        val tint = 0.85 * dusk
        c.drawRect(box(0.0, 0.0, 393.0, g + 8), shaded(lin(0.0, 0.0, 0.0, g + 8,
            intArrayOf(mix(0x8CCBF7, 0xB3A6EA, tint), mix(0xB8E0FA, 0xF6BCB4, tint), mix(0xE6F4FC, 0xFFE4C8, tint)),
            floatArrayOf(0f, 0.55f, 1f))))
        // Sun: sets behind the hills at dusk and rises again for the next day.
        val sunY = g - 210 + 250 * dusk
        c.layer {
            clipRect(box(0.0, 0.0, 393.0, g))
            ellipse(318.0, sunY, 128.0, 128.0, shaded(rad(318.0, sunY, 64.0,
                intArrayOf(mix(0xFFF3B0, 0xFFC27A, dusk, 0.55), mix(0xFFF3B0, 0xFFC27A, dusk, 0.0)))))
            ellipse(318.0, sunY, 40.0, 40.0, shaded(rad(312.0, sunY - 6, 24.0,
                intArrayOf(mix(0xFFFBE0, 0xFFE2B0, dusk), mix(0xFFD66B, 0xFF9A5A, dusk)))))
        }
        drawClouds(c, d, g, dusk)
        drawHills(c, d, g, dusk)
        tiled(d, m.BUSH_FACTOR, m.BUSH_TILE) { x -> drawBushTile(c, x, g) }
        c.drawRect(box(0.0, g - 5, 393.0, 8.0), shaded(lin(0.0, g - 5, 0.0, g + 3, intArrayOf(argb(0xC4E9A4), argb(0xA9DC86)))))
        val s = g + 38
        c.drawRect(box(0.0, g, 393.0, 40.0), shaded(lin(0.0, g, 0.0, s + 2,
            intArrayOf(argb(0xF3DDB4), argb(0xF8EAD0), argb(0xFAF1E1), argb(PAGE)), floatArrayOf(0f, 0.5f, 0.82f, 1f))))
        c.drawRect(box(0.0, g, 393.0, 1.2), solid(argb(0xFFFFFF, 0.55)))
        tiled(d, 1.0, m.TILE) { x -> drawPropTile(c, x, g) }
        drawBao(c, m.MASCOT_X, g + 22, pose, happy)
        tiled(d, m.FRONT_FACTOR, m.FRONT_TILE) { x ->
            tuft(c, x + 40, s - 1, 20.0, 0x58AE40, 0x93D56E)
            tuft(c, x + 196, s - 2, 26.0, 0x58AE40, 0x93D56E)
            tuft(c, x + 300, s - 1, 15.0, 0x58AE40, 0x93D56E)
        }
    }

    private fun cloudPath(cx: Double, cy: Double, s: Double): Path = Path().apply {
        addOval(RectF((cx - 34 * s).f, (cy - 22 * s).f, (cx + 10 * s).f, (cy + 22 * s).f), Path.Direction.CW)
        addOval(RectF((cx - 6 * s).f, (cy - 30 * s).f, (cx + 44 * s).f, (cy + 20 * s).f), Path.Direction.CW)
        addOval(RectF((cx - 54 * s).f, (cy - 6 * s).f, (cx - 20 * s).f, (cy + 24 * s).f), Path.Direction.CW)
        addOval(RectF((cx + 26 * s).f, (cy - 4 * s).f, (cx + 58 * s).f, (cy + 24 * s).f), Path.Direction.CW)
        addRoundRect(RectF((cx - 46 * s).f, (cy + 2 * s).f, (cx + 52 * s).f, (cy + 24 * s).f), (11 * s).f, (11 * s).f, Path.Direction.CW)
    }

    private val clouds = listOf(Triple(60.0, -200.0, 1.0), Triple(300.0, -250.0, 0.78), Triple(190.0, -150.0, 0.62), Triple(380.0, -292.0, 0.9))

    private fun drawClouds(c: Canvas, d: Double, g: Double, dusk: Double) {
        for ((i, cl) in clouds.withIndex()) {
            val tau = m.fract(d / m.TILE + i / 4.0)
            val alpha = m.smooth(0.0, 0.18, tau) * (1 - m.smooth(0.82, 1.0, tau))
            if (alpha < 0.01) continue
            val x = cl.first - m.CLOUD_DRIFT * tau
            val y = g + cl.second
            val s = cl.third
            c.drawPath(cloudPath(x, y, s), shaded(lin(x, y - 10 * s, x, y + 24 * s,
                intArrayOf(mix(0xFFFFFF, 0xFFF0EA, dusk, alpha), mix(0xE4F1FB, 0xF3CBCB, dusk, alpha)))))
        }
    }

    private fun drawHills(c: Canvas, d: Double, g: Double, dusk: Double) {
        val off = m.mod(d * m.HILL_FACTOR, m.HILL_TILE)
        val p = Path()
        p.mv(-4.0, g + 2)
        var x = -4.0
        while (x <= 397) {
            val u = 2 * PI * (x + off) / m.HILL_TILE
            p.ln(x, g - 22 - 10 * sin(u) - 5 * sin(2 * u + 1.3))
            x += 4
        }
        p.ln(397.0, g + 2)
        p.close()
        c.drawPath(p, shaded(lin(0.0, g - 40, 0.0, g,
            intArrayOf(mix(0xD3EEDB, 0xDCD4E4, dusk * 0.6), mix(0xB9E0C5, 0xC2D3BE, dusk * 0.6)))))
    }

    private fun drawBushTile(c: Canvas, x: Double, g: Double) {
        c.round(x + 66, g - 40, 9.0, 40.0, 3.0, shaded(lin(x + 66, 0.0, x + 75, 0.0, intArrayOf(argb(0xD5A877), argb(0xB98A5C)))))
        blob(c, x + 52, g - 52, 20.0, 0xC7EDA9, 0x9BD67E, 0x76BD5F)
        blob(c, x + 90, g - 50, 21.0, 0xC7EDA9, 0x9BD67E, 0x76BD5F)
        blob(c, x + 71, g - 70, 29.0, 0xCDF0B0, 0xA2DA84, 0x7CC164)
        blob(c, x + 190, g - 12, 17.0, 0xC2EBA2, 0x94D276, 0x6FB95A)
        blob(c, x + 232, g - 11, 15.0, 0xC2EBA2, 0x94D276, 0x6FB95A)
        blob(c, x + 211, g - 19, 22.0, 0xC9EEAB, 0x9AD57C, 0x73BC5D)
        blob(c, x + 322, g - 11, 15.0, 0xC2EBA2, 0x94D276, 0x6FB95A)
        blob(c, x + 342, g - 17, 19.0, 0xC9EEAB, 0x9AD57C, 0x73BC5D)
        c.round(x + 396, g - 30, 7.0, 30.0, 3.0, solid(argb(0xC99A6B)))
        blob(c, x + 399, g - 48, 18.0, 0xCDF0B0, 0xA2DA84, 0x7CC164)
    }

    private val pebbles = listOf(Triple(150.0, 14.0, 8.0), Triple(340.0, 28.0, 10.0), Triple(520.0, 18.0, 7.0),
        Triple(700.0, 30.0, 9.0), Triple(810.0, 12.0, 6.0), Triple(40.0, 26.0, 7.0))
    private val nearTufts = listOf(30.0 to 16.0, 300.0 to 20.0, 408.0 to 14.0, 610.0 to 18.0, 822.0 to 22.0)

    private fun drawPropTile(c: Canvas, x: Double, g: Double) {
        val base = g + 4
        for ((px, py, w) in pebbles) {
            c.ellipse(x + px, g + py, w, w * 0.5, solid(argb(0xE6CB9C)))
            c.ellipse(x + px - w * 0.12, g + py - w * 0.1, w * 0.5, w * 0.2, solid(argb(0xFFFFFF, 0.6)))
        }
        drawBench(c, x + 70, base)
        drawSign(c, x + 470, base)
        drawPlanter(c, x + 660, base)
        for ((tx, h) in nearTufts) tuft(c, x + tx, g + 3, h)
    }

    private fun drawBench(c: Canvas, x: Double, base: Double) {
        softShadow(c, x + 80, base, 176.0, 12.0, 0.16)
        for (lx in listOf(22.0, 132.0)) c.round(x + lx + 8, base - 30, 7.0, 24.0, 2.0, solid(argb(0xE2C390)))
        for (sx in listOf(22.0, 134.0)) {
            c.round(x + sx, base - 70, 7.0, 44.0, 3.0, shaded(lin(x + sx, 0.0, x + sx + 7, 0.0, intArrayOf(argb(0xDDB57A), argb(0xC59A60)))))
        }
        for ((sy, h) in listOf(68.0 to 13.0, 50.0 to 13.0)) {
            val top = base - sy
            c.round(x + 4, top, 152.0, h, 5.0, shaded(lin(0.0, top, 0.0, top + h, intArrayOf(argb(0xF8E2B6), argb(0xE9C88E)))))
            c.round(x + 8, top + 1.5, 144.0, 2.0, 1.0, solid(argb(0xFFFFFF, 0.45)))
        }
        c.round(x, base - 34, 160.0, 12.0, 5.0, shaded(lin(0.0, base - 34, 0.0, base - 22, intArrayOf(argb(0xFBE7BF), argb(0xEFD09A)))))
        c.round(x + 2, base - 24, 156.0, 6.0, 3.0, solid(argb(0xD9B479)))
        for (lx in listOf(10.0, 140.0)) {
            c.round(x + lx, base - 22, 9.0, 22.0, 2.5, shaded(lin(x + lx, 0.0, x + lx + 9, 0.0, intArrayOf(argb(0xDDB57A), argb(0xC59A60)))))
        }
    }

    private fun drawSign(c: Canvas, x: Double, base: Double) {
        softShadow(c, x, base, 34.0, 8.0, 0.18)
        c.round(x - 3.5, base - 128, 7.0, 128.0, 3.0, shaded(lin(x - 3.5, 0.0, x + 3.5, 0.0, intArrayOf(argb(0xF1F4F7), argb(0xC5CED8)))))
        val cy = base - 150
        c.ellipse(x + 1.5, cy + 2.5, 56.0, 56.0, solid(argb(0x2F6FB0, 0.18)))
        c.ellipse(x, cy, 56.0, 56.0, solid(argb(0xFFFFFF)))
        c.ellipse(x, cy, 48.0, 48.0, shaded(rad(x - 8, cy - 10, 32.0, intArrayOf(argb(0x7CC2F7), argb(0x3E95E6)))))
        c.ellipse(x - 1, cy - 13.5, 7.0, 7.0, solid(argb(0xFFFFFF)))
        val limbs = Path().apply {
            mv(x - 1, cy - 7); ln(x + 1, cy + 4)
            mv(x + 1, cy + 4); ln(x - 5, cy + 13)
            mv(x + 1, cy + 4); ln(x + 7, cy + 12)
            mv(x - 7, cy + 1); ln(x, cy - 4); ln(x + 7, cy - 1)
        }
        c.drawPath(limbs, line(argb(0xFFFFFF), 3.2))
        c.drawArc(oval(x, cy, 38.0, 38.0), 200f, 50f, false, line(argb(0xFFFFFF, 0.45), 3.0))
    }

    private val flowers = listOf(
        doubleArrayOf(24.0, 48.0) to (0xFF9FB8 to 0xFFE38A), doubleArrayOf(52.0, 58.0) to (0xFFD25C to 0xFF9A4D),
        doubleArrayOf(78.0, 47.0) to (0xFF9FB8 to 0xFFE38A), doubleArrayOf(40.0, 40.0) to (0xFFFFFF to 0xFFD25C),
        doubleArrayOf(64.0, 42.0) to (0xFF9FB8 to 0xFFE38A),
    )

    private fun drawPlanter(c: Canvas, x: Double, base: Double) {
        softShadow(c, x + 50, base, 118.0, 10.0, 0.16)
        blob(c, x + 22, base - 36, 15.0, 0xB9E794, 0x8FD06E, 0x68B44E)
        blob(c, x + 78, base - 36, 15.0, 0xB9E794, 0x8FD06E, 0x68B44E)
        blob(c, x + 50, base - 42, 19.0, 0xB9E794, 0x8FD06E, 0x68B44E)
        for ((pos, col) in flowers) {
            val fx = pos[0]
            val fy = pos[1]
            for (k in 0 until 5) {
                val a = k * 2 * PI / 5
                c.ellipse(x + fx + 4 * cos(a), base - fy + 4 * sin(a), 5.5, 5.5, solid(argb(col.first)))
            }
            c.ellipse(x + fx, base - fy, 4.0, 4.0, solid(argb(col.second)))
        }
        c.round(x, base - 30, 100.0, 30.0, 7.0, shaded(lin(0.0, base - 30, 0.0, base, intArrayOf(argb(0xF1AE86), argb(0xD9825C)))))
        c.round(x - 3, base - 33, 106.0, 9.0, 4.5, shaded(lin(0.0, base - 33, 0.0, base - 24, intArrayOf(argb(0xF8C2A0), argb(0xE79A73)))))
    }

    // MARK: Bao

    private val bun: Path = Path().apply {
        mv(0.0, 0.0)
        cb(-58.0, 0.0, -76.0, -18.0, -75.0, -52.0)
        cb(-74.0, -102.0, -42.0, -136.0, 0.0, -136.0)
        cb(42.0, -136.0, 74.0, -102.0, 75.0, -52.0)
        cb(76.0, -18.0, 58.0, 0.0, 0.0, 0.0)
        close()
    }

    private fun drawFoot(c: Canvas, x: Double, y: Double, angle: Double, far: Boolean) = c.layer {
        translate(x.f, y.f)
        rotate(angle.f)
        val cols = if (far) intArrayOf(argb(0xF3E6D2), argb(0xD6BF9F)) else intArrayOf(argb(0xFFF8EE), argb(0xE6D2B6))
        ellipse(0.0, 0.0, 38.0, 20.0, shaded(lin(0.0, -10.0, 0.0, 10.0, cols)))
        ellipse(-4.0, -5.0, 18.0, 5.0, solid(argb(0xFFFFFF, if (far) 0.25 else 0.55)))
    }

    private fun drawArm(c: Canvas, px: Double, py: Double, angle: Double, far: Boolean) = c.layer {
        translate(px.f, py.f)
        rotate(angle.f)
        val cols = if (far) intArrayOf(argb(0xF1E3CD), argb(0xD9C3A3)) else intArrayOf(argb(0xFFFCF6), argb(0xE8D6BC))
        round(-9.5, -6.0, 19.0, 34.0, 9.5, shaded(lin(-9.5, 0.0, 9.5, 0.0, cols)))
        round(-9.5, -6.0, 19.0, 34.0, 9.5, line(argb(0xDCC6A6, 0.8), 1.2))
        if (!far) ellipse(-3.5, 6.0, 5.0, 14.0, solid(argb(0xFFFFFF, 0.6)))
    }

    private fun drawStrap(c: Canvas, hx: Double, hy: Double, angle: Double) = c.layer {
        translate(hx.f, hy.f)
        rotate(angle.f)
        scale(0.84f, 0.84f)
        drawPath(Path().apply { mv(-13.0, 18.0); cb(-13.0, -8.0, 13.0, -8.0, 13.0, 18.0) }, line(argb(0xE2911F), 4.4))
        drawPath(Path().apply { mv(-9.0, 4.0); qd(-8.0, -3.0, -2.0, -4.0) }, line(argb(0xFFD27A), 1.6))
    }

    private fun drawBag(c: Canvas, hx: Double, hy: Double, angle: Double) = c.layer {
        translate(hx.f, hy.f)
        rotate(angle.f)
        scale(0.84f, 0.84f)
        layer {
            translate(10f, 10f)
            rotate(24f)
            round(-7.0, -26.0, 14.0, 46.0, 7.0, shaded(lin(-7.0, 0.0, 7.0, 0.0, intArrayOf(argb(0xF3C784), argb(0xC98B45)))))
            for (sy in listOf(-19.0, -11.0, -3.0)) {
                drawLine(-3.5f, (sy + 3).f, 3.5f, sy.f, line(argb(0xFBE3B8), 2.0))
            }
        }
        val body = Path().apply {
            mv(-22.0, 16.0); ln(22.0, 16.0)
            qd(25.0, 18.0, 26.0, 50.0); qd(27.0, 58.0, 18.0, 58.0)
            ln(-18.0, 58.0)
            qd(-27.0, 58.0, -26.0, 50.0); qd(-25.0, 18.0, -22.0, 16.0)
            close()
        }
        drawPath(body, shaded(lin(0.0, 16.0, 0.0, 58.0, intArrayOf(argb(0xFFD272), argb(0xF6B23E), argb(0xE69523)))))
        round(-16.0, 21.0, 5.0, 30.0, 2.5, solid(argb(0xFFFFFF, 0.35)))
        round(-22.0, 15.0, 44.0, 5.0, 2.5, solid(argb(0xFFE29A)))
        val heart = Path().apply {
            mv(6.0, 42.0)
            cb(-2.0, 37.0, 1.0, 29.0, 6.0, 32.0)
            cb(11.0, 29.0, 14.0, 37.0, 6.0, 42.0)
        }
        drawPath(heart, solid(argb(0xFF7A59)))
    }

    private val pleats: Path = Path().apply {
        for (a in listOf(-70.0, -35.0, 0.0, 35.0, 70.0)) {
            val r = a * PI / 180
            val r2 = r + 0.35
            mv(7 * sin(r), -129 + 4 * cos(r))
            qd(18 * sin(r), -129 + 13 * cos(r), 26 * sin(r2), -129 + 17 * cos(r2))
        }
    }

    private val twist: Path = Path().apply {
        mv(-12.0, 2.0)
        cb(-14.0, -10.0, -6.0, -18.0, 2.0, -20.0)
        cb(8.0, -22.0, 11.0, -16.0, 9.0, -12.0)
        cb(14.0, -8.0, 15.0, -2.0, 12.0, 2.0)
        close()
    }

    fun drawBao(c: Canvas, x: Double, ground: Double, p: WalkingStepsBuddyMotion.Pose, happy: Boolean) {
        val air = p.lift
        softShadow(c, x, ground, 128 * (1 - air / 110) - 10 * (-p.bob / 7), 15.0, 0.24 * (1 - air / 80))
        c.layer {
            translate(x.f, (ground - air).f)
            drawFoot(this, 14 + p.far.x, -9 - p.far.lift, p.far.angle, true)
            drawFoot(this, -12 + p.near.x, -9 - p.near.lift, p.near.angle, false)
        }
        c.layer {
            translate(x.f, (ground - 15 + p.bob - air).f)
            rotate(p.lean.f)
            scale(p.sx.f, p.sy.f)
            drawArm(this, 66.0, -72.0, p.arm, true)
            // The near arm comes out from behind the body and holds the tote away from it.
            val pivotX = -62.0
            val pivotY = -68.0
            val armAngle = 42 - 0.8 * p.arm
            val a = armAngle * PI / 180
            val hx = pivotX - 26 * sin(a)
            val hy = pivotY + 26 * cos(a)
            val bagAngle = -p.arm * 0.35 + p.bag
            drawArm(this, pivotX, pivotY, armAngle, false)
            drawPath(bun, shaded(rad(-22.0, -98.0, 150.0,
                intArrayOf(argb(0xFFFFFF), argb(0xFFF8EE), argb(0xE6D3B8)), floatArrayOf(0f, 0.45f, 1f))))
            layer {
                clipPath(bun)
                drawRect(box(-80.0, -40.0, 160.0, 42.0), shaded(lin(0.0, -40.0, 0.0, 0.0, intArrayOf(argb(0xD8C2A2, 0.0), argb(0xD8C2A2, 0.45)))))
                ellipse(-30.0, -104.0, 46.0, 26.0, shaded(rad(-30.0, -104.0, 24.0, intArrayOf(argb(0xFFFFFF, 0.9), argb(0xFFFFFF, 0.0)))))
            }
            drawPath(pleats, line(argb(0xE8D6BC), 2.0))
            layer {
                translate(0f, -130f)
                rotate(p.knot.f)
                drawPath(twist, shaded(rad(-4.0, -12.0, 20.0, intArrayOf(argb(0xFFFFFF), argb(0xFFF6EA), argb(0xE4CFB0)))))
                drawPath(Path().apply { mv(-4.0, -4.0); qd(-3.0, -14.0, 5.0, -14.0) }, line(argb(0xE3CFB2), 2.0))
            }
            for (cx in listOf(-4.0, 58.0)) {
                ellipse(cx, -54.0, 20.0, 12.0, shaded(rad(cx, -54.0, 11.0, intArrayOf(argb(0xFF9C87, 0.7), argb(0xFF9C87, 0.0)))))
            }
            for (ex in listOf(12.0, 44.0)) {
                if (happy) {
                    drawPath(Path().apply { mv(ex - 6, -66.0); qd(ex, -77.0, ex + 6, -66.0) }, line(argb(0x2A2420), 3.2))
                } else {
                    ellipse(ex, -69.0, 10.5, 14.0, solid(argb(0x2A2420)))
                    ellipse(ex - 1.8, -72.5, 3.6, 4.2, solid(argb(0xFFFFFF)))
                }
            }
            if (happy) {
                drawPath(Path().apply { mv(21.0, -58.0); qd(28.0, -45.0, 35.0, -58.0); close() }, solid(argb(0x9A4232)))
            } else {
                drawPath(Path().apply { mv(21.0, -58.0); qd(24.5, -53.0, 28.0, -57.0); qd(31.5, -53.0, 35.0, -58.0) }, line(argb(0x2A2420), 2.2))
            }
            drawBag(this, hx, hy, bagAngle)
            drawStrap(this, hx, hy, bagAngle)
        }
    }

    // MARK: Bar

    fun drawBar(c: Canvas, t: Double, steps: Double, top: Double) {
        val cy = top + 6
        c.round(20.0, top, 353.0, 12.0, 6.0, solid(argb(0xEDF0F4)))
        val end = max(34.0, m.barX(steps))
        c.round(20.0, top, end - 20, 12.0, 6.0, shaded(lin(20.0, 0.0, end, 0.0, intArrayOf(argb(ORANGE_LIGHT), argb(ORANGE)))))
        c.round(24.0, top + 2, max(0.0, end - 30), 3.0, 1.5, solid(argb(0xFFFFFF, 0.35)))
        for (i in 0 until 4) {
            val x = m.tickX[i]
            val pop = m.tickPop(i, t)
            if (pop > 0.02) {
                val r = 3.5 + 6 * pop
                c.ellipse(x, cy + 1.5, 2 * r + 2, 2 * r + 2, solid(argb(0xC9471A, 0.25)))
                c.ellipse(x, cy, 2 * r + 4, 2 * r + 4, solid(argb(0xFFFFFF)))
                c.ellipse(x, cy, 2 * r, 2 * r, shaded(lin(x, cy - r, x, cy + r, intArrayOf(argb(ORANGE_LIGHT), argb(ORANGE)))))
                val s = r / 9.5
                val check = Path().apply { mv(x - 4 * s, cy + 0.2 * s); ln(x - 1 * s, cy + 3.2 * s); ln(x + 4.5 * s, cy - 3 * s) }
                c.drawPath(check, line(argb(0xFFFFFF), 2.2 * s))
            } else {
                c.ellipse(x, cy, 6.0, 6.0, solid(argb(0xFFFFFF)))
            }
        }
        c.ellipse(end - 6, cy, 6.0, 6.0, solid(argb(0xFFFFFF)))
    }

    // MARK: Celebration

    fun drawCelebration(c: Canvas, t: Double, barCentre: Double) {
        val x0 = m.tickX[0]
        for (cf in m.confetti(t)) {
            c.layer {
                translate((x0 + cf.x).f, (barCentre + cf.y).f)
                rotate((cf.angle * 180 / PI).f)
                if (cf.kind == 0) ellipse(0.0, 0.0, 6.0, 6.0, solid(argb(cf.color, cf.alpha)))
                else round(-4.0, -2.0, 8.0, 4.0, 1.0, solid(argb(cf.color, cf.alpha)))
            }
        }
        val b = m.bubble(t)
        if (b.alpha > 0.01) {
            val n = c.saveLayerAlpha(null, (b.alpha * 255).roundToInt())
            c.translate((x0 + 22).f, (barCentre + b.y).f)
            c.scale(b.scale.f, b.scale.f)
            c.round(-42.0, -11.0, 84.0, 26.0, 13.0, solid(argb(0xC9471A, 0.25)))
            c.round(-42.0, -13.0, 84.0, 26.0, 13.0, shaded(lin(0.0, -13.0, 0.0, 13.0, intArrayOf(argb(ORANGE_LIGHT), argb(ORANGE)))))
            c.drawPath(Path().apply { mv(-28.0, 12.0); ln(-22.0, 19.0); ln(-16.0, 12.0); close() }, solid(argb(ORANGE)))
            drawCoin(c, -27.0, 0.0, 7.0, 1.0)
            textPaint.textSize = 12f
            c.drawText("+10 coins", 6f, 4.3f, textPaint)
            c.restoreToCount(n)
        }
    }

    // MARK: Icons (each drawn in its own small box, in points)

    fun drawCoin(c: Canvas, cx: Double, cy: Double, r: Double, mixF: Double) {
        c.ellipse(cx, cy, 2 * r, 2 * r, shaded(lin(cx, cy - r, cx, cy + r,
            intArrayOf(mix(0xD9DEE4, 0xFFD76A, mixF), mix(0xBEC6CF, 0xF0A12E, mixF)))))
        c.ellipse(cx, cy, 1.25 * r, 1.25 * r, line(mix(0xEEF1F4, 0xFFEBA8, mixF), max(1.0, r * 0.22)))
    }

    fun drawShoe(c: Canvas) {
        softShadow(c, 18.0, 25.0, 32.0, 5.0, 0.25, 0xC9471A)
        val upper = Path().apply {
            mv(3.0, 19.0); cb(2.0, 10.0, 7.0, 5.0, 13.0, 5.0); ln(18.0, 6.0)
            cb(24.0, 10.0, 33.0, 11.0, 33.0, 17.0); ln(33.0, 19.0); close()
        }
        c.drawPath(upper, shaded(lin(0.0, 5.0, 0.0, 19.0, intArrayOf(argb(0xFFB067), argb(0xFF6A1F)))))
        c.ellipse(8.0, 8.0, 8.0, 5.0, solid(argb(0xD9531A)))
        c.drawPath(Path().apply { mv(15.0, 8.5); ln(18.5, 11.5); mv(18.5, 7.5); ln(22.0, 10.5) }, line(argb(0xFFFFFF), 1.8))
        c.round(1.5, 17.5, 33.0, 6.5, 3.2, shaded(lin(0.0, 17.5, 0.0, 24.0, intArrayOf(argb(0xFFFFFF), argb(0xF0D8C4)))))
    }

    fun drawCalendar(c: Canvas) {
        softShadow(c, 22.0, 41.0, 34.0, 5.0, 0.18, 0x3A4A5C)
        c.round(5.0, 8.0, 34.0, 31.0, 8.0, shaded(lin(0.0, 8.0, 0.0, 39.0, intArrayOf(argb(0xFFFFFF), argb(0xE5EBF2)))))
        c.layer {
            clipPath(Path().apply { addRoundRect(box(5.0, 8.0, 34.0, 31.0), 8f, 8f, Path.Direction.CW) })
            drawRect(box(5.0, 8.0, 34.0, 10.0), shaded(lin(0.0, 8.0, 0.0, 18.0, intArrayOf(argb(0xFF8C6B), argb(0xF25F3C)))))
        }
        for (rx in listOf(13.0, 27.0)) c.round(rx, 4.0, 4.0, 9.0, 2.0, solid(argb(0x5A6676)))
        c.drawPath(Path().apply { mv(15.0, 28.0); ln(20.0, 33.0); ln(29.0, 23.0) }, line(argb(0x37B26C), 3.6))
    }

    fun drawGift(c: Canvas) {
        softShadow(c, 22.0, 41.0, 36.0, 5.0, 0.18, 0x7A2E1A)
        c.round(7.0, 20.0, 30.0, 20.0, 4.0, shaded(lin(0.0, 20.0, 0.0, 40.0, intArrayOf(argb(0xFF8A65), argb(0xE8552F)))))
        c.round(4.0, 13.0, 36.0, 10.0, 3.5, shaded(lin(0.0, 13.0, 0.0, 23.0, intArrayOf(argb(0xFFA486), argb(0xF46C47)))))
        c.drawRect(box(19.0, 13.0, 6.0, 27.0), shaded(lin(0.0, 13.0, 0.0, 40.0, intArrayOf(argb(0xFFE08A), argb(0xF5B93A)))))
        for ((lx, rot) in listOf(15.0 to -28.0, 29.0 to 28.0)) {
            c.layer {
                translate(lx.f, 9f)
                rotate(rot.f)
                ellipse(0.0, 0.0, 12.0, 8.0, solid(argb(0xFFD15C)))
                ellipse(0.0, 0.0, 5.0, 3.0, solid(argb(0xE9A72A)))
            }
        }
        c.ellipse(22.0, 11.0, 6.0, 6.0, solid(argb(0xF5B93A)))
        c.round(9.0, 25.0, 3.0, 10.0, 1.5, solid(argb(0xFFFFFF, 0.35)))
    }

    fun drawPlane(c: Canvas) {
        softShadow(c, 24.0, 41.0, 30.0, 5.0, 0.16, 0x1E4F86)
        c.drawPath(Path().apply { mv(40.0, 6.0); ln(5.0, 21.0); ln(17.0, 25.0); close() },
            shaded(lin(5.0, 6.0, 40.0, 25.0, intArrayOf(argb(0x8FD0FA), argb(0x4AA3EC)))))
        c.drawPath(Path().apply { mv(40.0, 6.0); ln(17.0, 25.0); ln(29.0, 36.0); close() },
            shaded(lin(17.0, 6.0, 40.0, 36.0, intArrayOf(argb(0x5DB4F2), argb(0x2F82D6)))))
        c.drawPath(Path().apply { mv(40.0, 6.0); ln(17.0, 25.0); ln(19.0, 34.0); close() }, solid(argb(0x2369B8)))
        c.drawPath(Path().apply { mv(3.0, 31.0); ln(10.0, 29.0); mv(6.0, 38.0); ln(15.0, 35.0) }, line(argb(0x9CCBF2), 2.2))
    }

    fun drawChevron(c: Canvas) {
        c.drawPath(Path().apply { mv(9.0, 2.0); ln(2.0, 9.0); ln(9.0, 16.0) }, line(argb(INK), 2.4))
    }

    fun drawShare(c: Canvas) {
        val p = Path().apply {
            mv(7.0, 8.0); ln(4.0, 8.0); ln(4.0, 20.0); ln(18.0, 20.0); ln(18.0, 8.0); ln(15.0, 8.0)
            mv(11.0, 2.0); ln(11.0, 13.0)
            mv(7.0, 5.5); ln(11.0, 1.5); ln(15.0, 5.5)
        }
        c.drawPath(p, line(argb(INK), 2.0))
    }

    fun drawGear(c: Canvas) {
        val p = Path()
        for (i in 0 until 8) {
            val a = i * PI / 4
            val pts = listOf(7.6 to a - 0.25, 10.0 to a - 0.15, 10.0 to a + 0.15, 7.6 to a + 0.25)
            for ((j, q) in pts.withIndex()) {
                val px = 11 + q.first * cos(q.second)
                val py = 11 + q.first * sin(q.second)
                if (i == 0 && j == 0) p.mv(px, py) else p.ln(px, py)
            }
        }
        p.close()
        c.drawPath(p, line(argb(INK), 1.9))
        c.ellipse(11.0, 11.0, 6.0, 6.0, line(argb(INK), 1.9))
    }
}
