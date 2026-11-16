package com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** One frame of the pear: everything the drawing reads. Mirrors RN `Look`. */
internal data class ObjectSigninDissolveLook(
    val yaw: Double, // deg
    val c: Double, // cut fraction, 0 solid .. 1 all shell
    val roll: Double, // rad
    val pitchP: Double, // rad
    val scale: Double,
    val wipeP: Double, // 0 dark .. 1 light
    val rate: Double, // deg/s
    val seam: Double,
    val key: Double, // rad
    val zip: Double,
)

/** The finger's live state, stepped every frame. Mirrors RN `Live`. */
internal data class ObjectSigninDissolveLive(
    val yaw: Double,
    val yawV: Double,
    val yawTarget: Double,
    val c: Double,
    val cV: Double,
    val roll: Double,
    val rollV: Double,
    val pitchP: Double,
    val pitchV: Double,
    val scale: Double,
    val scaleV: Double,
    val q: Double,
    val qTarget: Double,
)

internal data class ObjectSigninDissolveSpring(val damping: Double, val stiffness: Double, val mass: Double)

/**
 * Every motion value of the RN block (`osd-motion.ts`), value for value. Frame t of the autoplay loop is a pure function of
 * t (springs are solved analytically); the finger drives the same variables through [stepLive]. Angles in degrees unless named rad.
 */
internal object ObjectSigninDissolveMotion {
    const val LOOP = 8.0

    // ---- the loop (s) ----
    const val T_TOUCH = 0.6
    const val T_DRAG_A = 0.7
    const val T_DRAG_B = 1.8
    const val T_RELEASE = 2.3
    const val T_WIPE_IN_A = 3.0
    const val T_WIPE_IN_B = 3.9
    const val T_FLICK_DOWN = 4.0
    const val T_WIPE_OUT_A = 5.6
    const val T_WIPE_OUT_B = 6.5

    // ---- poses (deg) ----
    const val HERO_A = -18.0
    const val HERO_B = 162.0
    const val YAW0 = HERO_A
    const val DRAG_TURN = 200.0
    const val FLICK1 = 520.0
    const val FLICK2 = 4800.0
    const val FRICTION = 6.02

    // ---- the cut ----
    const val CUT_FINGER0 = 0.25
    const val CUT_WAIST = 0.55
    const val CUT_RUBBER = 0.35
    const val CUT_RUBBER_MAX = 20.0
    const val PLANE_TOP = -214.0
    const val PLANE_BOTTOM = 196.0
    const val PLANE_SPAN = PLANE_BOTTOM - PLANE_TOP

    // ---- gesture ----
    const val DEG_PER_PT = 0.55
    const val TAP_SLOP = 8.0
    const val TAP_TIME = 0.18
    const val MAX_TURNS = 3.0
    const val RESUME_AFTER = 2.0
    const val TICK_EVERY = 25.0
    const val TICK_GAP = 0.1
    const val BANK = 0.012
    const val BANK_MAX = 12.0
    const val PITCH = 0.03
    const val PITCH_MAX = 8.0
    const val SPIN_LO = 540.0
    const val SPIN_HI = 1260.0
    const val BREATHE = 1.5
    const val BREATHE_HZ = 0.5
    const val BREATHE_AFTER = 0.6
    const val PRESS = 1.02

    // ---- springs ----
    val SPRING_TOUCH = ObjectSigninDissolveSpring(18.0, 300.0, 1.0)
    val SPRING_PLANE = ObjectSigninDissolveSpring(20.0, 240.0, 1.0)
    val SPRING_HEAL = ObjectSigninDissolveSpring(22.0, 160.0, 1.0)
    val SPRING_HOME = ObjectSigninDissolveSpring(16.0, 90.0, 1.0)
    val SPRING_BANK = ObjectSigninDissolveSpring(12.0, 160.0, 1.0)

    // ---- the wipe ----
    const val WIPE_MS = 880.0
    const val WIPE_CX = -80.0
    const val WIPE_DROP = 80.0 // the disc's centre is this far below the screen's bottom edge

