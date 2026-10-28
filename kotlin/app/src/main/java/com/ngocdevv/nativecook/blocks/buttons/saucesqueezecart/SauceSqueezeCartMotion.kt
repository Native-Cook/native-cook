package com.ngocdevv.nativecook.blocks.buttons.saucesqueezecart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Sauce Squeeze Cart — the timeline, the springs and the touch driver (a port of
 SauceSqueezeCartMotion.swift).

 Every frame is a pure function of time. Before the release, the squeeze `s` (0…1) comes from the
 script (autoPlay) or from how long the finger has held (touch). After the release, everything is a
 function of τ, the time since the release, so the touch-driven shot replays the loop's own show.

 autoPlay: LOOP = 8 s
   0.0 – 1.2   rest: the bottle bobs gently on the pink page
   1.1 – 2.6   the "Hold to add" button is held: the bottle squashes (wider, shorter, the waist
               pinched), trembles, a bead of sauce swells at the nozzle
   2.6         release (τ = 0): the bottle snaps into a stretch and recoils up, a thick glossy stream
               whips out of the cap and down the screen
   2.9 – 3.8   a red pool rises from the bottom with a wavy surface until the screen is red
   3.95 – 5.7  stickers pop on the red ("IN YOUR CART", a smiley, a cart, "+1 ADDED", an arrow, a sparkle)
   5.5 – 6.4   the red retracts up the screen, drips hanging from it, and reveals the page
   6.4 – 7.3   the cart in the header bumps and a "+1" floats up
   7.3 – 8.0   rest (the frame at 8 is the frame at 0)
*/

internal class SscFrame {
    /** Hold pressure, 0…1: the button's fill and the bead at the nozzle. */
    var s = 0.0
    /** Squash (+) or stretch (−) of the bottle. */
    var q = 0.0
    /** The button pressed in, 0…1. */
    var press = 0.0
    /** Recoil of the bottle, dp (negative is up). */
    var lift = 0.0
    /** Extra rotation of the bottle, degrees. */
    var spin = 0.0
    /** Idle bob, −1…1 (and its sway). */
    var bob = 0.0
    var sway = 0.0
    /** Tremble of a hard squeeze, dp. */
    var shake = 0.0
    /** Time since the release when a shot is running, or null. */
    var tau: Double? = null
    /** The squeeze the shot was released with (scales the recoil). */
    var shotS = 1.0
    var reduced = false
}

internal object SscMotion {
    const val LOOP = 8.0

    const val RELEASE_AT = 2.6
    const val SQUEEZE_FROM = 1.2
    const val SQUEEZE_TO = 2.45
    /** When a shot has played out (rest again), in τ. */
    const val SHOT_END = 4.8
    /** The screen is fully red from here, in τ. */
    const val COVERED_AT = 1.2
    const val THRESHOLD = 0.4
    const val IDLE_BEFORE_BACK = 2.0
    /** 60 cycles per loop = 7.5 Hz, zero at the release. */
    const val TREMBLE_K = 60.0

    // Curves

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun mix(a: Double, b: Double, u: Double) = a + (b - a) * u
    fun smooth(x: Double): Double { val c = clamp01(x); return c * c * (3 - 2 * c) }
    fun smooth(a: Double, b: Double, x: Double) = smooth((x - a) / (b - a))
    fun easeOut(u: Double): Double { val c = 1 - clamp01(u); return 1 - c * c * c }
    fun easeInOut(u: Double): Double {
        val c = clamp01(u)
        return if (c < 0.5) 4 * c * c * c else 1 - (-2 * c + 2).pow(3) / 2
    }
    fun easeInOutQuad(u: Double): Double {
        val c = clamp01(u)
        return if (c < 0.5) 2 * c * c else 1 - (-2 * c + 2).pow(2) / 2
    }
    fun easeInBack(u: Double): Double {
        val c = clamp01(u)
        val k = 1.70158
        return (k + 1) * c * c * c - k * c * c
    }
    fun hash(i: Double): Double {
        val v = sin(i * 12.9898 + 78.233) * 43758.5453
        return v - floor(v)
    }

    /** An analytic damped spring (damping < 1) at time τ after it starts from x0 with velocity v0. */
    fun spring(tau: Double, x0: Double, v0: Double, target: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return x0
        val z = min(damping, 0.999)
        val w0 = 2 * PI / response
        val wd = w0 * sqrt(1 - z * z)
        val a = x0 - target
        val b = (v0 + z * w0 * a) / wd
        return target + exp(-z * w0 * tau) * (a * cos(wd * tau) + b * sin(wd * tau))
    }

    // autoPlay

