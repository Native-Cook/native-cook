package com.ngocdevv.nativecook.blocks.pickers.dicerollpicker

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.EDGE
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.HALF
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.INNER
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.RIM
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.ROUND
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TO_LIGHT
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TX0
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TX1
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TY0
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.TY1
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.WALL
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.clamp
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.project
import com.ngocdevv.nativecook.blocks.pickers.dicerollpicker.DiceMath.support
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 The dice table's art. Baked once: the dark games-room background and the walnut tray with its
 green felt. Per frame: each die (its shadow on the felt, a rounded silhouette, the lit faces and
 their concave pips, all projected from 3D), the players' avatars and the result line.
 */

internal class DiceFonts {
    val initials = diceFont(false, 18f, 700)
    val name = diceFont(false, 13f, 600)
    val range = diceFont(false, 11f, 500)
    val result = diceFont(false, 20f, 700)
    val tag = diceFont(false, 9f, 800)
}

internal class DicePlayer(val name: String, val initials: String, val range: String, val sums: IntArray, val colors: IntArray)

internal val DicePlayers = arrayOf(
    DicePlayer("Tomás", "T", "2–3", intArrayOf(2, 3), intArrayOf(0x7ec4cf, 0x3f8ea0, 0x245766)),
    DicePlayer("June", "J", "4–5", intArrayOf(4, 5), intArrayOf(0xf3b6a0, 0xd77a62, 0x8f4433)),
    DicePlayer("Kofi", "K", "6", intArrayOf(6), intArrayOf(0xc9d98a, 0x8fa64a, 0x56682a)),
    DicePlayer("Mira", "M", "7", intArrayOf(7), intArrayOf(0xd3b8f0, 0x9a7cc8, 0x5e4686)),
    DicePlayer("Elias", "E", "8–9", intArrayOf(8, 9), intArrayOf(0xf2d58a, 0xd0a23f, 0x8a6620)),
    DicePlayer("Priya", "P", "10–12", intArrayOf(10, 11, 12), intArrayOf(0xf5a9c4, 0xd9668f, 0x8e3258)),
)

internal fun dicePlayerFor(sum: Int): Int {
    for (i in DicePlayers.indices) if (sum in DicePlayers[i].sums) return i
    return 0
}

internal class DiceGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val ctaTop = h - max(bottom, 16f) - 8 - 56
    val avatarsTop = ctaTop - 20 - 92
    val resultBase = avatarsTop - 22
    private val headerBottom = top + 112
    val cx = w / 2
    // The tray's centre sits midway between the header and the result line, nudged down a little
    // since the far rim draws higher than the near one.
    val cy = (headerBottom + resultBase - 30) / 2 + 26
}

private fun c(vararg v: Pair<Int, Float>) = diceColors(*v)
private fun f(vararg v: Float) = v

// ——— Baked: room, tray, felt ———

private fun quad(g: DiceGeo, vararg pts: Double): Path {
    val out = DoubleArray(3)
    val p = Path()
    var i = 0
    while (i < pts.size) {
        project(g.cx.toDouble(), g.cy.toDouble(), pts[i], pts[i + 1], pts[i + 2], out)
        if (i == 0) p.moveTo(out[0].toFloat(), out[1].toFloat()) else p.lineTo(out[0].toFloat(), out[1].toFloat())
        i += 3
    }
    p.close()
    return p
}

private fun projY(g: DiceGeo, x: Double, y: Double, z: Double): Float {
    val out = DoubleArray(3)
    project(g.cx.toDouble(), g.cy.toDouble(), x, y, z, out)
    return out[1].toFloat()
}

