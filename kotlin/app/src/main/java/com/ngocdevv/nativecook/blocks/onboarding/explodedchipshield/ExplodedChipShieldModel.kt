package com.ngocdevv.nativecook.blocks.onboarding.explodedchipshield

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Timing, layout, copy and colours of the Exploded Chip Shield, and the frame it draws (a port of
 * ExplodedChipShieldModel.swift). Every value is a function of the loop time (autoPlay) or of the
 * open amount q (touch), so the stacks match.
 */
internal object EcsModel {
    const val LOOP = 7.0

    // Copy
    const val BRAND = "Ferrin"
    const val SKIP = "Skip"
    const val EYEBROW = "VAULT-GRADE PROTECTION"
    const val TITLE = "Every layer,\nencrypted."
    const val BODY = "Your vault is sealed on this device\nand opens only for you."
    const val CTA = "Continue"

    class Callout(val layer: Int, val title: String, val caption: String)

    /** Callouts, top to bottom. */
    val callouts = listOf(
        Callout(5, "Alloy shell", "Tamper-sealed"),
        Callout(4, "Cipher mesh", "256-bit cipher"),
        Callout(2, "Key enclave", "On-device keys"),
    )

    // Colours
    const val INK = 0x14171B
    const val GREY = 0x6B7280
    const val CAPTION = 0x7A818C
    const val TEAL = 0x1E9C8A
    const val GLOW = 0x5FD3C2
    const val LINE = 0x9AA3AD
    const val DOT_OFF = 0xC9CED5
    const val BG_TOP = 0xFFFFFF
    const val BG_BOTTOM = 0xEEF1F4

    // Layout (dp)
    const val UNIT = 150.0           // side of the lid
    const val SQUASH = 0.58          // isometric: y scale after the 45° turn
    const val REST_YAW = PI / 4
    const val SHIFT = 40.0           // the stack slides left this much as it opens
    const val TEXT_BLOCK = 280.0     // from the top of the eyebrow to the bottom safe edge
    const val CLOSED_LIFT = 206.0    // anchor of the closed stack above the text block
    const val OPEN_DROP = 106.0      // the anchor moves down this much when open
    const val DRAG_SPAN = 240.0      // a drag of 240 dp opens the stack fully
    const val CLOSED_ZOOM = 0.16     // the closed chip is drawn 16 % larger

    class Layer(
        val side: Double, val radius: Double, val thick: Double,
        val zClosed: Double, val zOpen: Double, val rank: Double, val fall: Double,
    )

    /** Bottom to top: base, tray, core, frosted ring, glass sheet, lid. */
    val layers = listOf(
        Layer(1.00, 0.22, 9.0, 0.0, 0.0, 0.0, 0.0),
        Layer(0.90, 0.18, 7.0, 9.0, 50.0, 1.00, 4.70),
        Layer(0.74, 0.15, 5.0, 9.0, 76.0, 0.75, 4.76),
        Layer(0.84, 0.17, 8.0, 9.0, 128.0, 0.50, 4.82),
        Layer(0.92, 0.20, 2.5, 9.0, 184.0, 0.25, 4.88),
        Layer(1.00, 0.22, 10.0, 9.0, 240.0, 0.0, 4.94),
    )
    const val DISC_SIDE = 0.44
    const val DISC_THICK = 7.0
    const val HOLE_SIDE = 0.50
    const val TRAY_HOLE = 0.76
    const val LANDING = 5.56

    // Curves
    fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeIn(x: Double) = x * x * x
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    val SPRING_W = 2 * PI / 0.55
    const val SPRING_Z = 0.72

    /** Damped spring (response 0.55 s, damping ratio 0.72) stepping 0 → 1 at τ = 0. */
    fun springStep(tau: Double): Double {
        if (tau <= 0) return 0.0
        if (tau >= 1.6) return 1.0
        val a = SPRING_Z * SPRING_W
        val wd = SPRING_W * sqrt(1 - SPRING_Z * SPRING_Z)
        return 1 - exp(-a * tau) * (cos(wd * tau) + a / wd * sin(wd * tau))
    }

