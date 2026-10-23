package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion as M
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The paper: the "invite them" button folded into a dart, flown, and a new sheet unfolded.
 *
 * The button is a sheet in its own flat coordinates (x 0..W, y 0..H, origin at its top-left). Each
 * visible piece of paper (a face) is a flat polygon carried into 3D by a chain of rigid folds, then
 * placed on screen and projected with a pinhole camera: one 3x3 homography per face, which Android's
 * canvas draws directly (perspective matrix), so the gradient stays printed on the paper.
 *
 * Faces (draw order 1, 0, 4, 5, 2, 3): 0 left half, 1 right half, 2 top wing, 3 bottom wing,
 * 4 top flap, 5 bottom flap.
 */
internal object FindFriendsInviteFold {
    const val FACES = 6
    val drawOrder = intArrayOf(1, 0, 4, 5, 2, 3)
    private val hidden = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0)

    /** 3D affine map, row-major: [m00 m01 m02 m10 m11 m12 m20 m21 m22 t0 t1 t2]. */
    private fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
        val r = DoubleArray(12)
        for (i in 0..2) {
            for (j in 0..2) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
            }
        }
        for (i in 0..2) {
            r[9 + i] = a[i * 3] * b[9] + a[i * 3 + 1] * b[10] + a[i * 3 + 2] * b[11] + a[9 + i]
        }
        return r
    }

    /** Rotation by [th] (rad) about the axis through (px, py, pz) along unit (ux, uy, uz). */
    private fun rotAbout(
        px: Double, py: Double, pz: Double,
        ux: Double, uy: Double, uz: Double,
        th: Double,
    ): DoubleArray {
        val c = cos(th)
        val s = sin(th)
        val k = 1 - c
        val m = doubleArrayOf(
            c + ux * ux * k, ux * uy * k - uz * s, ux * uz * k + uy * s,
            uy * ux * k + uz * s, c + uy * uy * k, uy * uz * k - ux * s,
            uz * ux * k - uy * s, uz * uy * k + ux * s, c + uz * uz * k,
        )
        return doubleArrayOf(
            m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8],
            px - (m[0] * px + m[1] * py + m[2] * pz),
            py - (m[3] * px + m[4] * py + m[5] * pz),
            pz - (m[6] * px + m[7] * py + m[8] * pz),
        )
    }

    private fun move(x: Double, y: Double, z: Double) = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, x, y, z)

    /** Uniform scale [k] about (x, y) in the screen plane. */
    private fun scaleAbout(k: Double, x: Double, y: Double) =
        doubleArrayOf(k, 0.0, 0.0, 0.0, k, 0.0, 0.0, 0.0, k, x - k * x, y - k * y, 0.0)

    private fun homography(a: DoubleArray, cx: Double, cy: Double, d: Double) = doubleArrayOf(
        a[0] - cx * a[6] / d, a[1] - cx * a[7] / d, a[9] - cx * a[11] / d,
        a[3] - cy * a[6] / d, a[4] - cy * a[7] / d, a[10] - cy * a[11] / d,
        -a[6] / d, -a[7] / d, 1 - a[11] / d,
    )

    /** The face's shadow on the card (z = 0), cast along the light. */
    private fun shadowMatrix(a: DoubleArray): DoubleArray {
        val sx = M.Light.shadowDir[0]
        val sy = M.Light.shadowDir[1]
        return doubleArrayOf(
            a[0] + sx * a[6], a[1] + sx * a[7], a[9] + sx * a[11],
            a[3] + sy * a[6], a[4] + sy * a[7], a[10] + sy * a[11],
            0.0, 0.0, 1.0,
        )
    }

    /** Lighting of a face from its normal: (dark overlay, bright overlay). */
    private fun shade(a: DoubleArray): Pair<Double, Double> {
        var nx = a[2]
        var ny = a[5]
        var nz = a[8]
        val len = hypot(hypot(nx, ny), nz).let { if (it == 0.0) 1.0 else it }
        nx /= len
        ny /= len
        nz /= len
        if (nz < 0) {
            nx = -nx
            ny = -ny
            nz = -nz
        }
        val l = M.Light.dir
        val ll = sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2])
        val d = (nx * l[0] + ny * l[1] + nz * l[2]) / ll - l[2] / ll
        return Pair(max(0.0, -d) * M.Light.dark, max(0.0, d) * M.Light.bright)
    }

    fun pressScale(tau: Double, pressIn: Double, release: Double, to: Double): Double {
        if (tau <= 0) return 1.0
        if (tau < pressIn) return 1 - (1 - to) * M.ease(M.easeOut, tau / pressIn)
        if (tau < release) return to
        return to + (1 - to) * M.springStep(tau - release, M.SpringUi.omega, M.SpringUi.zeta)
    }

    /** Corner radius of the button's sheet. */
    fun sheetRadius(tau: Double, pill: Double): Double {
        val t = tau - M.Invite.release
        if (tau <= M.Invite.release) return pill
        if (t < M.Reform.radiusBack[0]) {
            return pill + (M.Fold.paperRadius - pill) * M.ease(M.easeOut, M.seg(t, 0.0, M.Fold.radiusDur))
        }
        return M.Fold.paperRadius +
            (pill - M.Fold.paperRadius) * M.ease(M.easeOut, M.seg(t, M.Reform.radiusBack[0], M.Reform.radiusBack[1]))
    }

    /** Label + icon of the button: (opacity, scale). */
    fun labelState(tau: Double): Pair<Double, Double> {
        val t = tau - M.Invite.release
        if (tau <= M.Invite.release) return Pair(1.0, 1.0)
        val out = M.ease(M.easeOut, M.seg(t, 0.0, M.Fold.labelOut))
        val back = M.ease(M.easeOut, M.seg(t, M.Reform.labelIn[0], M.Reform.labelIn[1]))
        val v = if (t < M.Reform.labelIn[0]) 1 - out else back
        return Pair(v, M.Fold.labelScale + (1 - M.Fold.labelScale) * v)
    }

    /** Whether the native button (pill + label) is shown. */
    fun buttonShown(tau: Double): Boolean {
        val t = tau - M.Invite.release
        return tau <= M.Invite.release || t < M.Fold.rnOff || t >= M.Reform.rnOn
    }

    fun paperOn(tau: Double): Boolean {
        val t = tau - M.Invite.release
        return tau > M.Invite.release && t >= M.Fold.skiaOn &&
            t < max(M.Reform.skiaOff, M.Fold.launch + M.Flight.duration + M.Contrail.fadeOut)
    }

    class Geo(
        val w: Double, val h: Double, val left: Double, val top: Double,
        val camX: Double, val camY: Double, val offX: Double, val offY: Double,
    )

    class Frame {
        val mats = Array(FACES) { hidden }
        val shadows = Array(FACES) { hidden }
        val vis = DoubleArray(FACES)
        val dark = DoubleArray(FACES)
        val bright = DoubleArray(FACES)
        var radius = 0.0
        /** Face 0 is the whole pill (no crease yet / any more). */
        var flat = false
        var shadowOpacity = M.Light.shadowOpacity
        var shadowBlur = M.Light.shadowBlur
        var trailOpacity = 0.0
        /** Ribbon edge points as x, y pairs, two points per sample (tail -> head); empty without a contrail. */
        var trail = DoubleArray(0)
        val any: Boolean get() = vis.any { it > 0.0 }
    }

    /** The paper at [tau] seconds after the press of "invite them". */
    fun frame(tau: Double, g: Geo, pill: Double): Frame {
        val t = tau - M.Invite.release
        val w = g.w
        val h = g.h
        val c = h / 2
        val d = M.Flight.camera
        val f = Frame()
        f.radius = sheetRadius(tau, pill)
        if (tau <= M.Invite.release || t < M.Fold.skiaOn) return f

        fun put(i: Int, a: DoubleArray, o: Double) {
            f.mats[i] = homography(a, g.camX, g.camY, d)
            f.shadows[i] = shadowMatrix(a)
            f.vis[i] = o
            val s = shade(a)
            f.dark[i] = s.first
            f.bright[i] = s.second
        }

        val cx = g.left + w / 2
        val cy = g.top + c
        val flightEnd = M.Fold.launch + M.Flight.duration

        // The sheet before and during the book fold (faces 0, 1).
        if (t < M.Fold.book[1]) {
            val k = pressScale(tau, M.Invite.pressIn, M.Invite.release, M.Invite.pressScale)
            val place = mul(scaleAbout(k, cx, cy), move(g.left, g.top, 0.0))
            val th = PI * M.ease(M.easeFold, M.seg(t, M.Fold.book[0], M.Fold.book[1]))
            f.flat = th <= 0
            put(0, mul(place, rotAbout(w / 2, 0.0, 0.0, 0.0, 1.0, 0.0, th)), 1.0)
            if (!f.flat) put(1, place, 1.0)
        }

        // The dart: flaps, keel, launch and flight (faces 2...5).
        var z = 0.0
        if (t >= M.Fold.book[1] && t < flightEnd) {
            var beta = M.Fold.keelAngle * M.ease(M.easeFold, M.seg(t, M.Fold.keelClose[0], M.Fold.keelClose[1]))
            if (t > M.Fold.keelClose[1]) {
                beta = M.Fold.keelAngle + (M.Fold.wingAngle - M.Fold.keelAngle) *
                    M.springStep(t - M.Fold.keelClose[1], M.Fold.wingOmega, M.Fold.wingZeta)
            }
            beta = beta * PI / 180
            val fa = M.Fold.flapAngle * PI / 180
            val ft = fa * M.ease(M.easeFold, M.seg(t, M.Fold.flapTop[0], M.Fold.flapTop[1]))
            val fb = fa * M.ease(M.easeFold, M.seg(t, M.Fold.flapBot[0], M.Fold.flapBot[1]))
            val fadeFrom = (M.Fold.flapAngle - M.Fold.flapFade) * PI / 180
            val ot = 1 - M.seg(ft, fadeFrom, fa)
            val ob = 1 - M.seg(fb, fadeFrom, fa)

            var px = g.left + 3 * w / 4
            var py = g.top + c
            var yaw = 0.0
            var m = 1.0
            var roll = 0.0
            var k = pressScale(tau, M.Invite.pressIn, M.Invite.release, M.Invite.pressScale)
            if (t >= M.Fold.launch) {
                val tf = t - M.Fold.launch
                val p = M.ease(M.easeHang, tf / M.Flight.duration)
                val at = M.pathAt(p * M.path.length)
                px = at.x + g.offX
                py = at.y + g.offY
                z = M.Flight.lift * sin(PI * p)
                yaw = at.h * M.ease(M.easeOut, M.seg(tf, 0.0, M.Flight.yawDur))
                m = 1 - (1 - M.Flight.scale) * M.ease(M.easeOut, M.seg(tf, 0.0, M.Flight.scaleDur))
                roll = at.b * M.ease(M.easeOut, M.seg(tf, 0.0, M.Flight.bankIn))
                k = 1.0
            }
            val cs = cos(yaw)
            val sn = sin(yaw)
            val dcx = 3 * w / 4
            val dcy = c
            val fly = doubleArrayOf(
                cs * m, -sn * m, 0.0, sn * m, cs * m, 0.0, 0.0, 0.0, m,
                px - m * (cs * dcx - sn * dcy), py - m * (sn * dcx + cs * dcy), z,
            )
            val place = mul(scaleAbout(k, cx, cy), fly)
            val book = rotAbout(w / 2, 0.0, 0.0, 0.0, 1.0, 0.0, PI)
            put(2, mul(mul(place, rotAbout(0.0, c, 0.0, 1.0, 0.0, 0.0, -beta + roll)), book), 1.0)
            put(3, mul(mul(place, rotAbout(0.0, c, 0.0, 1.0, 0.0, 0.0, beta + roll)), book), 1.0)
            val ln = hypot(w / 2, c)
            if (ot > 0) put(4, mul(mul(place, rotAbout(w / 2, 0.0, 0.0, w / 2 / ln, c / ln, 0.0, ft)), book), ot)
            if (ob > 0) put(5, mul(mul(place, rotAbout(w / 2, h, 0.0, w / 2 / ln, -c / ln, 0.0, -fb)), book), ob)
        }

        // The new sheet: appears folded, unfolds into the button (faces 0, 1).
        if (t >= M.Reform.appear && t < M.Reform.skiaOff) {
            val a = M.ease(M.easeOut, M.seg(t, M.Reform.appear, M.Reform.appear + M.Reform.appearDur))
            val k = M.Reform.appearScale + (1 - M.Reform.appearScale) * a
            val place = mul(scaleAbout(k, g.left + 3 * w / 4, cy), move(g.left, g.top, 0.0))
            var th = PI
            if (t > M.Reform.unfold) {
                th = PI * max(0.0, 1 - M.springStep(t - M.Reform.unfold, M.Reform.omega, M.Reform.zeta))
            }
            if (t >= M.Reform.rnOn) th = 0.0
            f.flat = th <= 0
            put(0, mul(place, rotAbout(w / 2, 0.0, 0.0, 0.0, 1.0, 0.0, th)), a)
            if (!f.flat) put(1, place, a)
        }

        // Shadow: softer and lighter as the dart climbs.
        f.shadowOpacity = M.Light.shadowOpacity * (1 - 0.5 * z / M.Flight.lift)
        f.shadowBlur = M.Light.shadowBlur + M.Light.shadowBlurPerPt * z

        // Contrail: a ribbon along the path behind the dart.
        if (t >= M.Fold.launch && t < flightEnd + M.Contrail.fadeOut) {
            val tf = t - M.Fold.launch
            val p = M.ease(M.easeHang, min(tf / M.Flight.duration, 1.0))
            val m = 1 - (1 - M.Flight.scale) * M.ease(M.easeOut, M.seg(tf, 0.0, M.Flight.scaleDur))
            val head = p * M.path.length - (w / 4) * m * M.Contrail.headGap
            val tail = max(0.0, head - M.Contrail.length)
            if (head > 1) {
                f.trailOpacity = M.seg(tf, 0.0, M.Contrail.fadeIn) *
                    (1 - M.seg(tf, M.Flight.duration, M.Flight.duration + M.Contrail.fadeOut))
                val count = M.Contrail.samples
                val pts = DoubleArray(count * 4)
                for (i in 0 until count) {
                    val u = i.toDouble() / (count - 1)
                    val s = tail + (head - tail) * u
                    val at = M.pathAt(s)
                    val zi = M.Flight.lift * sin(PI * s / M.path.length)
                    val fz = d / (d - zi)
                    val age = 1 - u
                    val width = (M.Contrail.widthHead + (M.Contrail.widthTail - M.Contrail.widthHead) * age) * fz
                    val drift = M.Contrail.wobble * age * sin(s * 0.07 + tf * 2.2)
                    val nx = -sin(at.h)
                    val ny = cos(at.h)
                    val x = g.camX + (at.x + g.offX + nx * drift - g.camX) * fz
                    val y = g.camY + (at.y + g.offY + ny * drift - g.camY) * fz
                    pts[i * 4] = x + nx * width / 2
                    pts[i * 4 + 1] = y + ny * width / 2
                    pts[i * 4 + 2] = x - nx * width / 2
                    pts[i * 4 + 3] = y - ny * width / 2
                }
                f.trail = pts
            }
        }
        return f
    }

    /** Flat polygon (x, y pairs) of the sheet's rounded rectangle, or one half of it. */
    fun sheetOutline(w: Double, h: Double, radius: Double, side: Int): DoubleArray {
        val r = max(0.01, min(radius, h / 2))
        val out = ArrayList<Double>()
        fun point(x: Double, y: Double) {
            out.add(x)
            out.add(y)
        }
        fun arc(cx: Double, cy: Double, a0: Double, a1: Double) {
            val n = 10
            for (i in 0..n) {
                val a = (a0 + (a1 - a0) * i / n) * PI / 180
                point(cx + r * cos(a), cy + r * sin(a))
            }
        }
        when (side) {
            SIDE_FULL -> {
                arc(w - r, r, -90.0, 0.0)
                arc(w - r, h - r, 0.0, 90.0)
                arc(r, h - r, 90.0, 180.0)
                arc(r, r, 180.0, 270.0)
            }
            SIDE_LEFT -> {
                point(w / 2, 0.0)
                arc(r, r, 270.0, 180.0)
                arc(r, h - r, 180.0, 90.0)
                point(w / 2, h)
            }
            else -> {
                point(w / 2, h)
                arc(w - r, h - r, 90.0, 0.0)
                arc(w - r, r, 0.0, -90.0)
                point(w / 2, 0.0)
            }
        }
        return out.toDoubleArray()
    }

    const val SIDE_FULL = 0
    const val SIDE_LEFT = 1
    const val SIDE_RIGHT = 2

    /** The dart's four polygons in the left half's own coordinates (nose at x 0), faces 2...5. */
    fun dartOutlines(w: Double, h: Double): Array<DoubleArray> {
        val c = h / 2
        return arrayOf(
            doubleArrayOf(w / 2, 0.0, 0.0, c, w / 2, c),
            doubleArrayOf(w / 2, c, 0.0, c, w / 2, h),
            doubleArrayOf(w / 2, 0.0, 0.0, 0.0, 0.0, c),
            doubleArrayOf(w / 2, h, 0.0, h, 0.0, c),
        )
    }
}
