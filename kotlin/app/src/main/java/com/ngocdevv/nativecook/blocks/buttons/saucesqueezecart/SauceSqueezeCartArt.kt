package com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Sauce Squeeze Cart — everything drawn in code (a port of SauceSqueezeCartArt.swift): the squeeze
 bottle (a pillowy body with three bulges per side, a two-tier cap, a label with a tomato), the
 sauce stream, the red flood and the stickers. The bottle is drawn in its own coordinates: origin at
 the body's centre, cap pointing down (+y). Units are dp (the caller scales the canvas by density).
*/

internal const val SSC_PINK = 0xF792DCL
internal const val SSC_SAUCE = 0xE90002L
internal const val SSC_PLUM = 0x3C063EL
internal const val SSC_MUTED_PLUM = 0xA0528EL

internal fun sscHex(hex: Long, alpha: Float = 1f) = Color(
    ((hex shr 16) and 0xFF).toInt() / 255f, ((hex shr 8) and 0xFF).toInt() / 255f, (hex and 0xFF).toInt() / 255f, alpha,
)

private fun Double.f() = toFloat()

internal object SscArt {
    private val m = SscMotion

    const val HALF_W = 86.0
    const val HALF_H = 114.0
    /** The nozzle's tip below the body's centre. */
    const val NOZZLE_Y = HALF_H + 38

    /** Blurred fills and strokes (SwiftUI's blur radius × 1.5 as Android's blur radius). */
    private val blurPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    fun DrawScope.blurred(path: Path, color: Color, radius: Double, stroke: Double = 0.0, dx: Double = 0.0, dy: Double = 0.0) {
        blurPaint.reset()
        blurPaint.isAntiAlias = true
        blurPaint.color = color.toArgb()
        blurPaint.maskFilter = BlurMaskFilter((radius * 1.5).f().coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        if (stroke > 0) {
            blurPaint.style = android.graphics.Paint.Style.STROKE
            blurPaint.strokeWidth = stroke.f()
            blurPaint.strokeCap = android.graphics.Paint.Cap.ROUND
            blurPaint.strokeJoin = android.graphics.Paint.Join.ROUND
        } else {
            blurPaint.style = android.graphics.Paint.Style.FILL
        }
        drawIntoCanvas {
            val c = it.nativeCanvas
            c.save()
            c.translate(dx.f(), dy.f())
            c.drawPath(path.asAndroidPath(), blurPaint)
            c.restore()
        }
    }

    fun circle(x: Double, y: Double, r: Double) = Path().apply {
        addOval(Rect(Offset(x.f(), y.f()), r.f()))
    }

    fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double) = Path().apply {
        addRoundRect(RoundRect(x.f(), y.f(), (x + w).f(), (y + h).f(), CornerRadius(r.f(), r.f())))
    }

    /** A closed path through points, smoothed with quadratic curves through their midpoints. */
    fun smoothPath(xs: DoubleArray, ys: DoubleArray): Path {
        val n = xs.size
        val path = Path()
        path.moveTo(((xs[n - 1] + xs[0]) / 2).f(), ((ys[n - 1] + ys[0]) / 2).f())
        for (i in 0 until n) {
            val j = (i + 1) % n
            path.quadraticTo(xs[i].f(), ys[i].f(), ((xs[i] + xs[j]) / 2).f(), ((ys[i] + ys[j]) / 2).f())
        }
        path.close()
        return path
    }

    // Bottle

    /** Width and height scale of a squash q (anchored at the cap), and the waist's pinch. */
    fun squash(q: Double) = Triple(1 + 0.18 * q, 1 - 0.14 * q, 0.12 * max(0.0, q))

