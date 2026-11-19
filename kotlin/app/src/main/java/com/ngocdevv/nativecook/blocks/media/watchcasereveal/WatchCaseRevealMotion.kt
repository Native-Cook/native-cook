package com.ngocdevv.nativecook.blocks.media.watchcasereveal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// The scene's fixed numbers and the film (watchScene.ts + watchMotion.ts). glTF space: y up, metres, the camera in
// front (+z). One loop drives the film, and one number drives the case: `open`, 0 (closed) .. 1 (lid open) .. 2
// (pillow up). Every value is a pure function of t (and open), so t = LOOP gives the same frame as t = 0.
//
//  0.0 –  1.2  poster: the closed walnut case, the camera drifting
//  1.2 –  1.36 the clasp lets go: the lid pops 2 deg (haptic)
//  1.36 – 3.7  the lid swings open on a damped hinge (soft haptic at rest)
//  3.7 –  4.7  the pillow rises 18 mm and tips the dial 15 deg to the lens (haptic at the top)
//  4.7 –  8.6  the watch turns to catch the light; the camera pushes in to the dial (5.4 – 6.8) and back (7.9 – 8.9);
//              the dial picker tries every dial: green at 5.5, silver at 6.9, back to blue at 8.2
//  8.5         "Reserve in a boutique" is pressed: it reads "Reserved" until 10.3
//  8.6 –  9.4  the pillow sinks back
//  9.4 – 11.3  the lid closes (soft haptic as it lands)
// 11.3 – 12.0  poster; last frame = first frame

/** The film's period in seconds (watchMotion.ts LOOP). */
const val LOOP = 12.0

internal object WcrScene {
    const val CASE_W = 0.124
    val LID_HINGE = floatArrayOf(0f, 0.052f, -0.056f)
    const val LID_OPEN = 104 * PI / 180
    const val CUSHION_Y = 0.031
    const val LIFT = 0.018
    const val TILT = 15 * PI / 180
    const val WATCH_Y = 0.0184
    val DIAL_C = doubleArrayOf(0.0, WATCH_Y + 0.008, 0.0)

    class Dial(val name: String, val color: Int, val metal: Float, val rough: Float)

    val DIALS = arrayOf(
        Dial("Blue", 0x173764, 0.55f, 0.3f),
        Dial("Green", 0x1E4A3B, 0.55f, 0.3f),
        Dial("Silver", 0xC3C7CC, 0.65f, 0.28f),
    )

    const val HFOV_DEG = 30.0
    const val CAMERA_NEAR = 0.01
    const val CAMERA_FAR = 10.0

    /** Shots: eye xyz, target xyz. */
    val POSTER = doubleArrayOf(0.12, 0.275, 0.325, 0.0, 0.028, 0.0)
    val OPEN = doubleArrayOf(0.101, 0.323, 0.336, 0.0, 0.06, 0.0)
    val TURN = doubleArrayOf(0.06, 0.202, 0.21, 0.0, 0.05, 0.0)
    val MACRO = doubleArrayOf(0.03, 0.153, 0.12, 0.0, 0.065, 0.004)

    const val IBL_INTENSITY = 30_000f
    val KEY_DIRECTION = floatArrayOf(0.4f, -0.85f, -0.35f)
    const val KEY_LUX = 72_000f
    val FILL_DIRECTION = floatArrayOf(-0.7f, -0.3f, -0.6f)
    const val FILL_LUX = 15_000f
    const val EXPOSURE = 0f

    const val BG_TOP = 0xFFF1EDE7.toInt()
    const val BG_BOTTOM = 0xFFE1DAD0.toInt()
    const val INK = 0xFF1A1917.toInt()
    const val MUTE = 0xFF68625A.toInt()

    const val BRAND = "Halden"
    const val MODEL = "Arden 39"
    const val LINE = "Automatic, steel on calf leather"
    const val PRICE = "\$2,450"

    class Spec(val value: Int, val unit: String, val label: String)

    val SPECS = arrayOf(Spec(39, "mm", "Case"), Spec(70, "h", "Power reserve"), Spec(100, "m", "Water resistant"))
}