    /** The landing bump: a fast damped wobble that starts and ends at 0. */
    fun bump(tau: Double): Double {
        if (tau <= 0 || tau >= 0.7) return 0.0
        return exp(-9 * tau) * sin(18 * tau)
    }

    /** The autoPlay timeline (seconds in 0 until LOOP). Frames 0 and LOOP are both the closed chip. */
    fun timeline(t: Double): EcsFrame {
        val f = EcsFrame()
        f.t = t
        val close = easeInOut(seg(t, 4.60, 5.70))
        f.open = easeInOut(seg(t, 1.00, 2.20)) * (1 - close)
        for (i in 1 until layers.size) {
            val l = layers[i]
            val rise = springStep(t - 1.0 - 0.07 * 4 * l.rank)
            val fall = easeIn(seg(t, l.fall, l.fall + 0.62))
            f.p[i] = rise * (1 - fall)
        }
        for (k in callouts.indices) {
            val d = 0.08 * k
            f.callout[k] = easeOut(seg(t, 1.90 + d, 2.50 + d)) * (1 - easeIn(seg(t, 4.50, 4.80)))
        }
        f.yaw = REST_YAW + 5 * PI / 180 * sin(2 * PI * t / LOOP - 1.2) * f.open
        val b = bump(t - LANDING)
        f.lidBump = 5 * max(0.0, b)
        f.dip = 1.5 * b
        return f
    }

    /** The same stack driven by the open amount q (0 closed … 1 open) under the finger. */
    fun manual(q: Double, yawOffset: Double, t: Double): EcsFrame {
        val f = EcsFrame()
        f.t = t
        f.open = min(max(q, 0.0), 1.06)
        for (i in 1 until layers.size) {
            val r = layers[i].rank
            f.p[i] = min(max((q - 0.3 * r) / 0.7, 0.0), 1.0) + max(0.0, q - 1)
        }
        for (k in callouts.indices) {
            val d = 0.05 * k
            f.callout[k] = easeOut(seg(q, 0.70 + d, 0.92 + d))
        }
        f.yaw = REST_YAW + yawOffset
        return f
    }

    /** Height of layer i's bottom face, with a slow bob while it floats. */
    fun z(i: Int, f: EcsFrame): Double {
        if (i == 0) return 0.0
        val l = layers[i]
        val p = f.p[i]
        val bob = 3.5 * sin(2 * PI * 2 * f.t / LOOP + 0.6 * i) * min(p, 1.0)
        var z = l.zClosed + (l.zOpen - l.zClosed) * p + bob
        if (i == layers.size - 1) z += f.lidBump
        return z
    }
}

internal class EcsFrame {
    var t = 0.0                       // loop time (bob)
    var open = 0.0                    // the whole stack: slide, glow, halo rings
    val p = DoubleArray(6)            // per layer: closed 0 … open 1
    val callout = DoubleArray(3)
    var yaw = EcsModel.REST_YAW
    var lidBump = 0.0
    var dip = 0.0
}

/** A damped spring from (x0, v0) towards a target, as a pure function of time. */
internal class EcsSpring(
    val from: Double, val velocity: Double, val to: Double, val start: Double,
    val zeta: Double = EcsModel.SPRING_Z,
) {
    fun value(now: Double): Double {
        val tau = max(0.0, now - start)
        val w = EcsModel.SPRING_W
        val a = zeta * w
        val wd = w * sqrt(1 - zeta * zeta)
        val aa = from - to
        val bb = (velocity + a * aa) / wd
        return to + exp(-a * tau) * (aa * cos(wd * tau) + bb * sin(wd * tau))
    }

    fun done(now: Double) = now - start > 1.4
}