    /** The body's outline for a squash q. */
    fun bodyPath(q: Double): Path {
        val a = HALF_W
        val b = HALF_H
        val (sx, sy, pinch) = squash(q)
        val n = 120
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        val e = 2 / 4.2
        for (i in 0 until n) {
            val th = 2 * PI * i / n
            val c = cos(th)
            val s = sin(th)
            var x = a * (if (c < 0) -1 else 1) * abs(c).pow(e)
            var y = b * (if (s < 0) -1 else 1) * abs(s).pow(e)
            // Three bulges down each side.
            val v = (y + b) / (2 * b)
            val side = m.smooth(0.55, 0.92, abs(x) / a)
            val env = m.smooth(0.06, 0.2, v) * (1 - m.smooth(0.8, 0.95, v))
            val sw = sin(3 * PI * v)
            x += (if (x < 0) -1 else 1) * (9 * sw * sw - 3.5) * side * env
            // A pillowy top, a shoulder narrowing into the cap.
            if (y < 0) y -= 7 * max(0.0, 1 - (x / a) * (x / a)) * m.smooth(0.6, 0.95, -y / b)
            if (y > 0) x *= 1 - 0.08 * m.smooth(0.7, 1.0, y / b)
            // Squeeze: wider and shorter, the waist pinched by the fingers, the top swelling.
            val waist = 1 - pinch * exp(-(y / (0.42 * b)).pow(2))
            val swell = 1 + 0.4 * pinch * exp(-((y + 0.7 * b) / (0.3 * b)).pow(2))
            xs[i] = x * sx * waist * swell
            ys[i] = b + (y - b) * sy
        }
        return smoothPath(xs, ys)
    }

    private fun shifted(p: Path, dx: Double, dy: Double) = Path().apply { addPath(p, Offset(dx.f(), dy.f())) }

    /** The whole bottle, in its own coordinates (the caller translates and rotates the canvas). */
    fun DrawScope.bottle(f: SscFrame, tx: SscTexts) {
        val b = HALF_H
        val (sx, sy, pinch) = squash(f.q)
        val body = bodyPath(f.q)

        // Soft shadow on the page.
        blurred(body, sscHex(0x9A1E6E, 0.22f), 18.0, dx = 8.0, dy = 22.0)

        // Cap: a white collar and a smaller tip, tucked under the body.
        val cw = 1 + 0.06 * f.q
        val capShade = Brush.linearGradient(
            0f to sscHex(0xFFFFFF), 0.5f to sscHex(0xF3EEF1), 1f to sscHex(0xCFC4CB),
            start = Offset(-56f, 0f), end = Offset(56f, 0f),
        )
        drawPath(roundRect(-38 * cw, b + 16, 76 * cw, 22.0, 8.0), capShade)
        drawPath(roundRect(-38 * cw, b + 33, 76 * cw, 5.0, 2.5), sscHex(0xBDB1B9))
        drawPath(roundRect(-56 * cw, b - 10, 112 * cw, 30.0, 10.0), capShade)
        drawPath(roundRect(-56 * cw, b + 14, 112 * cw, 6.0, 3.0), sscHex(0xD8CED5))
        drawOval(sscHex(0x9E2030), Offset(-6f, (b + 35.5).f()), Size(12f, 3f))

        // Body: a red gradient across, darker towards the cap.
        drawPath(
            body,
            Brush.linearGradient(
                0f to sscHex(0xF3302E), 0.45f to sscHex(0xD80C12), 1f to sscHex(0xA30008),
                start = Offset(-HALF_W.f(), 0f), end = Offset(HALF_W.f(), 0f),
            ),
        )
        clipPath(body) {
            drawPath(
                body,
                Brush.linearGradient(
                    listOf(sscHex(0x7A0006, 0f), sscHex(0x7A0006, 0.32f)),
                    start = Offset(0f, 0f), end = Offset(0f, b.f()),
                ),
            )
            // Inner shading: dark along the right and bottom, light along the top left.
            blurred(body, sscHex(0x6E0005, 0.55f), 7.0, stroke = 12.0, dx = -9.0, dy = -7.0)
            blurred(body, sscHex(0xFF8A7A, 0.35f), 5.0, stroke = 6.0, dx = 6.0, dy = 7.0)

            // Label: scaled with the waist so it squeezes with the body.
            withTransform({
                translate(0f, b.f())
                scale((sx * (1 - pinch * 0.9)).f(), sy.f(), Offset.Zero)
                translate(0f, -b.f())
            }) { label(tx) }

            // Specular streaks that slide a little as the bottle turns.
            val hx = -HALF_W * 0.66 + f.spin * 0.25
            withTransform({
                translate(0f, b.f())
                scale(sx.f(), sy.f(), Offset.Zero)
                translate(0f, -b.f())
            }) {
                blurred(roundRect(hx - 6, -b * 0.62, 12.0, b * 1.04, 6.0), Color.White.copy(alpha = 0.5f), 3.0)
                blurred(roundRect(hx + 13, -b * 0.5, 4.0, b * 0.55, 2.0), Color.White.copy(alpha = 0.28f), 3.0)
                blurred(
                    Path().apply { addOval(Rect((-HALF_W * 0.2 - 36 + f.spin * 0.3).f(), (-b - 2).f(), (-HALF_W * 0.2 + 36 + f.spin * 0.3).f(), (-b + 16).f())) },
                    Color.White.copy(alpha = 0.38f), 6.0,
                )
            }
        }
    }

