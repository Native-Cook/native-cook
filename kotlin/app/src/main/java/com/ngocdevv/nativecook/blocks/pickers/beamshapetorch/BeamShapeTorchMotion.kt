package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The whole look at loop time t (RN `Look`). */
internal data class BeamShapeTorchLook(
    val b: Double, // brightness
    val w: Double, // spread (may overshoot a little below 0 or above 1)
    val m: Double, // capsule height 0 island .. 1 open
    val mw: Double, // capsule width
    val press: Double, // capsule scale
    val hot: Double, // lens heat: b with a thermal lag
    val phase: Double, // 0 .. 1 over the loop: the heat shimmer
    val after: Double, // 0 .. 1: the afterimage of a held pencil
)

/** The optics (design pt and degrees). */
internal class BeamShapeTorchOptics(
    val theta: Double, // half-angle, radians
    val reach: Double, // design pt from the lens to the solid arc's apex
    val gain: Double, // the core's intensity relative to the wide beam
    val hw: Double, // the solid arc's half-width
    val sag: Double, // how far its ends drop below the apex
    val reachMax: Double, // the dotted arc: where the beam reaches at full brightness for this spread
    val hwMax: Double,
    val sagMax: Double,
    val dots: Double, // the dotted arc's opacity
    val arcAlpha: Double, // the solid arc's opacity
    val glyph: Double, // the torch's level: 0.45 off .. 1 on
)

internal class BeamShapeTorchArc(
    val straight: Boolean,
    val cy: Double,
    val r: Double,
    val half: Double,
    val ex: Double,
    val ey: Double,
    val tx: Double,
    val ty: Double,
)

internal class BeamShapeTorchSpring(val damping: Double, val stiffness: Double, val mass: Double)

/**
 * The loop, the optics and the gesture constants of the RN block (`bst-motion.ts`), value for value. Every frame of the autoplay
 * is a pure function of the loop time (no accumulated state).
 *
 * The state is three numbers: b brightness 0..1 (a level: it never springs), w spread 0 (pencil) .. 1 (wide fan) (focus: it
 * settles with one overshoot), m the capsule 0 (closed into the island) .. 1 (open panel).
 *
 * Loop (LOOP = 9 s; payoff at 1.6 s): rest to .5, the beam squeezes to a pencil .5-1.54 and hunts focus once, the pencil lands on
 * the ceiling 1.43-3.1, the fan opens 3.1-4.35 leaving an afterimage, the lamp dims 4.3-5.1, the capsule collapses into the island
 * 5.2-6.3 and expands 6.3-7.2, the lamp ignites 6.8-7.95, rest to 9.
 */
internal object BeamShapeTorchMotion {
    const val LOOP = 9.0

    // ---- the loop (s) ----
    const val T_REST0 = 0.5
    const val T_PRESS_DOWN = 0.45
    const val T_PRESS_UP = 2.0
    const val T_SQUEEZE = 0.5
    const val T_SQUEEZE_END = 1.54
    const val T_HUNT_END = 2.2
    const val T_OPEN = 3.1
    const val T_DIM_A = 4.3
    const val T_DIM_B = 5.1
    const val T_COLLAPSE = 5.2
    const val T_EXPAND = 6.3
    const val T_IGNITE = 6.8
    const val T_REST_END = 7.95
    const val T_WIDTH_LAG = 0.05
    const val T_AFTERGLOW = 0.14
    const val T_AFTER_IN = 0.08
    const val T_AFTER_OUT_A = 0.08
    const val T_AFTER_OUT_B = 0.98

    /** Springs as (response s, damping ratio). */
    class Resp(val response: Double, val zeta: Double)
    val SPRING_SQUEEZE = Resp(1.6, 0.7)
    val SPRING_OPEN = Resp(1.0, 0.8)
    val SPRING_COLLAPSE = Resp(0.8, 0.9)
    val SPRING_EXPAND = Resp(0.75, 0.8)
    val SPRING_IGNITE = Resp(0.9, 1.0)
    val SPRING_PRESS = Resp(0.35, 0.85)