    fun autoFrame(t: Double): SscFrame {
        val f = SscFrame()
        val phase = 2 * PI * 2 * t / LOOP
        f.bob = sin(phase)
        f.sway = sin(phase + 1.1)
        if (t < RELEASE_AT) {
            val s = easeInOut(seg(t, SQUEEZE_FROM, SQUEEZE_TO))
            val tr = sin(2 * PI * TREMBLE_K * t / LOOP)
            f.s = s
            f.q = s + 0.03 * s * s * tr
            f.shake = 1.6 * s * s * tr
            f.press = easeOut(seg(t, 1.1, 1.25))
        } else {
            shot(f, t - RELEASE_AT, 1.0)
        }
        return f
    }

    /** The bottle after a release with squeeze s0, τ seconds ago. */
    fun shot(f: SscFrame, tau: Double, s0: Double) {
        f.tau = tau
        f.shotS = s0
        f.s = s0 * (1 - easeOut(seg(tau, 0.0, 0.2)))
        f.q = max(-1.0, spring(tau, s0, -14 * s0, 0.0, 0.55, 0.38))
        f.lift = spring(tau, 0.0, -560 * s0, 0.0, 0.6, 0.5)
        f.spin = spring(tau, 0.0, 60 * s0, 0.0, 0.6, 0.45)
        f.press = 1 - easeOut(seg(tau, 0.0, 0.15))
    }

    // The show after the release (functions of τ)

    /** How far the stream's head has travelled, 0…1. */
    fun streamHead(tau: Double) = easeOut(seg(tau, 0.0, 0.35))
    fun streamWidth(tau: Double) = 44 * easeOut(seg(tau, 0.0, 0.12)) + 106 * easeInOut(seg(tau, 0.35, 1.0))
    fun streamSway(tau: Double) = 26 * easeOut(seg(tau, 0.0, 0.35)) + 54 * easeInOut(seg(tau, 0.35, 1.0))
    fun streamVisible(tau: Double) = tau >= 0 && tau < 1.25

    /** The red's top edge before its waves: it floods up from below the screen. */
    fun floodLevel(tau: Double, h: Double) = mix(h + 30, -80.0, easeInOut(seg(tau, 0.3, COVERED_AT)))

    /** The red's bottom edge before its waves and drips: it retracts up towards the cart. */
    fun drainLevel(tau: Double, h: Double) = mix(h + 60, -140.0, easeInOutQuad(drain(tau)))

    /** How far the drain has gone, 0…1 (drives the drips). */
    fun drain(tau: Double) = seg(tau, 2.9, 3.8)

    private val dripX = doubleArrayOf(0.14, 0.36, 0.58, 0.8, 0.93)
    private val dripW = doubleArrayOf(16.0, 22.0, 13.0, 19.0, 14.0)
    private val dripL = doubleArrayOf(110.0, 170.0, 80.0, 140.0, 95.0)

    fun waves(x: Double, tau: Double) =
        10 * sin(2 * PI * (x / 220 + 1.3 * tau)) + 6 * sin(2 * PI * (x / 97 - 2.1 * tau) + 1.3)

    /** The red's top edge at x (the flood's wavy surface). */
    fun redTop(x: Double, tau: Double, h: Double) = floodLevel(tau, h) + waves(x, tau)

    /** The red's bottom edge at x: a wavy line with drips hanging from it while it rises. */
    fun redBottom(x: Double, tau: Double, w: Double, h: Double): Double {
        val u = drain(tau)
        val y = drainLevel(tau, h) + waves(x, tau + 0.4)
        if (u <= 0 || u >= 1) return y
        val grow = sin(PI * u).pow(0.7)
        var hang = 0.0
        for (i in dripX.indices) {
            val dx = abs(x - dripX[i] * w)
            val hw = dripW[i]
            val len = dripL[i] * grow
            var d = 0.0
            if (dx < hw) {
                val cap = sqrt(hw * hw - dx * dx)
                d = if (len > hw) len - hw + cap else len * cap / hw
            }
            val fillet = min(len, 40.0) * 0.5 * exp(-(dx / (hw * 1.9)).pow(2))
            hang = max(hang, max(d, fillet))
        }
        return y + hang
    }

    /** Reduced motion: the red fades in and out instead of flooding. */
    fun redFade(tau: Double) = smooth(seg(tau, 0.0, 0.3)) * (1 - smooth(seg(tau, 2.7, 3.1)))

    /** A sticker's scale: pops in on a playful spring, pops out with a little anticipation. */
    fun sticker(i: Int, tau: Double): Double {
        val start = 1.35 + 0.07 * i
        if (tau < start) return 0.0
        val pop = spring(tau - start, 0.0, 0.0, 1.0, 0.5, 0.55)
        val out = seg(tau, 2.70 + 0.04 * (5 - i), 2.92 + 0.04 * (5 - i))
        return max(0.0, pop * (1 - easeInBack(out)))
    }

    /** The cart button's bump, around 0 (scale = 1 + 0.6 × bump). */
    fun cartBump(tau: Double) = spring(tau - 3.8, 0.0, 7.0, 0.0, 0.42, 0.4)