    private fun DrawScope.label(tx: SscTexts) {
        drawPath(roundRect(-52.0, -74.0, 104.0, 140.0, 18.0), sscHex(0xE8553A))
        drawPath(roundRect(-42.0, -64.0, 84.0, 120.0, 12.0), sscHex(0xF6B4B0, 0.9f))
        tomato(0.0, -14.0)
        text(tx.brand, 0.0, 36.0)
    }

    private fun DrawScope.tomato(x: Double, y: Double) {
        withTransform({ translate(x.f(), y.f()) }) {
            val lobes = Path().apply {
                addOval(Rect(-25f, -16f, 5f, 18f))
                addOval(Rect(-5f, -16f, 25f, 18f))
                addOval(Rect(-16f, -18f, 16f, 19f))
            }
            drawPath(
                lobes,
                Brush.radialGradient(
                    listOf(sscHex(0xF2473A), sscHex(0xE3201B), sscHex(0xB5100E)),
                    center = Offset(-6f, -6f), radius = 30f,
                ),
            )
            val crease = Stroke(1.4f)
            drawPath(Path().apply { moveTo(-5f, -12f); quadraticTo(-10f, 0f, -6f, 14f) }, sscHex(0xB5100E, 0.6f), style = crease)
            drawPath(Path().apply { moveTo(6f, -12f); quadraticTo(11f, 0f, 7f, 14f) }, sscHex(0xB5100E, 0.6f), style = crease)
            drawOval(Color.White.copy(alpha = 0.45f), Offset(-15f, -11f), Size(9f, 6f))
            // Calyx: a five-point green star with a stem.
            val star = Path()
            for (i in 0 until 10) {
                val a = -PI / 2 + i * PI / 5
                val r = if (i % 2 == 0) 12.0 else 4.0
                val px = (r * cos(a)).f()
                val py = (-16 + r * sin(a) * 0.55).f()
                if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
            }
            star.close()
            drawPath(star, sscHex(0x2E8B3A))
            drawPath(roundRect(-1.5, -24.0, 3.0, 8.0, 1.5), sscHex(0x2E8B3A))
        }
    }

    /** The bead of sauce swelling at the nozzle while squeezed. */
    fun DrawScope.bead(s: Double) {
        val r = 7 * s
        if (r <= 0.3) return
        val cy = NOZZLE_Y + r * 0.7
        drawPath(circle(0.0, cy, r), sscHex(SSC_SAUCE))
        drawPath(circle(-r * 0.35, cy - r * 0.3, r * 0.28), Color.White.copy(alpha = 0.5f))
    }

    // Stream, drops and the red