    val SNAP = BeamShapeTorchSpring(30.5, 322.3, 1.0)
    val POP = BeamShapeTorchSpring(19.0, 195.0, 1.0)

    // ---- gesture mapping (design pt; x S on screen) ----
    const val PT_PER_BRIGHTNESS = 220.0
    const val PT_PER_SPREAD = 120.0
    const val AXIS_LEAD = 10.0
    const val FOLLOW = 0.6
    const val PROJECT_B = 0.08
    const val PROJECT_W = 0.12
    const val RUBBER = 0.25
    const val OFF_BELOW = -0.06
    const val TAP_SLOP = 8.0
    const val TAP_TIME = 0.35
    const val RESUME_AFTER = 2.0
    const val PRESS_SCALE = 1.012
    const val PINCH_GAIN = 1.1
    const val TICK_B = 0.1
    const val TICK_THETA = 5.0
    const val TICK_GAP = 0.06
    const val TICK_GAP_FAST = 0.035
    const val TICK_FAST_DEG = 60.0
    const val SUN_HIT = 22.0
    const val SUN_GLIDE = 0.12
    const val SUN_NEAR = 12.0
    const val CEILING_ON = 205.0
    const val CEILING_OFF = 190.0
    const val DWELL = 1.2
    const val AFTER_FADE = 0.9

    // ---- the optics ----
    const val THETA_WIDE = 40.0
    const val THETA_NARROW = 4.0
    const val REACH_WIDE = 135.0
    const val NARROW_GAIN = 1.59
    const val REACH_OFF = 22.0
    const val OFF_HALF = 20.0
    const val HW_WIDE = 72.0 / 135.0
    const val HW_NARROW = 13.0 / 236.0
    const val SAG_WIDE = 34.0 / 135.0
    const val HOT_MAX = 7.5

    // ---- the page follows the hero ----
    const val SPILL_GLOW = 0.1
    const val SPILL_GLOW_RADIUS = 120.0
    const val SPILL_GLOW_FROM = 108.0
    const val SPILL_HALO = 0.22
    const val SPILL_HALO_RADIUS = 60.0
    const val SPILL_DARK = 0.12
    const val SHADOW_OFF = 0.42
    const val SHADOW_ON = 0.28

    /** How long the afterimage of a held pencil is. */
    const val AFTER_REACH = 205.0

    const val FOCUS_LOCK_DELAY = 0.26

    // ---- helpers ----
    fun clamp01(x: Double): Double = min(max(x, 0.0), 1.0)
    fun mix(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    fun smooth(a: Double, b: Double, x: Double): Double {
        val k = clamp01((x - a) / (b - a))
        return k * k * (3 - 2 * k)
    }

    /** 0 -> 1 step response of a spring (response s, damping ratio). */
    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = (2 * PI) / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    /** A timeline spring (response, damping ratio) as a mass-1 damped spring, so the finger moves alike. */
    fun springCfg(response: Double, zeta: Double): BeamShapeTorchSpring {
        val k = ((2 * PI) / response).pow(2)
        return BeamShapeTorchSpring(2 * zeta * sqrt(k), k, 1.0)
    }

    /** Rubber band past an end: `over` beyond the limit shows as less and less, in units of the value. */
    fun rubber(x: Double): Double {
        val k = RUBBER
        if (x < 0) return -(0.55 * -x) / (1 + -x / k)
        if (x > 1) return 1 + (0.55 * (x - 1)) / (1 + (x - 1) / k)
        return x
    }

    /** Cubic bezier easing (x1, y1, x2, y2), solved by Newton steps. */
    fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        var u = x
        for (i in 0 until 6) {
            val cx = 3 * x1 * u * (1 - u) * (1 - u) + 3 * x2 * u * u * (1 - u) + u * u * u - x
            val dx = 3 * x1 * (1 - u) * (1 - 4 * u + 3 * u * u) + 3 * x2 * u * (2 - 3 * u) + 3 * u * u
            if (abs(dx) < 1e-6) break
            u = min(max(u - cx / dx, 0.0), 1.0)
        }
        return 3 * y1 * u * (1 - u) * (1 - u) + 3 * y2 * u * u * (1 - u) + u * u * u
    }

