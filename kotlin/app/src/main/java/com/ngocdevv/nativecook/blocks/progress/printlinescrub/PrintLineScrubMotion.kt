package com.ngocdevv.nativecook.blocks.progress.printlinescrub

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** One frame of the loop (RN `Look`). */
internal data class PrintLineScrubLook(
    val h: Double, // cut height
    val duck: Double, // pt the capsule sits below the line, 0..duckPt
    val yaw: Double, // radians
    val heat: Double, // 0..1
    val band: Double, // pt
    val press: Double, // 0..1: touch-down state (lifted, label "Layer n")
    val bead: Double, // bead azimuth (rad)
    val speed: Double, // pt/s of the line
    val flash: Double, // 0..1 progress of the arrival flash (or -1)
    val over: Double, // dots overshoot, pt (signed)
    val held: Double, // 0..1: label crossfade
)

internal class PrintLineScrubSpring(val damping: Double, val stiffness: Double, val mass: Double)

/**
 * Every motion value of the RN block (`pls-motion.ts`). One loop time drives the autoplay ([lookAt]), a finger drives the same values
 * live; the physics on release are closed-form springs, so the loop and a real release are the same maths.
 *
 *   0.0-0.4 rest (the pin's frame; a warm pulse through the frost every 1.4 s); 0.4-0.65 press: the capsule ducks under the line, the
 *   gizmo stretches, the label becomes "Layer n / 480"; 0.5-1.5 drag down to the hips; 1.5-2.3 hold, tilt to 3.5 deg; 2.3-3.6 drag up
 *   through the waist to the shoulders, the numbers race through the head; 3.6 release: the plane catches up to the live line with the
 *   release velocity; 4.3-4.7 the capsule covers the seam again; 4.4-5.1 the dots overshoot, re-pack, flash; 4.6-6.2 the last layers
 *   cool; 6.2-7.6 rest.
 */
internal object PrintLineScrubMotion {
    const val LOOP = 7.6
    const val LAYERS = 480
    /** the live line, world height (the pin's seam at screen y 470 of a 690 -> 190 axis). */
    const val H_LIVE = 0.44
    /** resting percentage at the live line: round(layerAt(H_LIVE) / LAYERS * 100) = round(211 / 480 * 100) */
    const val PCT_LIVE = 44.0

    val SPRING_CATCH_UP = PrintLineScrubSpring(20.0, 90.0, 1.2)
    val SPRING_YAW = PrintLineScrubSpring(16.0, 120.0, 1.0)
    val SPRING_DUCK = PrintLineScrubSpring(18.0, 260.0, 1.0)
    val SPRING_TOUCH = PrintLineScrubSpring(22.0, 380.0, 1.0)

    const val DUCK_PT = 62.0
    const val YAW_PER_PT = 0.03
    const val YAW_MAX = 4.0
    const val RUBBER_PT = 6.0
    const val RUBBER_SLACK = 120.0
    const val IDLE_TO_LOOP = 2.0
    const val TICK_EVERY = 10

    const val HEAT_GAIN = 1.6
    const val HEAT_TAU = 0.5
    const val HEAT_TAU_V = 1200.0
    const val BAND_MIN = 14.0
    const val BAND_MAX = 40.0
    const val SPEED_FULL = 1400.0

    /** s per lap while held (a warm pulse at rest) */
    const val BEAD_PERIOD = 1.4
    const val DOTS_TAIL = 22.0