    /** The ribbon of sauce from the nozzle (screen coordinates). */
    fun DrawScope.stream(rootX: Double, rootY: Double, tau: Double, h: Double) {
        val head = m.streamHead(tau)
        val width = m.streamWidth(tau)
        if (head <= 0.001 || width <= 0.5) return
        val sway = m.streamSway(tau)
        val len = (h + 60 - rootY) * head
        val n = 48
        val cx = DoubleArray(n + 1)
        val cy = DoubleArray(n + 1)
        for (k in 0..n) {
            val u = k.toDouble() / n
            val dy = len * u
            cx[k] = rootX + sway * u.pow(0.8) * sin(2 * PI * (dy / 300 - 1.6 * tau))
            cy[k] = rootY + dy
        }
        fun halfWidth(u: Double) = width / 2 * (0.55 + 0.45 * m.smooth(0.0, 0.25, u))
        val lx = DoubleArray(n + 1); val ly = DoubleArray(n + 1)
        val rx = DoubleArray(n + 1); val ry = DoubleArray(n + 1)
        for (k in 0..n) {
            val a = max(0, k - 1)
            val bb = min(n, k + 1)
            var nx = -(cy[bb] - cy[a])
            var ny = cx[bb] - cx[a]
            val l = max(0.0001, sqrt(nx * nx + ny * ny))
            nx /= l; ny /= l
            val hw = halfWidth(k.toDouble() / n)
            lx[k] = cx[k] + nx * hw; ly[k] = cy[k] + ny * hw
            rx[k] = cx[k] - nx * hw; ry[k] = cy[k] - ny * hw
        }
        val p = Path()
        p.moveTo(lx[0].f(), ly[0].f())
        for (k in 1..n) p.lineTo(lx[k].f(), ly[k].f())
        // A round head.
        val ang = atan2(cy[n] - cy[n - 1], cx[n] - cx[n - 1])
        val r = halfWidth(1.0)
        p.arcTo(
            Rect(Offset(cx[n].f(), cy[n].f()), r.f()),
            Math.toDegrees(ang + PI / 2).f(), -180f, false,
        )
        for (k in n downTo 0) p.lineTo(rx[k].f(), ry[k].f())
        p.close()
        drawPath(p, sscHex(SSC_SAUCE))

        // Gloss along the left of the ribbon.
        val gloss = Path()
        val k0 = 3
        val k1 = (n * 0.9).toInt()
        for (k in k0..k1) {
            val gx = (lx[k] * 0.62 + cx[k] * 0.38).f()
            val gy = (ly[k] * 0.62 + cy[k] * 0.38).f()
            if (k == k0) gloss.moveTo(gx, gy) else gloss.lineTo(gx, gy)
        }
        blurred(gloss, sscHex(0xFF6A58, 0.6f), 1.5, stroke = max(2.0, width * 0.1))
    }

    /** Droplets thrown from the nozzle as the stream starts. */
    fun DrawScope.drops(rootX: Double, rootY: Double, tau: Double) {
        for (i in 0 until 6) {
            val di = i.toDouble()
            val t = tau - 0.02 * di
            if (t <= 0 || t >= 0.9) continue
            val vx = (m.hash(di + 1) - 0.5) * 520
            val vy = -160 + m.hash(di + 7) * 300
            val x = rootX + vx * t
            val y = rootY + vy * t + 0.5 * 1600 * t * t
            val r = (4 + 5 * m.hash(di + 13)) * (1 - m.smooth(0.6, 0.9, t))
            drawPath(circle(x, y, r), sscHex(SSC_SAUCE))
            drawPath(circle(x - r * 0.3, y - r * 0.3, r * 0.3), Color.White.copy(alpha = 0.45f))
        }
    }