/** Plays the timeline, hands over to the finger, springs home 2 s after the last touch. */
internal class EcsDriver {
    private val m = EcsModel
    private var epoch: Double? = null
    var manual = false
        private set
    var dragging = false
        private set
    private var q = EcsSpring(0.0, 0.0, 0.0, 0.0)
    private var tilt = EcsSpring(0.0, 0.0, 0.0, 0.0, 0.6)
    private var q0 = 0.0
    private var yaw0 = 0.0
    private var lastTouch = -100.0
    private var returning = false
    // Reduced motion: 1 shows the open stack, 0 the closed chip; a tap crossfades in 0.25 s.
    private var shown = 1.0
    private var fadeFrom = 1.0
    private var fadeStart = -100.0

    private fun loopTime(now: Double): Double {
        val e = epoch ?: now.also { epoch = it }
        return (now - e) % m.LOOP
    }

    fun frame(now: Double, auto: Boolean, reduced: Boolean): EcsFrame {
        val t = loopTime(now)
        if (reduced) return m.manual(1.0, 0.0, 0.0)
        if (manual) {
            if (auto && !dragging && now - lastTouch > 2) {
                if (!returning) {
                    returning = true
                    q = EcsSpring(q.value(now), 0.0, 0.0, now)
                } else if (q.done(now) && tilt.done(now)) {
                    manual = false
                    returning = false
                    epoch = now
                    return m.timeline(0.0)
                }
            }
            return m.manual(q.value(now), tilt.value(now), t)
        }
        return if (auto) m.timeline(t) else m.manual(0.0, 0.0, t)
    }

    /** Whether the stack is still moving without the loop. */
    fun busy(now: Double, reduced: Boolean): Boolean {
        if (reduced) return now - fadeStart < 0.3
        return manual && (dragging || !q.done(now) || !tilt.done(now) || now - lastTouch < 2.2)
    }

    fun shownAmount(now: Double): Double {
        val k = m.seg(now, fadeStart, fadeStart + 0.25)
        return fadeFrom + (shown - fadeFrom) * k
    }

    fun toggleReduced(now: Double) {
        fadeFrom = shownAmount(now)
        shown = if (shown > 0.5) 0.0 else 1.0
        fadeStart = now
    }

    fun begin(now: Double, auto: Boolean) {
        val f = frame(now, auto, false)
        val current = if (manual) q.value(now) else f.open
        yaw0 = f.yaw - m.REST_YAW
        q = EcsSpring(current, 0.0, current, now)
        tilt = EcsSpring(yaw0, 0.0, yaw0, now, 0.6)
        q0 = current
        manual = true
        dragging = true
        returning = false
        lastTouch = now
    }

    /** dx, dy: the finger's travel since the touch began (dp). Up opens, sideways tilts. */
    fun drag(dx: Double, dy: Double, now: Double) {
        var v = q0 - dy / m.DRAG_SPAN
        if (v > 1) {
            val over = v - 1
            v = 1 + over / (1 + over * 3) * 0.35
        }
        if (v < 0) {
            val over = -v
            v = -(over / (1 + over * 3) * 0.35)
        }
        q = EcsSpring(v, 0.0, v, now)
        val tiltDeg = min(max(dx * 0.08, -10.0), 10.0)
        val y = yaw0 + tiltDeg * PI / 180
        tilt = EcsSpring(y, 0.0, y, now, 0.6)
        lastTouch = now
    }

    /** Ends the drag or tap; returns true when the stack commits to the other state. */
    fun end(vy: Double, tap: Boolean, now: Double): Boolean {
        dragging = false
        lastTouch = now
        val current = q.value(now)
        val wasOpen = q0 >= 0.5
        val velocity = -vy / m.DRAG_SPAN
        val target = if (tap) {
            if (wasOpen) 0.0 else 1.0
        } else {
            if (current + 0.15 * velocity > 0.5) 1.0 else 0.0
        }
        q = EcsSpring(current, if (tap) 0.0 else velocity, target, now)
        tilt = EcsSpring(tilt.value(now), 0.0, 0.0, now, 0.6)
        return (target > 0.5) != wasOpen
    }
}