internal fun diceDrawRoom(canvas: Canvas, k: DiceKit, g: DiceGeo) {
    canvas.drawRect(0f, 0f, g.w, g.h, k.linear(0f, 0f, 0f, g.h, c(0x231c17 to 1f, 0x1a1512 to 1f, 0x110e0c to 1f), f(0f, 0.5f, 1f)))
    canvas.drawRect(0f, 0f, g.w, g.h, k.radial(g.cx, g.cy - 40, g.w * 1.1f, c(0x6a5236 to 0.35f, 0x3b2c1f to 0.12f, 0 to 0f), f(0f, 0.55f, 1f)))
    val x0 = TX0
    val x1 = TX1
    val y0 = TY0
    val y1 = TY1
    val nearY = projY(g, 0.0, y0 - RIM, 0.0)
    val front = quad(g, x0 - RIM, y0 - RIM, 0.0, x1 + RIM, y0 - RIM, 0.0, x1 + RIM, y0 - RIM, WALL, x0 - RIM, y0 - RIM, WALL)
    canvas.drawPath(front, k.fill(0, 0.5f, 22f))
    canvas.drawOval(g.cx - g.w * 0.48f, nearY - 6, g.cx + g.w * 0.48f, nearY + 24, k.fill(0, 0.55f, 14f))
    // Front face of the tray (its outer side, toward the player).
    canvas.drawPath(front, k.linear(0f, nearY - 18, 0f, nearY, c(0x6b3f22 to 1f, 0x4a2a15 to 1f, 0x2e1a0c to 1f), f(0f, 0.5f, 1f)))
    // Tray: outer top surface at the wall's height.
    val topP = quad(g, x0 - RIM, y0 - RIM, WALL, x1 + RIM, y0 - RIM, WALL, x1 + RIM, y1 + RIM, WALL, x0 - RIM, y1 + RIM, WALL)
    val ot = projY(g, x0 - RIM, y1 + RIM, WALL)
    val ob = projY(g, x0 - RIM, y0 - RIM, WALL)
    canvas.drawPath(topP, k.linear(0f, ot, 0f, ob, c(0x8a5530 to 1f, 0x6e3f20 to 1f, 0x8f5a33 to 1f, 0x5a3218 to 1f), f(0f, 0.35f, 0.7f, 1f)))
    // Wood grain along the rails.
    canvas.save()
    canvas.clipPath(topP)
    k.brushed(canvas, 0f, ot - 20, g.w, ob - ot + 40, 0.16f, 9)
    canvas.restore()
    // Inner walls (far and sides), shaded, then the felt.
    canvas.drawPath(
        quad(g, x0, y1, 0.0, x1, y1, 0.0, x1, y1, WALL, x0, y1, WALL),
        k.linear(0f, projY(g, 0.0, y1, WALL), 0f, projY(g, 0.0, y1, 0.0), c(0x5a3519 to 1f, 0x3a210f to 1f), null),
    )
    canvas.drawPath(quad(g, x0, y0, 0.0, x0, y1, 0.0, x0, y1, WALL, x0, y0, WALL), k.fill(0x4d2d15))
    canvas.drawPath(quad(g, x1, y0, 0.0, x1, y1, 0.0, x1, y1, WALL, x1, y0, WALL), k.fill(0x2f1b0c))
    val felt = quad(g, x0, y0, 0.0, x1, y0, 0.0, x1, y1, 0.0, x0, y1, 0.0)
    val fc = DoubleArray(3)
    project(g.cx.toDouble(), g.cy.toDouble(), 0.0, 20.0, 0.0, fc)
    canvas.drawPath(felt, k.radial(fc[0].toFloat(), fc[1].toFloat(), g.w * 0.62f, c(0x2f7a59 to 1f, 0x22634a to 1f, 0x174433 to 1f), f(0f, 0.55f, 1f)))
    canvas.save()
    canvas.clipPath(felt)
    k.noise(canvas, 0f, 0f, g.w, g.h, 0.18f, 21)
    // Shade along the walls: the felt meets the rail in a soft shadow.
    canvas.drawPath(felt, k.stroke(0x0a1f16, 18f, 0.5f, 9f))
    canvas.restore()
    // Rail highlights: the inner and outer lips catch the lamp.
    fun lip(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double, col: Int, alpha: Float) {
        val p = DoubleArray(3)
        val q = DoubleArray(3)
        project(g.cx.toDouble(), g.cy.toDouble(), ax, ay, az, p)
        project(g.cx.toDouble(), g.cy.toDouble(), bx, by, bz, q)
        canvas.drawLine(p[0].toFloat(), p[1].toFloat(), q[0].toFloat(), q[1].toFloat(), k.stroke(col, 1.2f, alpha))
    }
    lip(x0, y1, WALL, x1, y1, WALL, 0xf2c08a, 0.5f)
    lip(x0 - RIM, y0 - RIM, WALL, x1 + RIM, y0 - RIM, WALL, 0xf2c08a, 0.45f)
    lip(x0, y0, WALL, x1, y0, WALL, 0x1a0d05, 0.6f)
    k.grain(canvas, 0f, 0f, g.w, g.h, 0.06f, 4)
    canvas.drawRect(0f, 0f, g.w, g.h, k.radial(g.cx, g.h * 0.45f, g.h * 0.72f, c(0 to 0f, 0 to 0.08f, 0 to 0.5f), f(0f, 0.65f, 1f)))
}