internal object WcrMotion {
    const val LATCH = 1.2
    const val SWING = 1.36
    const val SWING_END = 3.7
    const val LIFT_END = 4.7
    const val TURN_START = 4.7
    const val TURN_END = 8.6
    const val MACRO_IN = 5.4
    const val MACRO_IN_END = 6.8
    const val MACRO_OUT = 7.9
    const val MACRO_OUT_END = 8.9
    const val SINK_END = 9.4
    const val CLOSE_END = 11.3
    const val RESERVE = 8.5
    const val RESERVED = 10.3

    val DIAL_STEPS = doubleArrayOf(5.5, 6.9, 8.2)
    const val RESERVED_TOUCH = 1.5
    const val DIAL_BLEND = 0.5
    const val IDLE = 2.0
    const val RETURN_DURATION = 2.0

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    fun easeInOutCubic(x: Double): Double = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOutCubic(x: Double): Double = 1 - (1 - x).pow(3)
    fun easeInOutSine(x: Double): Double = -(cos(PI * x) - 1) / 2
    fun inOutQuad(x: Double): Double = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2

    /** A CSS cubic-bezier(x1, y1, x2, y2) at x. */
    fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
        fun bx(u: Double) = 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u
        fun by(u: Double) = 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
        val xx = clamp01(x)
        var lo = 0.0
        var hi = 1.0
        repeat(30) {
            val m = (lo + hi) / 2
            if (bx(m) < xx) lo = m else hi = m
        }
        return by((lo + hi) / 2)
    }

    fun easeInOutTouch(x: Double): Double = bezier(0.65, 0.0, 0.35, 1.0, x)
    fun easeHinge(x: Double): Double = bezier(0.25, 0.8, 0.3, 1.0, x)

    /** A damped hinge released from rest (critically damped step response, normalised to land on 1). */
    fun hinge(u: Double): Double {
        val x = clamp01(u) * 6
        val end = 1 - 7 * exp(-6.0)
        return (1 - (1 + x) * exp(-x)) / end
    }

    fun filmOpen(t: Double): Double = when {
        t < LATCH -> 0.0
        t < SWING -> 0.022 * easeOutCubic(seg(t, LATCH, SWING))
        t < SWING_END -> 0.022 + 0.978 * hinge(seg(t, SWING, SWING_END))
        t < LIFT_END -> 1 + easeInOutCubic(seg(t, SWING_END, LIFT_END))
        t < TURN_END -> 2.0
        t < SINK_END -> 2 - easeInOutCubic(seg(t, TURN_END, SINK_END))
        t < CLOSE_END -> 1 - easeInOutCubic(seg(t, SINK_END, CLOSE_END))
        else -> 0.0
    }

    fun filmYaw(t: Double): Double {
        val u = seg(t, TURN_START, TURN_END)
        val env = (1 - cos(2 * PI * u)) / 2
        val deg = -22 * sin(2 * PI * u) * env * 1.6 + 4 * sin(PI * u) * env
        return deg * PI / 180
    }

    fun filmMacro(t: Double): Double =
        easeInOutSine(seg(t, MACRO_IN, MACRO_IN_END)) * (1 - easeInOutSine(seg(t, MACRO_OUT, MACRO_OUT_END)))

    /** The film's dial state from home: [from offset, to offset, blend 0..1]. */
    fun filmDial(t: Double, out: DoubleArray) {
        var k = -1
        for (i in DIAL_STEPS.indices) if (t >= DIAL_STEPS[i]) k = i
        if (k < 0) {
            out[0] = 0.0; out[1] = 0.0; out[2] = 0.0
            return
        }
        out[0] = k.toDouble()
        out[1] = (k + 1).toDouble()
        out[2] = easeInOutSine(seg(t, DIAL_STEPS[k], DIAL_STEPS[k] + DIAL_BLEND))
    }

    fun filmReserved(t: Double): Boolean = t >= RESERVE && t < RESERVED

    fun lidOf(o: Double): Double = clamp01(o)
    fun liftOf(o: Double): Double = easeInOutSine(seg(o, 1.0, 2.0))
    fun tipOf(o: Double): Double = easeInOutSine(seg(o, 1.3, 2.0))
    fun specsOf(o: Double): Double = easeOutCubic(seg(o, 0.6, 1.9))

    private fun blendShot(a: DoubleArray, b: DoubleArray, k: Double, out: DoubleArray) {
        val az0 = atan2(a[0], a[2])
        var az1 = atan2(b[0], b[2])
        if (az1 - az0 > PI) az1 -= 2 * PI
        if (az0 - az1 > PI) az1 += 2 * PI
        val r0 = hypot(a[0], a[2])
        val r1 = hypot(b[0], b[2])
        val az = lerp(az0, az1, k)
        val r = lerp(r0, r1, k)
        val ey = lerp(a[1], b[1], k)
        val tx = lerp(a[3], b[3], k)
        val ty = lerp(a[4], b[4], k)
        val tz = lerp(a[5], b[5], k)
        out[0] = sin(az) * r
        out[1] = ey
        out[2] = cos(az) * r
        out[3] = tx
        out[4] = ty
        out[5] = tz
    }

    /** Camera for a case state, the push to the dial (0..1) and the loop time: out = [eye xyz, target xyz]. */
    fun cameraAt(o: Double, macro: Double, t: Double, out: DoubleArray) {
        blendShot(WcrScene.POSTER, WcrScene.OPEN, easeInOutSine(seg(o, 0.15, 1.3)), out)
        blendShot(out.copyOf(), WcrScene.TURN, easeInOutSine(seg(o, 1.1, 2.0)), out)
        blendShot(out.copyOf(), WcrScene.MACRO, clamp01(macro), out)
        val d = sin(2 * PI * t / LOOP)
        val az = atan2(out[0] - out[3], out[2] - out[5]) + 0.05 * d
        val r = hypot(out[0] - out[3], out[2] - out[5])
        out[0] = out[3] + sin(az) * r
        out[2] = out[5] + cos(az) * r
        out[1] += 0.004 * d
    }

    fun cameraBasis(c: DoubleArray, width: Double, out: DoubleArray) {
        var fx = c[3] - c[0]
        var fy = c[4] - c[1]
        var fz = c[5] - c[2]
        val fl = sqrt(fx * fx + fy * fy + fz * fz).let { if (it == 0.0) 1.0 else it }
        fx /= fl; fy /= fl; fz /= fl
        var rx = -fz
        var rz = fx
        val rl = hypot(rx, rz).let { if (it == 0.0) 1.0 else it }
        rx /= rl; rz /= rl
        out[0] = fx; out[1] = fy; out[2] = fz
        out[3] = rx; out[4] = rz
        out[5] = -rz * fy
        out[6] = rz * fx - rx * fz
        out[7] = rx * fy
        out[8] = width / 2 / tan(WcrScene.HFOV_DEG * PI / 360)
    }

    fun project(c: DoubleArray, b: DoubleArray, w: Double, h: Double, px0: Double, py0: Double, pz0: Double, out: DoubleArray): Boolean {
        val px = px0 - c[0]
        val py = py0 - c[1]
        val pz = pz0 - c[2]
        val z = px * b[0] + py * b[1] + pz * b[2]
        out[2] = z
        if (z < 0.01) return false
        out[0] = w / 2 + (px * b[3] + pz * b[4]) / z * b[8]
        out[1] = h / 2 - (px * b[5] + py * b[6] + pz * b[7]) / z * b[8]
        return true
    }
}