    /** The red that floods up from the bottom and later retracts upwards, dripping. */
    fun DrawScope.red(tau: Double, w: Double, h: Double) {
        val flooding = tau < SscMotion.COVERED_AT + 0.1
        val draining = m.drain(tau) > 0
        if (tau <= 0.3 || m.drain(tau) >= 1) return
        val count = ((w + 40) / 3).toInt() + 1
        val xs = DoubleArray(count) { -20.0 + 3 * it }
        val top = DoubleArray(count) { if (flooding) m.redTop(xs[it], tau, h) else -400.0 }
        val bottom = DoubleArray(count) { if (draining) m.redBottom(xs[it], tau, w, h) else h + 400 }
        val p = Path()
        p.moveTo(xs[0].f(), top[0].f())
        for (i in 1 until count) p.lineTo(xs[i].f(), top[i].f())
        for (i in count - 1 downTo 0) p.lineTo(xs[i].f(), bottom[i].f())
        p.close()
        drawPath(p, sscHex(SSC_SAUCE))

        // A glossy line just inside the moving edge.
        if (!flooding && !draining) return
        val edge = if (flooding) top else bottom
        val dy = if (flooding) 7.0 else -7.0
        val line = Path()
        for (i in 0 until count) {
            if (i == 0) line.moveTo(xs[i].f(), (edge[i] + dy).f()) else line.lineTo(xs[i].f(), (edge[i] + dy).f())
        }
        clipPath(p) { blurred(line, sscHex(0xFF5444, 0.55f), 2.0, stroke = 3.0) }
    }

    // Stickers

    /** Positions relative to the middle of the red (x, y, rotation), drawn at 1.15 ×. */
    private val stickers = arrayOf(
        doubleArrayOf(-124.0, -118.0, 0.0),
        doubleArrayOf(112.0, -92.0, 12.0),
        doubleArrayOf(-12.0, -34.0, -10.0),
        doubleArrayOf(-74.0, 64.0, 16.0),
        doubleArrayOf(104.0, 52.0, -68.0),
        doubleArrayOf(14.0, 150.0, -45.0),
    )

    fun DrawScope.stickers(tau: Double, cx: Double, cy: Double, reduced: Boolean, tx: SscTexts) {
        for (i in stickers.indices) {
            val s = stickers[i]
            var k = m.sticker(i, tau)
            var alpha = 1f
            if (reduced) {
                alpha = m.redFade(tau).f()
                k = if (alpha > 0.001f) 1.0 else 0.0
            }
            if (k <= 0.01) continue
            val float = if (reduced) 0.0 else 3 * sin(2 * PI * (tau / 1.6) + i * 1.7)
            val rot = s[2] + if (reduced) 0.0 else 30 * (1 - min(1.0, k))
            withTransform({
                translate((cx + s[0]).f(), (cy + s[1] + float).f())
                rotate(rot.f(), Offset.Zero)
                scale((1.15 * k).f(), (1.15 * k).f(), Offset.Zero)
            }) {
                blurred(silhouette(i, tx), sscHex(0x5A0010, 0.25f * alpha), 10.0, dy = 6.0)
                sticker(i, tx, alpha)
            }
        }
    }

    private fun tagSize(tx: SscTexts) = Pair(tx.tag.size.width + 32.0, 42.0)
    private fun pillSize(tx: SscTexts) = Pair(tx.added.size.width + 40.0, 40.0)