    fun inOut(x: Double): Double = bezier(0.65, 0.0, 0.35, 1.0, x)

    // ---- the timeline: one pure function per value ----
    fun spreadAt(t: Double): Double {
        if (t <= T_SQUEEZE) return 1.0
        if (t < T_SQUEEZE_END) return 1 - ((t - T_SQUEEZE) / (T_SQUEEZE_END - T_SQUEEZE)).pow(1.8)
        if (t < T_OPEN) {
            // One pop impulse from focus-ring momentum; peak -.046 at 1.62 s, then it rests as a pencil.
            val dt = t - T_SQUEEZE_END
            val omega = sqrt(POP.stiffness / POP.mass)
            val decay = POP.damping / (2 * POP.mass)
            val wd = sqrt(omega * omega - decay * decay)
            val hunt = (-1.37 / wd) * exp(-decay * dt) * sin(wd * dt)
            return hunt * (1 - smooth(T_HUNT_END - 0.15, T_HUNT_END, t))
        }
        return springAt(t - T_OPEN, SPRING_OPEN.response, SPRING_OPEN.zeta)
    }

    fun brightnessAt(t: Double): Double {
        if (t < T_DIM_A) return 1.0
        if (t < T_IGNITE) return 1 - inOut((t - T_DIM_A) / (T_DIM_B - T_DIM_A))
        return springAt(t - T_IGNITE, SPRING_IGNITE.response, SPRING_IGNITE.zeta)
    }

    /** The capsule's height (lead): its width is the same curve `T_WIDTH_LAG` later. */
    fun morphAt(t: Double): Double {
        if (t < T_EXPAND) return 1 - springAt(t - T_COLLAPSE, SPRING_COLLAPSE.response, SPRING_COLLAPSE.zeta)
        return springAt(t - T_EXPAND, SPRING_EXPAND.response, SPRING_EXPAND.zeta)
    }

    /** The afterimage: a held pencil leaves a dark lilac stripe in the fan as it opens (80 ms in, fading over 0.9 s). */
    fun afterAt(t: Double): Double {
        val k = t - T_OPEN
        if (k < 0 || k > T_AFTER_OUT_B) return 0.0
        return smooth(0.0, T_AFTER_IN, k) * (1 - smooth(T_AFTER_OUT_A, T_AFTER_OUT_B, k))
    }

    fun pressAt(t: Double): Double {
        val r = SPRING_PRESS.response
        val z = SPRING_PRESS.zeta
        return 1 + (PRESS_SCALE - 1) * (springAt(t - T_PRESS_DOWN, r, z) - springAt(t - T_PRESS_UP, r, z))
    }

    val REST = BeamShapeTorchLook(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0)

    /** The whole look at loop time t. */
    fun lookAt(t: Double): BeamShapeTorchLook {
        val tt = ((t % LOOP) + LOOP) % LOOP
        if (tt >= T_REST_END) return REST
        val b = brightnessAt(tt)
        val settle = smooth(T_REST_END - 0.3, T_REST_END, tt)
        return BeamShapeTorchLook(
            b = mix(b, REST.b, settle),
            w = mix(spreadAt(tt), REST.w, settle),
            m = mix(morphAt(tt), REST.m, settle),
            mw = mix(morphAt(tt - T_WIDTH_LAG), REST.mw, settle),
            press = mix(pressAt(tt), REST.press, settle),
            hot = mix(max(b, 0.85 * brightnessAt(tt - T_AFTERGLOW)), REST.hot, settle),
            phase = tt / LOOP,
            after = afterAt(tt),
        )
    }