/** What one frame shows: the film's, or a finger's. */
internal class WcrPose(
    val t: Double,
    val open: Double,
    val yaw: Double,
    val macro: Double,
    val dialFrom: Int,
    val dialTo: Int,
    val dialK: Double,
    val secs: Double,
    val pickerDial: Int,
    val reserved: Boolean,
    val filmPlaying: Boolean,
)

/**
 * The block's one source of truth (a port of the Swift controller, after WatchCaseReveal.tsx's modes): the film's
 * clock and whatever a finger set. `frame(now)` is a function of the stored fields and a time in seconds (the
 * Choreographer's frame time), read by the Filament stage and the Compose layers for the same vsync.
 */
internal class WcrController(autoPlay: Boolean, val reduced: Boolean) {
    private class Ease(val from: Double, val to: Double, val t0: Double, val dur: Double, val curve: (Double) -> Double = { it }) {
        fun at(now: Double): Double = if (dur <= 0) to else from + (to - from) * curve(WcrMotion.clamp01((now - t0) / dur))
        val end: Double get() = t0 + dur
    }

    /** A damped spring from x0 (with velocity v0) to 0, released at t0: damping 11, stiffness 55, mass 1. */
    private class Spring(val x0: Double, val v0: Double, val t0: Double) {
        fun at(now: Double): Double {
            val w = sqrt(55.0)
            val z = 11 / (2 * w)
            val wd = w * sqrt(1 - z * z)
            val t = maxOf(0.0, now - t0)
            return exp(-z * w * t) * (x0 * cos(wd * t) + (v0 + z * w * x0) / wd * sin(wd * t))
        }
    }