    /** The outer shape of a sticker, for its shadow. */
    private fun silhouette(i: Int, tx: SscTexts): Path = when (i) {
        0 -> starPath()
        1 -> circle(0.0, 0.0, 38.0)
        2 -> tagSize(tx).let { (w, h) -> roundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 14.0) }
        3 -> circle(0.0, 0.0, 44.0)
        4 -> pillSize(tx).let { (w, h) -> roundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 24.0) }
        else -> roundRect(-32.0, -21.0, 62.0, 42.0, 14.0)
    }

    private fun starPath(): Path {
        val star = Path()
        for (j in 0 until 16) {
            val a = -PI / 2 + j * PI / 8
            val r = if (j % 2 == 0) 22.0 else 8.5
            if (j == 0) star.moveTo((r * cos(a)).f(), (r * sin(a)).f()) else star.lineTo((r * cos(a)).f(), (r * sin(a)).f())
        }
        star.close()
        return star
    }

    private fun DrawScope.sticker(i: Int, tx: SscTexts, alpha: Float) {
        val plum = sscHex(SSC_PLUM)
        val white = Color.White
        when (i) {
            0 -> drawPath(starPath(), white, alpha = alpha)
            1 -> {
                drawPath(circle(0.0, 0.0, 38.0), white, alpha = alpha)
                drawPath(circle(0.0, 0.0, 34.0), sscHex(0xF8D3EF), alpha = alpha)
                cart(0.0, 0.0, 1.15, plum, alpha)
            }
            2 -> {
                val (w, h) = tagSize(tx)
                drawPath(roundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 14.0), white, alpha = alpha)
                drawPath(
                    roundRect(-w / 2, -h / 2, w, h, 10.0),
                    Brush.linearGradient(
                        listOf(sscHex(0x9EC2FA), sscHex(0xD3E1FF)),
                        start = Offset((-w / 2).f(), 0f), end = Offset((w / 2).f(), 0f),
                    ),
                    alpha = alpha,
                )
                text(tx.tag, 0.0, 0.0, alpha)
            }
            3 -> {
                drawPath(circle(0.0, 0.0, 44.0), white, alpha = alpha)
                drawPath(
                    circle(0.0, 0.0, 40.0),
                    Brush.radialGradient(
                        0f to sscHex(0xFBF79A), 0.5f to sscHex(0xF0E54A), 1f to sscHex(0xD4A92A),
                        center = Offset(-10f, -12f), radius = 52f,
                    ),
                    alpha = alpha,
                )
                drawPath(roundRect(-15.5, -14.0, 5.0, 10.0, 2.5), plum, alpha = alpha)
                drawPath(roundRect(9.0, -15.0, 5.0, 10.0, 2.5), plum, alpha = alpha)
                drawArc(
                    plum, 25f, 130f, false, Offset(-22f, -26f), Size(44f, 44f),
                    alpha = alpha, style = Stroke(3.5f, cap = StrokeCap.Round),
                )
            }
            4 -> {
                val (w, h) = pillSize(tx)
                drawPath(roundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 24.0), white, alpha = alpha)
                drawPath(roundRect(-w / 2, -h / 2, w, h, 20.0), sscHex(0x22C32C), alpha = alpha)
                drawPath(circle(-w / 2 + 12, 0.0, 3.5), plum, alpha = alpha)
                text(tx.added, 5.0, 0.0, alpha)
            }
            else -> {
                val arrow = Path().apply {
                    moveTo(-24f, 0f); lineTo(20f, 0f)
                    moveTo(6f, -13f); lineTo(21f, 0f); lineTo(6f, 13f)
                }
                drawPath(arrow, white, alpha = alpha, style = Stroke(17f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(arrow, plum, alpha = alpha, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }

    /** A line-drawn shopping cart centred at (x, y). */
    fun DrawScope.cart(x: Double, y: Double, k: Double, ink: Color, alpha: Float = 1f) {
        val p = Path().apply {
            moveTo((x - 13 * k).f(), (y - 9 * k).f())
            lineTo((x - 9 * k).f(), (y - 9 * k).f())
            lineTo((x - 5 * k).f(), (y + 5 * k).f())
            lineTo((x + 9 * k).f(), (y + 5 * k).f())
            lineTo((x + 12 * k).f(), (y - 5 * k).f())
            lineTo((x - 7.6 * k).f(), (y - 5 * k).f())
        }
        drawPath(p, ink, alpha = alpha, style = Stroke((2.2 * k).f(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(circle(x - 3 * k, y + 10 * k, 2 * k), ink, alpha = alpha)
        drawPath(circle(x + 7 * k, y + 10 * k, 2 * k), ink, alpha = alpha)
    }

    /** Draws measured text centred at (x, y), or starting at x with anchor −1. */
    fun DrawScope.text(l: TextLayoutResult, x: Double, y: Double, alpha: Float = 1f, anchor: Int = 0) {
        val w = l.size.width.toFloat()
        val h = l.size.height.toFloat()
        val left = if (anchor < 0) x.f() else x.f() - w / 2
        drawText(l, topLeft = Offset(left, y.f() - h / 2), alpha = alpha)
    }
}
