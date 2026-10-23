package com.ngocdevv.nativecook.blocks.media.tapereelphysics

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** An ARGB int from 0xRRGGBB and an alpha. */
internal fun argb(hex: Long, a: Double = 1.0): Int =
    android.graphics.Color.argb((255 * a.coerceIn(0.0, 1.0)).toInt(), ((hex shr 16) and 0xFF).toInt(), ((hex shr 8) and 0xFF).toInt(), (hex and 0xFF).toInt())

/**
 * Drawing with the native canvas, in dp: the cassette (in a 362 × 229 design space), the transport
 * keys and the VU meters. Swift's blur radius is used as a Gaussian sigma: Android's radius ≈ (sigma − 0.5) / 0.577.
 */
internal class TapeReelPhysicsArt(private val label: Typeface) {
    private typealias M = TapeReelPhysicsModel

    companion object {
        val AMBER = argb(0xF2A93B)
        const val W = 362f
        const val H = 229f
        val SUPPLY = floatArrayOf(107f, 138f)
        val TAKE_UP = floatArrayOf(255f, 138f)
        val LEFT_GUIDE = floatArrayOf(81f, 206f)
        val RIGHT_GUIDE = floatArrayOf(281f, 206f)
        const val GUIDE_R = 5f
        /** The span between the guides runs along y = 211 from x = 81 to 281. */
        const val SPAN_Y = 211f
        const val SPAN_X0 = 81f
        const val SPAN_X1 = 281f
        val WINDOW = RectF(54f, 86f, 308f, 191f)

        fun blur(sigma: Float) = BlurMaskFilter(max(0.1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
    }

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun fill(c: Int) = fill.apply { shader = null; maskFilter = null; color = c }
    fun stroke(c: Int, w: Float, round: Boolean = false) = stroke.apply {
        color = c
        strokeWidth = w
        strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        strokeJoin = if (round) Paint.Join.ROUND else Paint.Join.MITER
    }

    /** Draws text with its box anchored like SwiftUI's: hx 0/0.5/1 (left, centre, right), vy 0.5 (centre) or 1 (bottom). */
    fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface = Typeface.DEFAULT, hx: Float = 0.5f, vy: Float = 0.5f, tracking: Float = 0f) {
        text.typeface = face
        text.textSize = size
        text.color = color
        text.letterSpacing = tracking / size
        val fm = text.fontMetrics
        val w = text.measureText(s)
        val baseline = if (vy >= 1f) y - fm.descent else y - (fm.ascent + fm.descent) / 2
        c.drawText(s, x - w * hx, baseline, text)
    }

    /** The point where tape leaves a pack of radius r for a point p, on the side away from the cassette's middle. */
    private fun tangent(cx: Float, cy: Float, r: Double, px: Float, py: Float, left: Boolean): FloatArray {
        val dx = (px - cx).toDouble()
        val dy = (py - cy).toDouble()
        val d = max(hypot(dx, dy), r + 0.01)
        val phi = atan2(dy, dx)
        val a = acos(r / d)
        val t1 = floatArrayOf((cx + r * cos(phi + a)).toFloat(), (cy + r * sin(phi + a)).toFloat())
        val t2 = floatArrayOf((cx + r * cos(phi - a)).toFloat(), (cy + r * sin(phi - a)).toFloat())
        return if (left) (if (t1[0] < t2[0]) t1 else t2) else (if (t1[0] > t2[0]) t1 else t2)
    }

    /** Adds a slack run of tape from a to b, bowed [sag] dp to one side (−1 left, +1 right). */
    private fun slackRun(p: Path, ax: Float, ay: Float, bx: Float, by: Float, sag: Double, side: Double, move: Boolean) {
        if (move) p.moveTo(ax, ay) else p.lineTo(ax, ay)
        val dx = (bx - ax).toDouble()
        val dy = (by - ay).toDouble()
        val len = max(hypot(dx, dy), 0.01)
        var nx = -dy / len
        var ny = dx / len
        if (nx * side < 0) { nx = -nx; ny = -ny }
        for (i in 1..16) {
            val u = i / 16.0
            val o = sag * sin(PI * u)
            p.lineTo((ax + dx * u + nx * o).toFloat(), (ay + dy * u + ny * o).toFloat())
        }
    }

    private fun rr(c: Canvas, r: RectF, radius: Float, p: Paint) = c.drawRoundRect(r, radius, radius, p)

    // Cassette

    fun cassette(c: Canvas, f: TapeReelPhysicsModel.Frame, t: Double, still: Boolean) {
        val shell = RectF(0f, 0f, W, H)
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = LinearGradient(0f, 0f, 0f, H, argb(0xF0E9D8), argb(0xD9CFB7), Shader.TileMode.CLAMP)
        fill.maskFilter = null
        rr(c, shell, 14f, fill)
        fill.shader = null
        rr(c, RectF(0.5f, 0.5f, W - 0.5f, H - 0.5f), 14f, stroke(argb(0xBDB197), 1f))
        rr(c, RectF(5f, 5f, W - 5f, H - 5f), 10f, stroke(argb(0xFFFFFF, 0.35), 1f))
        // Screws.
        for (sp in arrayOf(14f to 14f, W - 14 to 14f, 14f to H - 14, W - 14 to H - 14, W / 2 to 182f)) {
            c.drawCircle(sp.first, sp.second, 5f, fill(argb(0xBFB49B)))
            c.drawLine(
                (sp.first - 3.5 * cos(0.5)).toFloat(), (sp.second - 3.5 * sin(0.5)).toFloat(),
                (sp.first + 3.5 * cos(0.5)).toFloat(), (sp.second + 3.5 * sin(0.5)).toFloat(), stroke(argb(0x8D836C), 1.2f),
            )
        }

        // Label.
        val label = RectF(22f, 12f, 340f, 78f)
        rr(c, label, 6f, fill(argb(0xFBF8F0)))
        c.save()
        path.reset(); path.addRoundRect(label, 6f, 6f, Path.Direction.CW)
        c.clipPath(path)
        c.drawRect(22f, 12f, 340f, 21f, fill(argb(0xE4572E)))
        c.drawRect(22f, 21f, 340f, 23.5f, fill(argb(0xF2A93B)))
        c.restore()
        for (y in floatArrayOf(55f, 68f)) c.drawLine(56f, y, 330f, y, stroke(argb(0x6C8FD8, 0.55), 0.8f))
        c.drawCircle(38f, 46f, 10f, stroke(argb(0x2B2B2B), 1.2f))
        text(c, "A", 38f, 46f, 12f, argb(0x2B2B2B), Typeface.DEFAULT_BOLD)
        text(c, "Night Drive Mix", 58f, 53f, 24f, argb(0x1F3A8A), this.label, hx = 0f, vy = 1f)
        text(c, "C60", 328f, 34f, 10f, argb(0x6B6355), Typeface.create(Typeface.DEFAULT, 600, false), hx = 1f)

        // Window, packs and hubs.
        rr(c, WINDOW, 10f, fill(argb(0x100D0A)))
        val rs = M.supply(f.s)
        val rt = M.takeUp(f.s)
        var extra = 0.0
        val p = f.pluck
        if (p != null) {
            val r = p.releasedAt
            extra = if (r == null) p.d else if (still) 0.0 else M.ringing(p.x, p.x, p.d, t - r)
        }
        val aS = M.supplyAngle(f.s) + 0.03 * abs(extra)
        val aT = M.takeUpAngle(f.s) - 0.03 * abs(extra)
        c.save()
        path.reset(); path.addRoundRect(WINDOW, 10f, 10f, Path.Direction.CW)
        c.clipPath(path)
        pack(c, SUPPLY[0], SUPPLY[1], rs)
        pack(c, TAKE_UP[0], TAKE_UP[1], rt)
        hub(c, SUPPLY[0], SUPPLY[1], aS, M.spin(f.v, rs))
        hub(c, TAKE_UP[0], TAKE_UP[1], aT, M.spin(f.v, rt))
        // Glare across the glass.
        path.reset()
        path.moveTo(120f, 86f); path.lineTo(170f, 86f); path.lineTo(120f, 191f); path.lineTo(70f, 191f); path.close()
        c.drawPath(path, fill(argb(0xFFFFFF, 0.06)))
        path.reset()
        path.moveTo(182f, 86f); path.lineTo(194f, 86f); path.lineTo(144f, 191f); path.lineTo(132f, 191f); path.close()
        c.drawPath(path, fill(argb(0xFFFFFF, 0.05)))
        c.restore()
        rr(c, WINDOW, 10f, stroke(argb(0xB5AA90), 1.5f))

        // The head opening at the bottom, with its felt pad.
        path.reset()
        path.moveTo(112f, H); path.lineTo(124f, 198f); path.lineTo(238f, 198f); path.lineTo(250f, H); path.close()
        c.drawPath(path, fill(argb(0x2A241C)))
        c.drawRect(172f, 213f, 190f, 220f, fill(argb(0xC9B48A)))
        for (x in floatArrayOf(140f, 222f)) c.drawCircle(x, 220f, 4f, fill(argb(0x0F0C09)))

        // The tape: supply pack → left guide → the span → right guide → take-up pack.
        val lox = LEFT_GUIDE[0] - GUIDE_R
        val roX = RIGHT_GUIDE[0] + GUIDE_R
        val gy = LEFT_GUIDE[1]
        val tape = Path()
        val ts = tangent(SUPPLY[0], SUPPLY[1], rs, lox, gy, true)
        slackRun(tape, ts[0], ts[1], lox, gy, f.sag, -1.0, true)
        tape.arcTo(RectF(LEFT_GUIDE[0] - GUIDE_R, gy - GUIDE_R, LEFT_GUIDE[0] + GUIDE_R, gy + GUIDE_R), 180f, -90f, false)
        var x = SPAN_X0
        while (x < SPAN_X1) {
            x = min(x + 2f, SPAN_X1)
            val u = ((x - SPAN_X0) / (SPAN_X1 - SPAN_X0)).toDouble()
            tape.lineTo(x, (SPAN_Y + M.span(u, f, t, still)).toFloat())
        }
        tape.arcTo(RectF(RIGHT_GUIDE[0] - GUIDE_R, gy - GUIDE_R, RIGHT_GUIDE[0] + GUIDE_R, gy + GUIDE_R), 90f, -90f, false)
        val tt = tangent(TAKE_UP[0], TAKE_UP[1], rt, roX, gy, false)
        slackRun(tape, roX, gy, tt[0], tt[1], f.sag, 1.0, false)
        c.drawPath(tape, stroke(argb(0x4A3320), 2.4f, true))
        c.save()
        c.translate(0f, -0.7f)
        c.drawPath(tape, stroke(argb(0xA88560, 0.55), 0.7f, true))
        c.restore()
        // Steel guides.
        for (g in arrayOf(LEFT_GUIDE, RIGHT_GUIDE)) {
            fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
            fill.shader = LinearGradient(g[0] - GUIDE_R, g[1] - GUIDE_R, g[0] + GUIDE_R, g[1] + GUIDE_R, argb(0xE4E7EB), argb(0x868D96), Shader.TileMode.CLAMP)
            c.drawCircle(g[0], g[1], GUIDE_R, fill)
            fill.shader = null
            c.drawCircle(g[0], g[1], GUIDE_R - 3, fill(argb(0x5A6068)))
        }
    }

    private fun pack(c: Canvas, cx: Float, cy: Float, r: Double) {
        val rf = r.toFloat()
        fill.maskFilter = null
        // SwiftUI's radial gradient runs from radius 16 to r.
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(cx, cy, rf, intArrayOf(argb(0x3D2A18), argb(0x3D2A18), argb(0x23160C)), floatArrayOf(0f, 16f / rf, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rf, fill)
        fill.shader = null
        for (k in floatArrayOf(4f, 9f, 15f, 22f)) if (rf - k > 17) c.drawCircle(cx, cy, rf - k, stroke(argb(0x000000, 0.22), 0.6f))
        c.drawArc(RectF(cx - rf + 2.5f, cy - rf + 2.5f, cx + rf - 2.5f, cy + rf - 2.5f), 200f, 55f, false, stroke(argb(0xA88560, 0.3), 2f, true))
    }

    /** A hub: a white ring with six teeth and three dark notches. Spinning fast, the teeth smear into rings. */
    private fun hub(c: Canvas, cx: Float, cy: Float, angle: Double, omega: Double) {
        val b = M.smoothstep(10.0, 40.0, abs(omega))
        c.drawCircle(cx, cy, 16f, fill(argb(0xFFFFFF, 0.84)))
        c.drawCircle(cx, cy, 9f, fill(argb(0x100D0A)))
        c.save()
        c.translate(cx, cy)
        c.rotate((angle * 180 / PI).toFloat())
        for (k in 0 until 6) {
            c.save()
            c.rotate(k * 60f)
            rr(c, RectF(5.8f, -1.3f, 9.4f, 1.3f), 0.8f, fill(argb(0xFFFFFF, 0.84 * (1 - 0.8 * b))))
            c.restore()
        }
        for (k in 0 until 3) {
            c.save()
            c.rotate(k * 120f + 30f)
            rr(c, RectF(10.5f, -1.6f, 15f, 1.6f), 1f, fill(argb(0x2A241C, 1 - 0.8 * b)))
            c.restore()
        }
        c.restore()
        if (b > 0.01) {
            c.drawCircle(cx, cy, 7.6f, stroke(argb(0xFFFFFF, 0.45 * b), 2.6f))
            c.drawCircle(cx, cy, 12.75f, stroke(argb(0x2A241C, 0.35 * b), 3.2f))
        }
    }

    // Keys

    enum class Glyph { Rew, Play, FF, Pause, Stop, Rec }

    fun key(c: Canvas, glyph: Glyph, r: RectF, pressed: Boolean) {
        val sink = if (pressed) 2f else 0f
        // The well the key sits in.
        rr(c, RectF(r.left, r.top + 4, r.right, r.bottom + 4), 10f, fill(argb(0x0B0A08)))
        val k = RectF(r.left, r.top + sink, r.right, r.bottom + sink)
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = LinearGradient(
            0f, k.top, 0f, k.bottom,
            if (pressed) argb(0x232325) else argb(0x333336), if (pressed) argb(0x1C1C1E) else argb(0x232325), Shader.TileMode.CLAMP,
        )
        rr(c, k, 10f, fill)
        fill.shader = null
        c.drawLine(k.left + 10, k.top + 0.75f, k.right - 10, k.top + 0.75f, stroke(argb(0xFFFFFF, 0.1), 1f))
        if (pressed) {
            val edge = if (glyph == Glyph.Rec) argb(0xE5484D, 0.9) else argb(0xF2A93B, 0.9)
            rr(c, RectF(k.left + 0.6f, k.top + 0.6f, k.right - 0.6f, k.bottom - 0.6f), 10f, stroke(edge, 1.2f))
        }
        val color = if (glyph == Glyph.Rec) argb(0xE5484D) else if (pressed) AMBER else argb(0xF1E8D6)
        val cx = k.centerX()
        val cy = k.centerY()
        val g = Path()
        fun tri(x: Float, dir: Float) {
            g.moveTo(x - 5 * dir, cy - 6); g.lineTo(x + 5 * dir, cy); g.lineTo(x - 5 * dir, cy + 6); g.close()
        }
        when (glyph) {
            Glyph.Rew -> { tri(cx - 4, -1f); tri(cx + 5, -1f) }
            Glyph.Play -> tri(cx + 1, 1f)
            Glyph.FF -> { tri(cx - 5, 1f); tri(cx + 4, 1f) }
            Glyph.Pause -> {
                g.addRoundRect(RectF(cx - 5.5f, cy - 6.5f, cx - 1.5f, cy + 6.5f), 1f, 1f, Path.Direction.CW)
                g.addRoundRect(RectF(cx + 1.5f, cy - 6.5f, cx + 5.5f, cy + 6.5f), 1f, 1f, Path.Direction.CW)
            }
            Glyph.Stop -> g.addRoundRect(RectF(cx - 5.5f, cy - 5.5f, cx + 5.5f, cy + 5.5f), 1.5f, 1.5f, Path.Direction.CW)
            Glyph.Rec -> g.addCircle(cx, cy, 6f, Path.Direction.CW)
        }
        c.drawPath(g, fill(color))
    }

    // Meters

    private val marks = listOf(0.0 to "−20", 0.35 to "−10", 0.6 to "−5", 0.75 to "0", 1.0 to "+3")

    fun meter(c: Canvas, r: RectF, level: Double, glow: Double, channel: String) {
        rr(c, r, 14f, fill(argb(0x221E18)))
        val face = RectF(r.left + 8, r.top + 8, r.right - 8, r.bottom - 8)
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = LinearGradient(0f, face.top, 0f, face.bottom, argb(0xF3E9CF), argb(0xDCCBA4), Shader.TileMode.CLAMP)
        rr(c, face, 8f, fill)
        fill.color = android.graphics.Color.BLACK // a shader is drawn at the paint's alpha
        fill.shader = RadialGradient(face.centerX(), face.bottom, face.width() * 0.8f, argb(0xFFC65C, 0.35 * glow), argb(0xFFC65C, 0.0), Shader.TileMode.CLAMP)
        rr(c, face, 8f, fill)
        fill.shader = null
        val px = face.centerX()
        val py = face.bottom - 10
        val radius = face.width() * 0.46f
        fun at(u: Double, rr: Float): FloatArray {
            val a = (-140 + 100 * u) * PI / 180
            return floatArrayOf((px + rr * cos(a)).toFloat(), (py + rr * sin(a)).toFloat())
        }
        // Scale: a thin arc, a red zone from 0 VU, ticks and numbers.
        val arcBox = RectF(px - radius, py - radius, px + radius, py + radius)
        c.drawArc(arcBox, -140f, 75f, false, stroke(argb(0x3A3225), 1f))
        val red = radius + 1.5f
        c.drawArc(RectF(px - red, py - red, px + red, py + red), -65f, 25f, false, stroke(argb(0xC8402F), 4f))
        for (i in 0..20) {
            val u = i / 20.0
            val major = i % 4 == 0
            val a = at(u, radius)
            val b = at(u, radius + if (major) 8f else 4.5f)
            c.drawLine(a[0], a[1], b[0], b[1], stroke(if (u > 0.75) argb(0xC8402F) else argb(0x3A3225), if (major) 1.2f else 0.8f))
        }
        for ((u, s) in marks) {
            val p = at(u, radius + 16)
            text(c, s, p[0], p[1], 8f, if (u > 0.74) argb(0xC8402F) else argb(0x4A4030), Typeface.create(Typeface.MONOSPACE, 500, false))
        }
        text(c, "VU", face.centerX(), py - radius * 0.42f, 13f, argb(0x3A3225), Typeface.DEFAULT_BOLD)
        text(c, channel, face.left + 12, face.bottom - 12, 10f, argb(0x6B5F48), Typeface.create(Typeface.DEFAULT, 600, false))
        // Needle and its cap.
        val tip = at(level, radius + 6)
        c.drawLine(px, py, tip[0], tip[1], stroke(argb(0x1A1612), 1.3f, true))
        c.drawRect(face.left, face.bottom - 8, face.right, face.bottom, fill(argb(0xCBBB94)))
        c.drawCircle(px, py, 5f, fill(argb(0x1A1612)))
    }
}