    const val T_PRESS = 0.4
    const val T_PRESS_END = 0.65
    const val T_DRAG_DOWN0 = 0.5
    const val T_DRAG_DOWN1 = 1.5
    const val T_HOLD1 = 2.3
    const val T_DRAG_UP1 = 3.6
    const val T_RELEASE = 3.6
    const val T_DUCK_BACK = 4.3
    const val T_FLASH = 4.55
    const val T_COOL = 6.2
    const val H_KNEES = 0.3
    const val H_SHOULDERS = 0.6
    const val YAW_HOLD = 3.5
    const val YAW_UP = 1.0

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun mix(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    fun easeInOut(x: Double): Double {
        val k = clamp01(x)
        return k * k * (3 - 2 * k)
    }

    /** past the ends the position gives way: limit + over / (1 + over / slack) */
    fun rubber(over: Double, slack: Double): Double = over / (1 + over / slack)

    /** Displacement of a damped spring from its target after `t` seconds, released at displacement x0 with velocity v0. */
    fun springDisp(t: Double, x0: Double, v0: Double, damping: Double, stiffness: Double, mass: Double): Double {
        if (t <= 0) return x0
        val w0 = sqrt(stiffness / mass)
        val z = damping / (2 * sqrt(stiffness * mass))
        if (z < 0.9999) {
            val wd = w0 * sqrt(1 - z * z)
            return exp(-z * w0 * t) * (x0 * cos(wd * t) + ((v0 + z * w0 * x0) / wd) * sin(wd * t))
        }
        if (z > 1.0001) {
            val s = w0 * sqrt(z * z - 1)
            val r1 = -z * w0 + s
            val r2 = -z * w0 - s
            val c2 = (v0 - r1 * x0) / (r2 - r1)
            val c1 = x0 - c2
            return c1 * exp(r1 * t) + c2 * exp(r2 * t)
        }
        return exp(-w0 * t) * (x0 + (v0 + w0 * x0) * t)
    }

    fun springDisp(t: Double, x0: Double, v0: Double, s: PrintLineScrubSpring): Double =
        springDisp(t, x0, v0, s.damping, s.stiffness, s.mass)

    /** velocity of the spring above (central difference, enough for a hand-off) */
    fun springVel(t: Double, x0: Double, v0: Double, s: PrintLineScrubSpring): Double {
        val e = 0.004
        return (springDisp(t + e, x0, v0, s) - springDisp(max(t - e, 0.0), x0, v0, s)) / (if (t < e) t + e else 2 * e)
    }

    // ---- the figure's volume table: the pin's calibrated progress curve (an illustrative lookup, not physical volume) ----
    private val VOLUME = doubleArrayOf(
        0.0, 0.0095, 0.0242, 0.037, 0.0461, 0.0515, 0.0565, 0.0624, 0.0689, 0.0763, 0.0843, 0.0929,
        0.1026, 0.1133, 0.1247, 0.1366, 0.1488, 0.1616, 0.1747, 0.1879, 0.2016, 0.2142, 0.2259, 0.237,
        0.2473, 0.2566, 0.2653, 0.2731, 0.28, 0.2859, 0.2918, 0.2983, 0.3066, 0.3182, 0.3329, 0.3504,
        0.3702, 0.3906, 0.4124, 0.4361, 0.4633, 0.492, 0.5222, 0.5543, 0.5887, 0.6244, 0.6615, 0.6994,
        0.7385, 0.7767, 0.8138, 0.8492, 0.8823, 0.9126, 0.9392, 0.9615, 0.9788, 0.9908, 0.9962, 0.9988,
        0.9998, 1.0, 1.0, 1.0, 1.0,
    )
    private val N = VOLUME.size - 1

    fun volumeBelow(h: Double): Double {
        val k = clamp01(h) * N
        val i = min(floor(k).toInt(), N - 1)
        return mix(VOLUME[i], VOLUME[i + 1], k - i)
    }

    fun layerAt(h: Double): Int = round(clamp01(h) * LAYERS).toInt()

    /**
     * Progress at cut height h: the layer's share of the job, so the percentage always equals round(layer / LAYERS * 100) and never contradicts
     * "Layer n / 480". The dotted bar and the ground tint follow the same value.
     */
    fun percentAt(h: Double): Double = layerAt(h).toDouble() / LAYERS * 100

    val REST_LOOK = PrintLineScrubLook(H_LIVE, 0.0, 0.0, 0.0, BAND_MIN, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0)

    /** the finger's path of the line before release, as a function of loop time */
    private fun pathH(t: Double): Double {
        if (t < T_DRAG_DOWN0) return H_LIVE
        if (t < T_DRAG_DOWN1) return mix(H_LIVE, H_KNEES, easeInOut(seg(t, T_DRAG_DOWN0, T_DRAG_DOWN1)))
        if (t < T_HOLD1) return H_KNEES
        return mix(H_KNEES, H_SHOULDERS, easeInOut(seg(t, T_HOLD1, T_DRAG_UP1)))
    }

    /** the loop's state at loop time t (a pure function: first and last frames are the pin's) */
    fun lookAt(tIn: Double): PrintLineScrubLook {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        fun speedPt(h0: Double, h1: Double, dt: Double) = (abs(h1 - h0) / dt) * 500
        var h = H_LIVE
        var speed = 0.0
        var duck = 0.0
        var press = 0.0
        var yaw = 0.0
        var heat = 0.0
        var flash = -1.0
        var over = 0.0
        var held = 0.0
        val duckMax = DUCK_PT
        if (t < T_RELEASE) {
            h = pathH(t)
            speed = speedPt(pathH(t - 0.01), pathH(t + 0.01), 0.02)
            val p = seg(t, T_PRESS, T_PRESS_END)
            press = 1 - (1 - p).pow(3)
            // the duck is a spring: anticipation then settle
            duck = duckMax * (1 + springDisp(max(t - T_PRESS, 0.0), -1.0, 0.0, SPRING_DUCK))
            if (t < T_PRESS) duck = 0.0
            held = seg(t, T_PRESS, T_PRESS + 0.2)
            val yawT = if (t < T_HOLD1) YAW_HOLD else mix(YAW_HOLD, YAW_UP, easeInOut(seg(t, T_HOLD1, T_DRAG_UP1)))
            yaw = (yawT * (1 + springDisp(max(t - 1.0, 0.0), -1.0, 0.0, SPRING_YAW)) * PI) / 180
            if (t < 1.0) yaw = 0.0
            // the first half second of the drag winds the heat up a little: playing with it warms the print
            heat = clamp01(abs(h - H_LIVE) * HEAT_GAIN + 0.12 * press)
        } else {
            val tr = t - T_RELEASE
            val h0 = pathH(T_RELEASE - 0.001)
            val v0 = (pathH(T_RELEASE - 0.0005) - pathH(T_RELEASE - 0.0015)) / 0.001 // world units / s at release
            val disp = springDisp(tr, h0 - H_LIVE, v0, SPRING_CATCH_UP)
            h = H_LIVE + disp
            val vel = springVel(tr, h0 - H_LIVE, v0, SPRING_CATCH_UP)
            speed = abs(vel) * 500
            held = 1 - seg(t, T_DUCK_BACK + 0.1, T_DUCK_BACK + 0.35)
            press = 1 - seg(t, T_RELEASE, T_RELEASE + 0.4)
            // capsule covers the seam again after the line has arrived
            val td = max(t - T_DUCK_BACK, 0.0)
            duck = if (t < T_DUCK_BACK) duckMax else max(duckMax * springDisp(td, 1.0, 0.0, SPRING_DUCK), -4.0)
            yaw = springDisp(tr, (YAW_UP * PI) / 180, 0.0, SPRING_YAW)
            // thermal memory: the release leaves the top layers glowing, decays over tau
            val heat0 = clamp01(abs(h0 - H_LIVE) * HEAT_GAIN + 0.12)
            val tau = HEAT_TAU * (1 + abs(v0 * 500) / HEAT_TAU_V)
            heat = heat0 * exp(-tr / tau) * (1 - seg(t, T_COOL, 7.0))
            // the dots: overshoot and flash when the line has arrived
            val fl = (t - T_FLASH) / 0.6
            if (fl >= 0 && fl <= 1) flash = fl
            over = if (t < T_FLASH) 0.0
            else 5 * seg(t, T_FLASH, T_FLASH + 0.05) * exp(-(t - T_FLASH) * 7) * cos((t - T_FLASH) * 24) * (if (t < 5.3) 1.0 else 0.0)
        }
        val bandSpeed = clamp01(speed / SPEED_FULL)
        val band = mix(BAND_MIN, BAND_MAX, max(clamp01(heat), bandSpeed))
        // the bead runs while held and during the return; five laps per loop, so the seam is exact
        val bead = (t / (LOOP / 5)) * PI * 2
        return PrintLineScrubLook(h, duck, yaw, heat, band, press, bead, speed, flash, over, held)
    }
}