// ——— Per frame: dice ———

private val PIPS: Array<IntArray> = arrayOf(
    intArrayOf(),
    intArrayOf(0, 0),
    intArrayOf(-1, -1, 1, 1),
    intArrayOf(-1, -1, 0, 0, 1, 1),
    intArrayOf(-1, -1, 1, -1, -1, 1, 1, 1),
    intArrayOf(-1, -1, 1, -1, -1, 1, 1, 1, 0, 0),
    intArrayOf(-1, -1, -1, 0, -1, 1, 1, -1, 1, 0, 1, 1),
)
private val FACE_POS = intArrayOf(3, 2, 1)
private val FACE_NEG = intArrayOf(4, 5, 6)
private val IVORY = doubleArrayOf(0.965, 0.935, 0.86)

/** Reused per-frame scratch for the dice. */
internal class DiceScratch {
    val px = DoubleArray(8)
    val py = DoubleArray(8)
    val order = IntArray(8)
    val hullX = DoubleArray(17)
    val hullY = DoubleArray(17)
    val v3 = DoubleArray(3)
    val cam = DoubleArray(3)
    val pr = DoubleArray(3)
    val pu = DoubleArray(3)
    val pv = DoubleArray(3)
    val q = Array(4) { DoubleArray(3) }
    val sil = Path()
    val face = Path()
    val matrix = Matrix()
    val mv = FloatArray(9)
}

/** Convex hull of the 8 projected corners into `out` (monotone chain). */
private fun hull(s: DiceScratch, out: Path) {
    for (i in 0 until 8) s.order[i] = i
    // Insertion sort by x, then y.
    for (i in 1 until 8) {
        val v = s.order[i]
        var j = i - 1
        while (j >= 0 && (s.px[s.order[j]] > s.px[v] || (s.px[s.order[j]] == s.px[v] && s.py[s.order[j]] > s.py[v]))) {
            s.order[j + 1] = s.order[j]
            j--
        }
        s.order[j + 1] = v
    }
    fun cross(ox: Double, oy: Double, ax: Double, ay: Double, bx: Double, by: Double) = (ax - ox) * (by - oy) - (ay - oy) * (bx - ox)
    var n = 0
    for (idx in 0 until 8) {
        val qx = s.px[s.order[idx]]
        val qy = s.py[s.order[idx]]
        while (n >= 2 && cross(s.hullX[n - 2], s.hullY[n - 2], s.hullX[n - 1], s.hullY[n - 1], qx, qy) <= 0) n--
        s.hullX[n] = qx
        s.hullY[n] = qy
        n++
    }
    val lower = n + 1
    for (idx in 6 downTo 0) {
        val qx = s.px[s.order[idx]]
        val qy = s.py[s.order[idx]]
        while (n >= lower && cross(s.hullX[n - 2], s.hullY[n - 2], s.hullX[n - 1], s.hullY[n - 1], qx, qy) <= 0) n--
        s.hullX[n] = qx
        s.hullY[n] = qy
        n++
    }
    n-- // the last point repeats the first
    out.reset()
    for (i in 0 until n) {
        if (i == 0) out.moveTo(s.hullX[i].toFloat(), s.hullY[i].toFloat()) else out.lineTo(s.hullX[i].toFloat(), s.hullY[i].toFloat())
    }
    out.close()
}