    // ---- the page answers the shell ----
    const val LAG_POOL = 0.0
    const val LAG_WORD = 0.06
    const val LAG_PILL = 0.12
    const val PULSE_POOL = 0.42
    const val PULSE_WORD = 0.24
    const val PULSE_R0 = 190.0
    const val PULSE_R1 = 230.0
    const val PULSE_A = 0.06
    val ZIPS = doubleArrayOf(2.62, 4.78)

    /** JS `Math.round`: halves go up. */
    fun jsRound(x: Double): Double = floor(x + 0.5)

    fun planeY(c: Double): Double = PLANE_TOP + (PLANE_BOTTOM - PLANE_TOP) * c

    fun clamp(x: Double, a: Double, b: Double): Double = min(max(x, a), b)
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a), 0.0, 1.0)
        return t * t * (3 - 2 * t)
    }
    fun mix(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    fun easeInOut(t: Double): Double {
        val x = clamp(t, 0.0, 1.0)
        return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    }

    /** Damped spring x(t) from x0 with velocity v0 toward `to`: position only. */
    fun springPos(t: Double, x0: Double, v0: Double, to: Double, k: Double, c: Double): Double {
        if (t <= 0) return x0
        val w = sqrt(k)
        val z = c / (2 * w)
        val e = x0 - to
        if (z < 1) {
            val wd = w * sqrt(1 - z * z)
            return to + exp(-z * w * t) * (e * cos(wd * t) + ((v0 + z * w * e) / wd) * sin(wd * t))
        }
        return to + exp(-w * t) * (e + (v0 + w * e) * t)
    }
    fun springVel(t: Double, x0: Double, v0: Double, to: Double, k: Double, c: Double): Double {
        val h = 1.0 / 240
        return (springPos(t + h, x0, v0, to, k, c) - springPos(t - h, x0, v0, to, k, c)) / (2 * h)
    }
    fun stepAt(t: Double, s: ObjectSigninDissolveSpring): Double = springPos(t, 0.0, 0.0, 1.0, s.stiffness / s.mass, s.damping / s.mass)

    /** The hero angle the pear will rest at if released at yaw with rate v. */
    fun heroNear(yaw: Double, v: Double): Double {
        val proj = yaw + clamp(v / FRICTION, -MAX_TURNS * 360, MAX_TURNS * 360)
        return HERO_A + jsRound((proj - HERO_A) / 180) * 180
    }

    fun spinShell(rate: Double): Double = smoothstep(SPIN_LO, SPIN_HI, abs(rate))
    fun rollOf(rate: Double): Double = clamp(rate * BANK, -BANK_MAX, BANK_MAX) * PI / 180
    fun pitchOf(planeVel: Double): Double = clamp(planeVel * PITCH, -PITCH_MAX, PITCH_MAX) * PI / 180

    /** Rubber band past the stalk / the base. */
    fun rubberC(c: Double): Double {
        if (c in 0.0..1.0) return c
        val over = (if (c < 0) -c else c - 1) * PLANE_SPAN
        val give = (CUT_RUBBER * over) / (1 + (CUT_RUBBER * over) / CUT_RUBBER_MAX)
        return if (c < 0) -give / PLANE_SPAN else 1 + give / PLANE_SPAN
    }

    /** The radius that reaches the far corner of a W x H screen from the disc's centre. */
    fun wipeReach(w: Double, h: Double): Double = hypot(w + 80, h + 80) + 6
    fun wipeRadius(p: Double, r: Double): Double = r * easeInOut(p)

    // ---- timeline ----
    private fun finger(t: Double): Double = mix(CUT_FINGER0, CUT_WAIST, easeInOut((t - T_DRAG_A) / (T_DRAG_B - T_DRAG_A)))
    private fun yawDrag(t: Double): Double = YAW0 + DRAG_TURN * easeInOut((t - T_DRAG_A) / (T_DRAG_B - T_DRAG_A))
    private const val Y_AT_RELEASE = YAW0 + DRAG_TURN
    private val TARGET1 = heroNear(Y_AT_RELEASE, FLICK1)
    private const val FLICK2_AT = T_FLICK_DOWN + 0.1
    private fun yawA(t: Double): Double =
        springPos(t - T_RELEASE, Y_AT_RELEASE, FLICK1, TARGET1, SPRING_HOME.stiffness, SPRING_HOME.damping)
    private val Y_AT_FLICK2 = TARGET1
    private val TARGET2 = heroNear(Y_AT_FLICK2, FLICK2)
    private fun yawB(t: Double): Double =
        springPos(t - FLICK2_AT, Y_AT_FLICK2, FLICK2, TARGET2, SPRING_HOME.stiffness, SPRING_HOME.damping)

    fun yawAt(t: Double): Double {
        if (t < T_DRAG_A) return YAW0
        if (t < T_RELEASE) return yawDrag(min(t, T_DRAG_B))
        if (t < FLICK2_AT) return yawA(t)
        return yawB(t)
    }

    private fun cDrag(t: Double): Double = finger(min(t, T_DRAG_B)) * clamp(stepAt(t - T_TOUCH, SPRING_PLANE), 0.0, 1.2)
    private const val C_REL = CUT_WAIST
    private fun cAt(t: Double): Double {
        if (t < T_TOUCH) return 0.0
        if (t < T_RELEASE) return cDrag(t)
        if (t < FLICK2_AT - 0.1) return max(C_REL * (1 - stepAt(t - T_RELEASE, SPRING_HEAL)), 0.0)
        if (t < T_FLICK_DOWN) return 0.0
        // the hard flick: the skin lets go as the spin passes 540 deg/s and zips back from the base as it falls under it
        var acc = 0.0
        var wsum = 0.0
        for (j in 0 until 36) {
            val tau = t - j * 0.0125
            val w = exp(-(j * 0.0125) / 0.11)
            wsum += w
            if (tau < T_FLICK_DOWN) continue
            val rate = abs(springVel(tau - FLICK2_AT, Y_AT_FLICK2, FLICK2, TARGET2, SPRING_HOME.stiffness, SPRING_HOME.damping))
            acc += w * (if (tau < FLICK2_AT) 0.0 else spinShell(rate))
        }
        return acc / wsum
    }

    private fun wipeIn(t: Double) = clamp((t - T_WIPE_IN_A) / (T_WIPE_IN_B - T_WIPE_IN_A), 0.0, 1.0)
    private fun wipeOut(t: Double) = clamp((t - T_WIPE_OUT_A) / (T_WIPE_OUT_B - T_WIPE_OUT_A), 0.0, 1.0)

    fun seamOf(c: Double): Double = smoothstep(0.012, 0.07, c) * (1 - smoothstep(0.93, 0.99, c))

    /** Idle life: the key light drifts +-3 deg of azimuth over one loop. */
    fun keyDrift(t: Double): Double = (3 * PI / 180) * sin(2 * PI * t / LOOP)

    fun lookAt(tt: Double): ObjectSigninDissolveLook {
        val t = ((tt % LOOP) + LOOP) % LOOP
        val h = 1.0 / 120
        val yaw = yawAt(t)
        val rate = (yawAt(t + h) - yawAt(t - h)) / (2 * h)
        val c = cAt(t)
        val planeVel = ((cAt(t + h) - cAt(t - h)) / (2 * h)) * PLANE_SPAN
        val wipeP = if (t < T_WIPE_OUT_A) wipeIn(t) else 1 - wipeOut(t)
        val pressed = if (t >= T_TOUCH && t < T_RELEASE) stepAt(t - T_TOUCH, SPRING_TOUCH)
        else if (t >= T_RELEASE) max(1 - stepAt(t - T_RELEASE, SPRING_TOUCH), 0.0)
        else 0.0
        val pressed2 = if (t >= T_FLICK_DOWN && t < FLICK2_AT) stepAt(t - T_FLICK_DOWN, SPRING_TOUCH) else 0.0
        return ObjectSigninDissolveLook(
            yaw = yaw,
            c = c,
            roll = rollOf(rate),
            pitchP = pitchOf(planeVel),
            scale = 1 + (PRESS - 1) * max(pressed, pressed2),
            wipeP = wipeP,
            rate = rate,
            seam = seamOf(c),
            key = keyDrift(t),
            zip = -1.0,
        )
    }

    val REST_LOOK: ObjectSigninDissolveLook by lazy { lookAt(0.0) }

    /** Seconds after the skin last closed at the stalk, for the pulse. */
    fun zipSince(t: Double): Double {
        val a = t - ZIPS[0]
        val b = t - ZIPS[1]
        if (b >= 0 && b < 1) return b
        if (a >= 0 && a < 1) return a
        return -1.0
    }

    fun liveFrom(k: ObjectSigninDissolveLook): ObjectSigninDissolveLive = ObjectSigninDissolveLive(
        yaw = k.yaw,
        yawV = k.rate,
        yawTarget = heroNear(k.yaw, k.rate),
        c = k.c,
        cV = 0.0,
        roll = k.roll,
        rollV = 0.0,
        pitchP = k.pitchP,
        pitchV = 0.0,
        scale = k.scale,
        scaleV = 0.0,
        q = k.wipeP,
        qTarget = if (k.wipeP > 0.5) 1.0 else 0.0,
    )

    fun lookOfLive(s: ObjectSigninDissolveLive, t: Double): ObjectSigninDissolveLook = ObjectSigninDissolveLook(
        yaw = s.yaw,
        c = s.c,
        roll = s.roll,
        pitchP = s.pitchP,
        scale = s.scale,
        wipeP = s.q,
        rate = s.yawV,
        seam = seamOf(s.c),
        key = keyDrift(t),
        zip = -1.0,
    )

    /**
     * One step of the live physics (semi-implicit Euler at 240 Hz sub-steps). `held`: the finger owns yaw and `fingerC` is where
     * it holds the plane. Reduce Motion: no coast (yaw eases to the hero angle), no bank, no spin dissolve.
     */
    fun stepLive(
        s: ObjectSigninDissolveLive,
        dt: Double,
        held: Boolean,
        fingerC: Double,
        reduce: Boolean,
        breathe: Double,
    ): ObjectSigninDissolveLive {
        val n = max(1, ceil(dt / (1.0 / 240)).toInt())
        val h = dt / n
        var yaw = s.yaw
        var yawV = s.yawV
        var c = s.c
        var cV = s.cV
        var roll = s.roll
        var rollV = s.rollV
        var pitchP = s.pitchP
        var pitchV = s.pitchV
        var scale = s.scale
        var scaleV = s.scaleV
        for (i in 0 until n) {
            if (!held) {
                val k = if (reduce) 900.0 else SPRING_HOME.stiffness
                val d = if (reduce) 60.0 else SPRING_HOME.damping
                val nv = yawV + (-k * (yaw - s.yawTarget) - d * yawV) * h
                yaw += nv * h
                yawV = nv
            }
            val spin = if (reduce) 0.0 else spinShell(yawV)
            val fingerPos = if (held) rubberC(fingerC) + breathe / PLANE_SPAN else 0.0
            val target = max(fingerPos, spin)
            val sp = if (target > c) SPRING_PLANE else SPRING_HEAL
            val ncv = cV + (-sp.stiffness * (c - target) - sp.damping * cV) * h
            c += ncv * h
            cV = ncv

            val rollTarget = if (reduce) 0.0 else rollOf(yawV)
            val nrv = rollV + (-SPRING_BANK.stiffness * (roll - rollTarget) - SPRING_BANK.damping * rollV) * h
            roll += nrv * h
            rollV = nrv

            val pitchTarget = if (reduce) 0.0 else pitchOf(cV * PLANE_SPAN)
            val npv = pitchV + (-SPRING_BANK.stiffness * (pitchP - pitchTarget) - SPRING_BANK.damping * pitchV) * h
            pitchP += npv * h
            pitchV = npv

            val scaleTarget = if (held && !reduce) PRESS else 1.0
            val nsv = scaleV + (-SPRING_TOUCH.stiffness * (scale - scaleTarget) - SPRING_TOUCH.damping * scaleV) * h
            scale += nsv * h
            scaleV = nsv
        }
        val dq = if (s.qTarget - s.q == 0.0) 0.0 else sign(s.qTarget - s.q) * (dt / (WIPE_MS / 1000))
        var q = s.q + dq
        if ((dq > 0 && q > s.qTarget) || (dq < 0 && q < s.qTarget)) q = s.qTarget
        return ObjectSigninDissolveLive(
            yaw, yawV, s.yawTarget, c, cV, roll, rollV, pitchP, pitchV, scale, scaleV, q, s.qTarget,
        )
    }
}