    val film = autoPlay && !reduced
    /** Set by the stage once the case has been drawn: the film holds its poster until then. */
    var ready = false
    private var auto = film
    private var start = Double.NaN
    private var home = 0
    private var open = Ease(if (reduced) 2.0 else 0.0, if (reduced) 2.0 else 0.0, 0.0, 0.0)
    private var yawEase = Ease(0.0, 0.0, 0.0, 0.0)
    private var yawSpring: Spring? = null
    private var macro = Ease(0.0, 0.0, 0.0, 0.0)
    private var dialFrom = 0
    private var dialTo = 0
    private var dialK = Ease(0.0, 0.0, 0.0, 0.0)
    private var secsAt = 0.0
    private var secsT0 = 0.0
    private var lastActivity = 0.0
    private var reservedUntil = -1.0
    var dragging = false
        private set
    private var dragKind = 0 // 0 none, 1 lid, 2 turn
    private var dragFrom = 0.0
    private var yawFrom = 0.0
    val isTurning: Boolean get() = dragging && dragKind == 2

    private var cacheTime = Double.NaN
    private var cache: WcrPose? = null

    private fun film(t: Double): WcrPose {
        val f = DoubleArray(3)
        WcrMotion.filmDial(t, f)
        val n = WcrScene.DIALS.size
        val from = (home + f[0].toInt()) % n
        val to = (home + f[1].toInt()) % n
        return WcrPose(
            t, WcrMotion.filmOpen(t), WcrMotion.filmYaw(t), WcrMotion.filmMacro(t), from, to, f[2], t,
            if (f[2] <= 0.001) from else to, WcrMotion.filmReserved(t), true,
        )
    }

    private fun endOfManual(): Double {
        var e = maxOf(lastActivity, open.end, macro.end, dialK.end, yawEase.end)
        yawSpring?.let { e = maxOf(e, it.t0 + 2.5) }
        return e
    }

    private fun manualYaw(now: Double) = yawSpring?.at(now) ?: yawEase.at(now)

    /** The pose at `now` (seconds); computed once per distinct time. */
    fun frame(now: Double): WcrPose {
        if (now == cacheTime) cache?.let { return it }
        val p = compute(now)
        cacheTime = now
        cache = p
        return p
    }

    private fun compute(now: Double): WcrPose {
        if (auto) {
            if (!ready) return film(0.0)
            if (start.isNaN()) start = now
            val t = maxOf(0.0, now - start) % LOOP
            return film(t)
        }
        var o = open.at(now)
        var yaw = manualYaw(now)
        var mac = macro.at(now)
        var df = dialFrom
        var dt = dialTo
        var dk = dialK.at(now)
        if (film && !dragging) {
            // 2 s after the last touch (and after what it set off) the case closes in 2 s, then the film starts again
            val r0 = endOfManual() + WcrMotion.IDLE
            if (now >= r0) {
                if (now >= r0 + WcrMotion.RETURN_DURATION) {
                    auto = true
                    start = r0 + WcrMotion.RETURN_DURATION
                    return compute(now)
                }
                val k = WcrMotion.easeInOutTouch(WcrMotion.clamp01((now - r0) / WcrMotion.RETURN_DURATION))
                val ao = open.at(r0)
                val ay = manualYaw(r0)
                val am = macro.at(r0)
                val ak = dialK.at(r0)
                o = ao * (1 - k)
                yaw = ay * (1 - k)
                mac = am * (1 - k)
                if (dialTo != home || ak < 1) {
                    df = if (ak >= 0.5) dialTo else dialFrom
                    dt = home
                    dk = WcrMotion.easeInOutSine(WcrMotion.clamp01((now - r0) / WcrMotion.DIAL_BLEND))
                }
            }
        }
        return WcrPose(
            0.0, o, yaw, mac, df, dt, dk, secsAt + (now - secsT0),
            if (dk <= 0.001) df else dt, now < reservedUntil, false,
        )
    }