    /** The "+1" over the cart: scale, rise (dp) and opacity. */
    fun plusOne(tau: Double): Triple<Double, Double, Double> {
        val c = tau - 3.85
        if (c <= 0) return Triple(0.0, 0.0, 0.0)
        val scale = spring(c, 0.0, 0.0, 1.0, 0.45, 0.55)
        return Triple(max(0.0, scale), 10 * easeOut(seg(c, 0.0, 0.85)), 1 - smooth(seg(c, 0.5, 0.85)))
    }
}

/** The touch session and the loop clock. The screen asks it for a frame each tick. */
internal class SscDriver {
    private val m = SscMotion

    private var autoStart: Double? = null
    var manual = false
        private set
    private var holdStart: Double? = null
    private var holdS0 = 0.0
    private var softAt: Double? = null
    private var softS = 0.0
    var shotAt: Double? = null
        private set
    private var shotS = 1.0
    var lastTouch = -1e9
        private set
    private var bob0 = 0.0
    private var sway0 = 0.0
    private var manualAt = 0.0
    /** The finger is down but arrived while a shot was running; it only delays the restart. */
    private var ignoring = false
    val touching get() = holdStart != null || ignoring
    val holding get() = holdStart != null

    fun frame(now: Double, auto: Boolean, reduced: Boolean): SscFrame {
        if (auto && manual && !touching) {
            var resume = lastTouch + SscMotion.IDLE_BEFORE_BACK
            shotAt?.let { resume = max(resume, it + SscMotion.SHOT_END) }
            if (now >= resume) {
                manual = false; autoStart = now; shotAt = null; softAt = null
            }
        }
        val f = if (auto && !manual) {
            val start = autoStart ?: now.also { autoStart = it }
            m.autoFrame((now - start) % SscMotion.LOOP)
        } else {
            manualFrame(now)
        }
        f.reduced = reduced
        if (reduced) {
            f.q = 0.0; f.lift = 0.0; f.spin = 0.0; f.bob = 0.0; f.sway = 0.0; f.shake = 0.0
        }
        return f
    }

    private fun manualFrame(now: Double): SscFrame {
        val f = SscFrame()
        val decay = exp(-(now - manualAt) / 0.35)
        f.bob = bob0 * decay
        f.sway = sway0 * decay
        val sa = shotAt
        val hs = holdStart
        val sf = softAt
        when {
            sa != null -> m.shot(f, now - sa, shotS)
            hs != null -> {
                val s = holdLevel(now - hs)
                val tr = sin(2 * PI * 7.5 * now)
                f.s = s
                f.q = s + 0.03 * s * s * tr
                f.shake = 1.6 * s * s * tr
                f.press = m.easeOut(m.seg(now - hs, 0.0, 0.15))
            }
            sf != null -> {
                val u = now - sf
                f.s = softS * (1 - m.easeOut(m.seg(u, 0.0, 0.2)))
                f.q = m.spring(u, softS, 0.0, 0.0, 0.45, 0.5)
                f.press = 1 - m.easeOut(m.seg(u, 0.0, 0.15))
            }
        }
        return f
    }

    private fun holdLevel(held: Double) = holdS0 + (1 - holdS0) * (1 - exp(-held / 0.6))

    /** The finger goes down. Returns false when a shot is running and the touch is ignored. */
    fun begin(now: Double, auto: Boolean, reduced: Boolean): Boolean {
        val current = frame(now, auto, reduced)
        lastTouch = now
        if (!manual) {
            manual = true
            manualAt = now
            bob0 = current.bob
            sway0 = current.sway
            val tau = current.tau
            if (tau != null && tau < SscMotion.SHOT_END) {
                shotAt = now - tau
                shotS = current.shotS
                ignoring = true
                return false
            }
        }
        val sa = shotAt
        if (sa != null && now - sa < SscMotion.SHOT_END) {
            ignoring = true
            return false
        }
        shotAt = null
        softAt = null
        holdS0 = m.clamp01(if (current.tau == null) current.s else 0.0)
        holdStart = now
        return true
    }

    /** Shoots at once (the accessibility action), unless a shot is already running. */
    fun shoot(now: Double, auto: Boolean, reduced: Boolean) {
        if (!begin(now, auto, reduced)) { ignoring = false; return }
        holdStart = null
        lastTouch = now
        shotAt = now
        shotS = 1.0
    }

    /** The finger lifts. Returns true when the squeeze was strong enough to shoot. */
    fun end(now: Double): Boolean {
        lastTouch = now
        if (ignoring) { ignoring = false; return false }
        val hs = holdStart ?: return false
        val s = holdLevel(now - hs)
        holdStart = null
        if (s >= SscMotion.THRESHOLD) {
            shotAt = now
            shotS = s
            return true
        }
        softAt = now
        softS = s
        return false
    }

    /** Seconds of holding until the squeeze is armed (0 if it already is). */
    fun armDelay(): Double {
        if (holdStart == null || holdS0 >= SscMotion.THRESHOLD) return 0.0
        return -0.6 * ln((1 - SscMotion.THRESHOLD) / (1 - holdS0))
    }
}