/** Lit ivory for a normal at a point, as ARGB with the given alpha. */
private fun shade(s: DiceScratch, nx: Double, ny: Double, nz: Double, ax: Double, ay: Double, az: Double, kk: Double, alpha: Float): Int {
    val diff = max(0.0, nx * TO_LIGHT[0] + ny * TO_LIGHT[1] + nz * TO_LIGHT[2])
    DiceMath.toCamera(ax, ay, az, s.cam)
    val hx = TO_LIGHT[0] + s.cam[0]
    val hy = TO_LIGHT[1] + s.cam[1]
    val hz = TO_LIGHT[2] + s.cam[2]
    val hl = sqrt(hx * hx + hy * hy + hz * hz)
    val spec = max(0.0, (nx * hx + ny * hy + nz * hz) / hl).pow(36) * 0.28
    val l = (0.5 + 0.55 * diff) * kk
    val r = (min(1.0, IVORY[0] * l + spec) * 255).roundToInt()
    val g = (min(1.0, IVORY[1] * l + spec) * 255).roundToInt()
    val b = (min(1.0, IVORY[2] * l + spec * 0.95) * 255).roundToInt()
    return ((alpha * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b
}

/** The die's shadow on the felt (drawn for both dice before either die). */
internal fun diceDrawShadow(canvas: Canvas, k: DiceKit, s: DiceScratch, g: DiceGeo, pose: DicePose) {
    val m = pose.m
    val zc = pose.hop + support(m)
    for (i in 0 until 8) {
        DiceMath.apply(m, if (i and 1 != 0) INNER else -INNER, if (i and 2 != 0) INNER else -INNER, if (i and 4 != 0) INNER else -INNER, s.v3)
        val z = zc + s.v3[2]
        project(g.cx.toDouble(), g.cy.toDouble(), pose.x + s.v3[0] - TO_LIGHT[0] / TO_LIGHT[2] * z, pose.y + s.v3[1] - TO_LIGHT[1] / TO_LIGHT[2] * z, 0.0, s.pr)
        s.px[i] = s.pr[0]
        s.py[i] = s.pr[1]
    }
    val lift = clamp(pose.hop / 90, 0.0, 1.0).toFloat()
    hull(s, s.sil)
    canvas.drawPath(s.sil, k.stroke(0x02100a, (ROUND * 2).toFloat(), 0.5f - 0.3f * lift, 6 + lift * 10))
    canvas.drawPath(s.sil, k.fill(0x02100a, 0.55f - 0.35f * lift, 6 + lift * 10))
}

internal fun diceDrawDie(canvas: Canvas, k: DiceKit, s: DiceScratch, g: DiceGeo, pose: DicePose) {
    val m = pose.m
    val gcx = g.cx.toDouble()
    val gcy = g.cy.toDouble()
    val zc = pose.hop + support(m)
    val cx = pose.x
    val cy = pose.y
    project(gcx, gcy, cx, cy, zc, s.pr)
    val pcs = s.pr[2].toFloat()
    for (i in 0 until 8) {
        DiceMath.apply(m, if (i and 1 != 0) INNER else -INNER, if (i and 2 != 0) INNER else -INNER, if (i and 4 != 0) INNER else -INNER, s.v3)
        project(gcx, gcy, cx + s.v3[0], cy + s.v3[1], zc + s.v3[2], s.pu)
        s.px[i] = s.pu[0]
        s.py[i] = s.pu[1]
    }
    // Rounded silhouette: the inner cube's outline, stroked round by the corner radius.
    hull(s, s.sil)
    val edge = shade(s, 0.0, -0.35, 0.94, cx, cy, zc, 0.82, 1f)
    val sp = k.fill(0)
    sp.color = edge
    canvas.drawPath(s.sil, sp)
    val ss = k.stroke(0, (ROUND * 2).toFloat() * pcs)
    ss.color = edge
    canvas.drawPath(s.sil, ss)
    for (ax in 0 until 3) {
        for (sgn in intArrayOf(-1, 1)) {
            val nx = m[ax] * sgn
            val ny = m[3 + ax] * sgn
            val nz = m[6 + ax] * sgn
            val fx = cx + nx * HALF
            val fy = cy + ny * HALF
            val fz = zc + nz * HALF
            DiceMath.toCamera(fx, fy, fz, s.cam)
            val facing = nx * s.cam[0] + ny * s.cam[1] + nz * s.cam[2]
            if (facing <= 0.01) continue
            val ku = (ax + 1) % 3
            val kv = (ax + 2) % 3
            val ux = m[ku]
            val uy = m[3 + ku]
            val uz = m[6 + ku]
            val vx = m[kv]
            val vy = m[3 + kv]
            val vz = m[6 + kv]
            val signs = intArrayOf(-1, -1, 1, -1, 1, 1, -1, 1)
            for (c in 0 until 4) {
                val a = signs[c * 2] * INNER
                val b = signs[c * 2 + 1] * INNER
                project(gcx, gcy, fx + ux * a + vx * b, fy + uy * a + vy * b, fz + uz * a + vz * b, s.q[c])
            }
            s.face.reset()
            s.face.moveTo(s.q[0][0].toFloat(), s.q[0][1].toFloat())
            for (c in 1 until 4) s.face.lineTo(s.q[c][0].toFloat(), s.q[c][1].toFloat())
            s.face.close()
            val lit = shade(s, nx, ny, nz, fx, fy, fz, 1.0, 1f)
            val fp = k.fill(0)
            fp.color = lit
            canvas.drawPath(s.face, fp)
            // The flat face blends into the rounded edge: a soft stroke in the face's own colour.
            val blend = k.stroke(0, (ROUND * 0.9).toFloat() * pcs, 0.55f, (ROUND * 0.35).toFloat())
            blend.color = (lit and 0xFFFFFF) or (140 shl 24)
            canvas.drawPath(s.face, blend)
            // Pips: a projected circle each, darker floor offset away from the light (concave).
            val value = if (sgn > 0) FACE_POS[ax] else FACE_NEG[ax]
            val layout = PIPS[value]
            val spread = EDGE * 0.245
            val pr = EDGE * (if (value == 1) 0.13 else 0.082)
            val lu = (TO_LIGHT[0] * ux + TO_LIGHT[1] * uy + TO_LIGHT[2] * uz).toFloat()
            val lv = (TO_LIGHT[0] * vx + TO_LIGHT[1] * vy + TO_LIGHT[2] * vz).toFloat()
            val alpha = clamp((facing - 0.01) / 0.25, 0.0, 1.0).toFloat()
            val red = value == 1
            var p = 0
            while (p < layout.size) {
                val a = layout[p] * spread
                val b = layout[p + 1] * spread
                val ox = fx + ux * a + vx * b
                val oy = fy + uy * a + vy * b
                val oz = fz + uz * a + vz * b
                project(gcx, gcy, ox, oy, oz, s.pr)
                project(gcx, gcy, ox + ux * pr, oy + uy * pr, oz + uz * pr, s.pu)
                project(gcx, gcy, ox + vx * pr, oy + vy * pr, oz + vz * pr, s.pv)
                val mv = s.mv
                mv[0] = (s.pu[0] - s.pr[0]).toFloat()
                mv[1] = (s.pv[0] - s.pr[0]).toFloat()
                mv[2] = s.pr[0].toFloat()
                mv[3] = (s.pu[1] - s.pr[1]).toFloat()
                mv[4] = (s.pv[1] - s.pr[1]).toFloat()
                mv[5] = s.pr[1].toFloat()
                mv[6] = 0f
                mv[7] = 0f
                mv[8] = 1f
                s.matrix.setValues(mv)
                canvas.save()
                canvas.concat(s.matrix)
                canvas.drawCircle(0f, 0f, 1f, k.fill(if (red) 0x9e1f1a else 0x2b1d17, alpha))
                canvas.drawCircle(-lu * 0.28f, -lv * 0.28f, 0.78f, k.fill(if (red) 0x6e100c else 0x140c09, alpha))
                canvas.drawCircle(lu * 0.42f, lv * 0.42f, 0.45f, k.fill(if (red) 0xd65a4c else 0x6b5a4c, alpha * 0.35f, 0.2f))
                canvas.restore()
                p += 2
            }
        }
    }
    // A thin bright rim on the lit side of the silhouette.
    canvas.save()
    canvas.translate(-0.6f, -0.8f)
    canvas.drawPath(s.sil, k.stroke(0xfffaf0, 1f, 0.22f))
    canvas.restore()
}

// ——— Per frame: avatars and the result line ———

private val PLAYER_GRADIENTS = Array(DicePlayers.size) { i -> IntArray(3) { diceArgb(DicePlayers[i].colors[it]) } }
private val STOPS_AVATAR = f(0f, 0.55f, 1f)

/** pop: the chosen player's pop (0 … 1.3 with the spring's overshoot); it also wears the "Starts" tag. */
internal fun diceDrawAvatars(canvas: Canvas, k: DiceKit, g: DiceGeo, ft: DiceFonts, pop: Float, chosen: Int) {
    val n = DicePlayers.size
    val cell = (g.w - 32) / n
    val r = min(24f, cell * 0.4f)
    val cy = g.avatarsTop + r + 14
    for (i in 0 until n) {
        val p = DicePlayers[i]
        val x = 16 + cell * (i + 0.5f)
        val kk = if (i == chosen) pop else 0f
        val s = 1 + 0.16f * kk
        val lift = 6 * kk
        canvas.save()
        canvas.translate(x, cy - lift)
        canvas.scale(s, s)
        canvas.drawCircle(0f, 3 + lift * 0.6f, r, k.fill(0, 0.35f + 0.1f * kk, 4 + 6 * kk))
        if (kk > 0.01f) canvas.drawCircle(0f, 0f, r + 3.5f, k.stroke(0xf2c46b, 2.2f, kk.coerceIn(0f, 1f)))
        canvas.drawCircle(0f, 0f, r, k.radial(-r * 0.3f, -r * 0.4f, r * 1.4f, PLAYER_GRADIENTS[i], STOPS_AVATAR))
        canvas.drawCircle(0f, 0f, r - 0.5f, k.stroke(0xffffff, 1f, 0.25f))
        val iw = ft.initials.measureText(p.initials)
        k.text(canvas, p.initials, -iw / 2, 6.5f, ft.initials, 0xfffaf2, 0.95f)
        canvas.restore()
        val nw = ft.name.measureText(p.name)
        k.text(canvas, p.name, x - nw / 2, cy + r + 20, ft.name, 0xf3eadb, 0.6f + 0.4f * kk.coerceIn(0f, 1f))
        val rw = ft.range.measureText(p.range)
        k.text(canvas, p.range, x - rw / 2, cy + r + 36, ft.range, 0xf3eadb, 0.38f)
        if (i == chosen && kk > 0.02f) {
            val a = kk.coerceIn(0f, 1f)
            val tw = ft.tag.measureText("STARTS") + 14
            val ty = cy - r * s - lift - 26 + (1 - a) * 8
            canvas.drawRoundRect(x - tw / 2, ty, x + tw / 2, ty + 18, 9f, 9f, k.fill(0xf2c46b, a))
            k.text(canvas, "STARTS", x - tw / 2 + 7, ty + 12.5f, ft.tag, 0x3a2610, a)
        }
    }
}

/** The result line: rolls from the prompt (k = 0) to "11 · Priya starts" (k = 1). */
internal fun diceDrawResult(canvas: Canvas, k: DiceKit, g: DiceGeo, ft: DiceFonts, text: String, roll: Float) {
    val travel = 22f
    canvas.save()
    canvas.clipRect(0f, g.resultBase - 26, g.w, g.resultBase + 8)
    for (j in 0 until 2) {
        val a = 1 - min(1f, abs(j - roll))
        if (a <= 0.01f) continue
        val font: Paint = if (j == 0) ft.name else ft.result
        val line = if (j == 0) "Flick the dice to pick who starts" else text
        val w = font.measureText(line)
        k.text(canvas, line, g.cx - w / 2, g.resultBase + (j - roll) * travel, font, if (j == 0) 0xf3eadb else 0xfff4dc, if (j == 0) a * 0.6f else a)
    }
    canvas.restore()
}