    /** Leaves the film (or the ease back) where it is: the current pose becomes the manual values. */
    private fun freeze(now: Double) {
        val p = compute(now)
        secsAt = p.secs
        secsT0 = now
        auto = false
        open = Ease(p.open, p.open, 0.0, 0.0)
        yawSpring = null
        yawEase = Ease(p.yaw, p.yaw, 0.0, 0.0)
        macro = Ease(p.macro, p.macro, 0.0, 0.0)
        dialFrom = p.dialFrom
        dialTo = p.dialTo
        dialK = Ease(p.dialK, p.dialK, 0.0, 0.0)
        lastActivity = now
        cacheTime = Double.NaN
    }

    fun dragBegan(now: Double) {
        freeze(now)
        dragging = true
        dragKind = 0
        dragFrom = open.to
        yawFrom = yawEase.to
    }

    fun dragChanged(now: Double, dx: Double, dy: Double) {
        lastActivity = now
        cacheTime = Double.NaN
        if (dragKind == 0) {
            if (abs(dx) < 8 && abs(dy) < 8) return
            dragKind = if (abs(dx) > abs(dy) && dragFrom > 1.5) 2 else 1
        }
        if (dragKind == 1) {
            val v = (dragFrom - dy / LID_PT).coerceIn(0.0, 2.0)
            open = Ease(v, v, 0.0, 0.0)
        } else {
            var v = yawFrom + dx * YAW_PER_PT
            val over = abs(v) - YAW_LIMIT
            if (over > 0) v = (if (v < 0) -1 else 1) * (YAW_LIMIT + over / (1 + over * 2.5))
            yawEase = Ease(v, v, 0.0, 0.0)
        }
    }

    fun dragEnded(now: Double, vx: Double, vy: Double) {
        dragging = false
        lastActivity = now
        cacheTime = Double.NaN
        if (dragKind == 1) {
            val projected = open.to - vy / LID_PT * 0.25
            animateOpen(now, if (projected > 0.6) 2.0 else 0.0)
        } else if (dragKind == 2) {
            yawSpring = Spring(yawEase.to, vx * YAW_PER_PT, now)
        }
        dragKind = 0
    }

    private fun animateOpen(now: Double, target: Double) {
        val cur = open.at(now)
        val dur = if (reduced) 0.22 else maxOf(0.5, abs(target - cur) * 1.15)
        open = Ease(cur, target, now, dur, if (target > cur) WcrMotion::easeHinge else WcrMotion::easeInOutTouch)
    }

    fun tap(now: Double) {
        freeze(now)
        if (open.to < 0.5) animateOpen(now, 2.0)
    }

    fun pickDial(i: Int, now: Double) {
        freeze(now)
        home = i
        val k = dialK.to
        val current = if (k >= 0.5) dialTo else dialFrom
        if (current == i && (k >= 1 || k == 0.0)) return
        dialFrom = current
        dialTo = i
        dialK = Ease(0.0, 1.0, now, if (reduced) 0.2 else WcrMotion.DIAL_BLEND, WcrMotion::inOutQuad)
    }

    fun reserve(now: Double) {
        freeze(now)
        reservedUntil = now + WcrMotion.RESERVED_TOUCH
    }

    companion object {
        /** Points of vertical drag per unit of `open` (the lid is one unit, the pillow another). */
        const val LID_PT = 150.0
        const val YAW_PER_PT = 0.009
        const val YAW_LIMIT = 0.8
        val DETENT = 12 * PI / 180
    }
}