    fun opticsOf(b: Double, w: Double, aMin: Double = THETA_NARROW, aMax: Double = THETA_WIDE): BeamShapeTorchOptics {
        val wc = min(max(w, -0.08), 1.06)
        val deg = max(mix(aMin, aMax, wc), 1.2)
        val theta = (deg * PI) / 180
        val w01 = clamp01(w)
        val reachMax = REACH_WIDE * mix(NARROW_GAIN, 1.0, w01)
        val on = smooth(0.0, 0.3, b)
        val reach = mix(REACH_OFF, reachMax, clamp01(b))
        val kHw = mix(HW_NARROW, HW_WIDE, w01)
        val curve = w01.pow(1.5) * SAG_WIDE
        // the gain: flux b through the solid angle 2 pi (1 - cos theta), relative to the wide beam, compressed
        val thetaWide = (aMax * PI) / 180
        val ratio = (1 - cos(thetaWide)) / max(1 - cos(max(theta, 0.035)), 1e-5)
        val gain = min(ratio.pow(0.45), HOT_MAX)
        return BeamShapeTorchOptics(
            theta = theta,
            reach = reach,
            gain = gain,
            hw = mix(OFF_HALF, reach * kHw, on),
            sag = reach * curve * on,
            reachMax = reachMax,
            hwMax = reachMax * kHw,
            sagMax = reachMax * curve,
            dots = (1 - smooth(0.86, 0.97, b)) * smooth(0.03, 0.14, b),
            arcAlpha = mix(0.5, 1.0, on),
            glyph = mix(0.45, 1.0, smooth(0.0, 0.25, b)),
        )
    }

    /**
     * The arc through (cx, apexY) with half-width hw and sag s, as a circle: centre y and radius, plus its right end and the unit
     * tangent there. A sag under 0.3 pt is a straight bar.
     */
    fun arcGeometry(cx: Double, apexY: Double, hw: Double, sag: Double): BeamShapeTorchArc {
        val h = max(hw, 0.5)
        if (sag < 0.3) return BeamShapeTorchArc(true, apexY, 0.0, 0.0, cx + h, apexY, 1.0, 0.0)
        val r = (h * h + sag * sag) / (2 * sag)
        val cy = apexY + r
        val half = asin(min(h / r, 1.0))
        val ex = cx + r * sin(half)
        val ey = cy - r * cos(half)
        return BeamShapeTorchArc(false, cy, r, half, ex, ey, cos(half), sin(half))
    }

    /** How much the pencil has landed on the ceiling, 0 .. 1. */
    fun landing(reach: Double, w: Double, b: Double): Double =
        smooth(190.0, 212.0, reach) * smooth(0.93, 1.0, 1 - clamp01(w)) * clamp01(b)

    class XY(val x: Double, val y: Double)

    /** Where the sun sits for (b, w), in design pt from the lens: x to the right, y up. */
    fun sunOffset(b: Double, w: Double, aMin: Double, aMax: Double, along: Double): XY {
        val o = opticsOf(b, w, aMin, aMax)
        val g = arcGeometry(0.0, 0.0, o.hw, o.sag)
        return XY(g.ex + g.tx * along, o.reach - g.ey - g.ty * along)
    }

    class Sun(val b: Double, val w: Double)

    /** Polar sun control law, calibrated to the pin's non-polar arc at touch-down. */
    fun solveSun(fx: Double, fy: Double, b0: Double, w0: Double, aMin: Double, aMax: Double, along: Double): Sun {
        val atGrab = sunOffset(clamp01(b0), clamp01(w0), aMin, aMax, along)
        val o = opticsOf(b0, w0, aMin, aMax)
        val phi = o.theta * 0.89
        val rho = o.reach + 19
        val x = fx + rho * sin(phi) - atGrab.x
        val y = fy + rho * cos(phi) - atGrab.y
        val thetaDeg = (atan2(abs(x), y) * 180) / (PI * 0.89)
        val w = (thetaDeg - aMin) / max(aMax - aMin, 1.0)
        val maxReach = opticsOf(1.0, clamp01(w), aMin, aMax).reachMax
        val reach = hypot(x, y) - 19
        return Sun((reach - REACH_OFF) / (maxReach - REACH_OFF), w)
    }
}
