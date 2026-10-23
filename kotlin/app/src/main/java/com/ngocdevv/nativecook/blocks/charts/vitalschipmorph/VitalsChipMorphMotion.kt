package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vitals Chip Morph — the timeline and the morph. Every value on screen is a pure function of a
 * blend (lo, hi, u): the screens lo < hi and progress u from lo (0) to hi (1), plus the heart's
 * beat. The auto-play loop and a touch use the same closed-form spring.
 */
internal object VitalsChipMorphMotion {
    const val LOOP: Double = 9.6
    const val N: Int = 180
    /** After the last touch, the screen waits this long before returning to Readiness. */
    const val RESUME_AFTER: Double = 2.0
    const val BEAT: Double = 60.0 / 62.0

    object T {
        const val pressFitness: Double = 1.5
        const val pressEnd: Double = 1.66
        const val toFitness: Double = 1.6
        const val dragStart: Double = 3.9
        const val dragEnd: Double = 4.25
        const val dragTo: Double = 0.34
        const val firstBeat: Double = 5.2
        const val beatsEnd: Double = 6.95
        const val returnStart: Double = 6.95
        const val restAt: Double = 8.85
    }

    data class SpringSpec(val omega: Double, val zeta: Double)

    object SPRINGS {
        /** A chip tap: critically damped, ω 4. */
        val tap = SpringSpec(omega = 4.0, zeta = 1.0)
        /** A released drag: ζ 0.9, ω 6, carrying the finger's velocity. */
        val release = SpringSpec(omega = 6.0, zeta = 0.9)
        /** The idle return to rest: critically damped, ω 4.5. */
        val idle = SpringSpec(omega = 4.5, zeta = 1.0)
        /** Reduced motion: a 200 ms fade instead of the morph. */
        const val reducedFade: Double = 0.2
    }

    /** The drag in the loop: u per second when the simulated finger lets go. */
    const val DRAG_RELEASE_VELOCITY: Double = 1.4

    object DRAG {
        /** Width of a full transition in ref px and the commit thresholds. */
        const val span: Double = 760.0
        const val commitDistance: Double = 0.5
        const val commitVelocity: Double = 1.1
        /** The hero follows 55 % of an over-drag, falling off past rubberFalloff ref px. */
        const val rubber: Double = 0.55
        const val rubberFalloff: Double = 240.0
    }

    // Helpers are declared before every caller, matching the worklet source ordering.

    fun clamp01(v: Double): Double {
        return if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v
    }

    fun seg(t: Double, a: Double, b: Double): Double {
        return clamp01((t - a) / (b - a))
    }

    fun lerp(a: Double, b: Double, p: Double): Double {
        return a + (b - a) * p
    }

    /** cubic-bezier(0.77, 0, 0.175, 1), represented by the cheaper symmetric cubic. */
    fun easeInOut(x: Double): Double {
        return if (x < 0.5) 4.0 * x * x * x else 1.0 - (-2.0 * x + 2.0).pow(3.0) / 2.0
    }

    fun easeOut(x: Double): Double {
        return 1.0 - (1.0 - x).pow(3.0)
    }

    /** 0 → 1 → 0 bump over [a, b]. */
    fun bump(t: Double, a: Double, b: Double): Double {
        val p = seg(t, a, b)
        return if (p <= 0.0 || p >= 1.0) 0.0 else sin(PI * p)
    }

    /** Closed-form damped spring from x0 (velocity v0, units/s) towards 1. */
    fun spring(tau: Double, x0: Double, v0: Double, omega: Double, zeta: Double): Double {
        if (tau <= 0.0) return x0
        val a = 1.0 - x0
        if (zeta >= 1.0) {
            return 1.0 - exp(-omega * tau) * (a + (a * omega - v0) * tau)
        }
        val wd = omega * sqrt(1.0 - zeta * zeta)
        val decay = exp(-zeta * omega * tau)
        return 1.0 - decay * (
            a * cos(wd * tau) +
                ((a * zeta * omega - v0) / wd) * sin(wd * tau)
            )
    }

    /** Velocity of the same spring (units/s). */
    fun springVelocity(tau: Double, x0: Double, v0: Double, omega: Double, zeta: Double): Double {
        val h = 1.0 / 240.0
        return (spring(tau + h, x0, v0, omega, zeta) - spring(tau, x0, v0, omega, zeta)) / h
    }

    /** The sibling block's 62 BPM beat: a 6 % systole and a 2.5 % echo. */
    private fun pulseValue(phase: Double, from: Double, to: Double): Double {
        if (phase < from || phase > to) return 0.0
        val s = sin((PI * (phase - from)) / (to - from))
        return s * s
    }

    fun pulse(phase: Double): Double {
        return 0.06 * pulseValue(phase, 0.08, 0.23) + 0.025 * pulseValue(phase, 0.31, 0.45)
    }

    // The three shapes, sampled once.

    private fun sampleArc(): DoubleArray {
        val gauge = VitalsChipMorphTheme.layout.gauge
        val out = DoubleArray(2 * N)
        for (i in 0 until N) {
            val a = (gauge.from + (gauge.to - gauge.from) * (i.toDouble() / (N - 1).toDouble())) * PI / 180.0
            out[2 * i] = gauge.cx + gauge.r * cos(a)
            out[2 * i + 1] = gauge.cy - gauge.r * sin(a)
        }
        return out
    }

    private fun sampleCircle(): DoubleArray {
        val rings = VitalsChipMorphTheme.layout.rings
        val out = DoubleArray(2 * N)
        for (i in 0 until N) {
            // t = 0 at the bottom, clockwise on screen (bottom → left → top → right).
            val a = (270.0 - 360.0 * (i.toDouble() / N.toDouble())) * PI / 180.0
            out[2 * i] = rings.cx + rings.r[0] * cos(a)
            out[2 * i + 1] = rings.cy - rings.r[0] * sin(a)
        }
        return out
    }

    /** The arc is open; the circle and heart are closed. */
    val ARC: DoubleArray = sampleArc()
    val CIRCLE: DoubleArray = sampleCircle()
    val HEART: DoubleArray = VitalsChipMorphHeartOutline.HEART_OUTLINE.copyOf()
    val SHAPES: Array<DoubleArray> = arrayOf(ARC, CIRCLE, HEART)
    /** Where each shape's parameter t lives: the arc spans i / (N − 1), closed ones i / N. */
    val CLOSED: DoubleArray = doubleArrayOf(0.0, 1.0, 1.0)

    // Blend.

    /** Which of the three choreographies a pair uses. */
    fun pairOf(lo: Int, hi: Int): Int {
        if (lo == hi) return -1
        if (lo == 0 && hi == 1) return 0 // arc ↔ ring
        if (lo == 1 && hi == 2) return 1 // ring ↔ heart
        return 2 // arc ↔ heart
    }

    /** Weight of screen k in the blend (0..1). */
    fun weight(k: Int, lo: Int, hi: Int, u: Double): Double {
        if (lo == hi) return if (lo == k) 1.0 else 0.0
        if (k == lo) return 1.0 - u
        if (k == hi) return u
        return 0.0
    }

    /** Shape progress: how far the curve's points have travelled from lo's shape to hi's. */
    fun shapeProgress(pair: Int, u: Double): Double {
        return when (pair) {
            0 -> easeInOut(seg(u, 0.0, 0.62))
            1 -> easeInOut(seg(u, 0.06, 0.88))
            2 -> easeInOut(seg(u, 0.08, 0.9))
            else -> 0.0
        }
    }

    class Hero(
        /** Shape progress along the pair. */
        var sh: Double = 0.0,
        /** Main curve: stroke width, fill opacity, and colour mix. */
        var width: Double = 0.0,
        var fill: Double = 0.0,
        var colourMix: Double = 0.0,
        /** Progress arc on the main curve. */
        var t0: Double = 0.0,
        var t1: Double = 0.0,
        var accentWidth: Double = 0.0,
        var accentOpacity: Double = 0.0,
        /** 0 = Readiness light blue, 1 = Move ring red gradient. */
        var accentRed: Double = 0.0,
        /** Gauge dot: opacity and diameter. */
        var dotOpacity: Double = 0.0,
        var dotD: Double = 0.0,
        /** Inner rings: radius scale, opacity, and drawn progress. */
        var ringR0: Double = 0.0,
        var ringR1: Double = 0.0,
        var ringO0: Double = 0.0,
        var ringO1: Double = 0.0,
        var ringP0: Double = 0.0,
        var ringP1: Double = 0.0,
        var arrows: Double = 0.0,
        /** Rendered heart, its halo, and the flat silhouette. */
        var heartImage: Double = 0.0,
        var halo: Double = 0.0,
        /** Readiness number: opacity and odometer position. */
        var sevenOpacity: Double = 0.0,
        var sevenRoll: Double = 0.0,
        /** "62" digits and "BPM" appear independently. */
        var bpm0: Double = 0.0,
        var bpm1: Double = 0.0,
        var bpmUnit: Double = 0.0,
    )

    private val SEG = VitalsChipMorphTheme.layout.segment
    private val RINGS = VitalsChipMorphTheme.layout.rings

    /** Everything the hero draws for the pair [lo], [hi] at progress [u]. */
    fun heroFrame(lo: Int, hi: Int, u: Double, out: Hero): Hero {
        val pair = pairOf(lo, hi)
        // The settled screen uses the pair that ends there at its end point.
        var p = pair
        var v = u
        if (pair < 0) {
            p = if (lo == 0) 0 else if (lo == 1) 0 else 1
            v = if (lo == 0) 0.0 else 1.0
        }

        val sh = shapeProgress(p, v)
        var width = 0.0
        var fill = 0.0
        var colourMix = 0.0
        var t0 = SEG.t0
        var t1 = SEG.t1
        var accentWidth = VitalsChipMorphTheme.layout.gauge.stroke
        var accentOpacity = 1.0
        var accentRed = 0.0
        var dotOpacity = 1.0
        var dotD = VitalsChipMorphTheme.layout.dot.d
        var ringR0 = 1.0
        var ringR1 = 1.0
        var ringO0 = 0.0
        var ringO1 = 0.0
        var ringP0 = 0.0
        var ringP1 = 0.0
        var arrows = 0.0
        var heartImage = 0.0
        var halo = 0.0
        var sevenOpacity = 0.0
        var sevenRoll = 7.0
        var bpm0 = 0.0
        var bpm1 = 0.0
        var bpmUnit = 0.0

        if (p == 0) {
            // Readiness ↔ Fitness: the arc closes into the Move ring.
            width = lerp(VitalsChipMorphTheme.layout.gauge.stroke, RINGS.stroke, sh)
            colourMix = easeInOut(seg(v, 0.08, 0.45))
            // The head passes the ring's bottom at v ≈ 0.61, as the gap closes.
            val lead = easeInOut(seg(v, 0.2, 1.0))
            t0 = lerp(SEG.t0, 0.5, easeOut(seg(v, 0.0, 0.5)))
            t1 = lerp(SEG.t1, 0.5 + RINGS.progress[0], lead)
            accentWidth = width
            accentRed = easeInOut(seg(v, 0.05, 0.38))
            // The dot rides the head as a comet, then becomes the round end cap.
            dotOpacity = 1.0 - seg(v, 0.5, 0.8)
            dotD = lerp(VitalsChipMorphTheme.layout.dot.d, RINGS.stroke * 0.62, seg(v, 0.1, 0.6))
            for (k in 0..1) {
                val d = 0.1 * k.toDouble()
                val e = easeOut(seg(v, 0.32 + d, 0.78 + d))
                val radius = lerp(1.0, RINGS.r[k + 1] / RINGS.r[0], e)
                val o = easeOut(seg(v, 0.3 + d, 0.42 + d))
                val progress = RINGS.progress[k + 1] * easeInOut(seg(v, 0.5 + d, 1.0))
                if (k == 0) {
                    ringR0 = radius
                    ringO0 = o
                    ringP0 = progress
                } else {
                    ringR1 = radius
                    ringO1 = o
                    ringP1 = progress
                }
            }
            arrows = seg(v, 0.82, 1.0)
            sevenOpacity = 1.0 - seg(v, 0.0, 0.22)
            sevenRoll = 7.0
        } else if (p == 1) {
            // Fitness ↔ Heart: the ring fills in and becomes the heart.
            width = lerp(RINGS.stroke, 0.0, seg(v, 0.08, 0.86))
            fill = easeInOut(seg(v, 0.14, 0.62))
            colourMix = easeInOut(seg(v, 0.04, 0.42))
            val shrink = easeInOut(seg(v, 0.0, 0.45))
            t0 = 0.5
            t1 = lerp(0.5 + RINGS.progress[0], 0.5, shrink)
            accentWidth = width
            accentOpacity = 1.0 - seg(v, 0.22, 0.45)
            accentRed = 1.0
            dotOpacity = 0.0
            for (k in 0..1) {
                val d = 0.08 * k.toDouble()
                val e = easeInOut(seg(v, d, 0.5 + d))
                val base = RINGS.r[k + 1] / RINGS.r[0]
                val radius = lerp(base, base * 0.45, e)
                val o = 1.0 - seg(v, 0.12 + d, 0.42 + d)
                if (k == 0) {
                    ringR0 = radius
                    ringO0 = o
                    ringP0 = RINGS.progress[1]
                } else {
                    ringR1 = radius
                    ringO1 = o
                    ringP1 = RINGS.progress[2]
                }
            }
            arrows = 1.0 - seg(v, 0.0, 0.18)
            heartImage = seg(v, 0.62, 0.95)
            halo = seg(v, 0.5, 1.0)
            bpm0 = easeOut(seg(v, 0.6, 0.9))
            bpm1 = easeOut(seg(v, 0.65, 0.95))
            bpmUnit = seg(v, 0.75, 1.0)
        } else {
            // Readiness ↔ Heart: the heart opens at its tip into the arch.
            width = lerp(VitalsChipMorphTheme.layout.gauge.stroke, 0.0, seg(v, 0.15, 0.85))
            fill = easeInOut(seg(v, 0.5, 0.88))
            colourMix = seg(v, 0.2, 0.7)
            val back = 1.0 - v
            val glide = easeOut(seg(back, 0.55, 0.97))
            t0 = SEG.t0
            t1 = lerp(SEG.t0 + 0.004, SEG.t1, glide)
            accentWidth = width
            accentOpacity = seg(back, 0.5, 0.6)
            dotOpacity = seg(back, 0.5, 0.62)
            heartImage = seg(v, 0.7, 0.95)
            halo = seg(v, 0.55, 1.0)
            sevenOpacity = seg(back, 0.5, 0.62)
            sevenRoll = 7.0 * glide
            bpm0 = easeOut(seg(v, 0.62, 0.9))
            bpm1 = easeOut(seg(v, 0.67, 0.95))
            bpmUnit = seg(v, 0.75, 1.0)
        }

        out.sh = sh
        out.width = width
        out.fill = fill
        out.colourMix = colourMix
        out.t0 = t0
        out.t1 = t1
        out.accentWidth = accentWidth
        out.accentOpacity = accentOpacity
        out.accentRed = accentRed
        out.dotOpacity = dotOpacity
        out.dotD = dotD
        out.ringR0 = ringR0
        out.ringR1 = ringR1
        out.ringO0 = ringO0
        out.ringO1 = ringO1
        out.ringP0 = ringP0
        out.ringP1 = ringP1
        out.arrows = arrows
        out.heartImage = heartImage
        out.halo = halo
        out.sevenOpacity = sevenOpacity
        out.sevenRoll = sevenRoll
        out.bpm0 = bpm0
        out.bpm1 = bpm1
        out.bpmUnit = bpmUnit
        return out
    }

    /** Point on the blended curve at parameter t; t may pass 1 on a closed curve. */
    fun pointAt(pts: DoubleArray, t: Double, closedness: Double): DoubleArray {
        val span = N - 1 + closedness
        var f = t * span
        if (closedness > 0.5) {
            val n = N.toDouble()
            f = ((f % n) + n) % n
        } else {
            f = min(max(f, 0.0), (N - 1).toDouble())
        }
        val i0 = floor(f).toInt()
        val i1 = (i0 + 1) % N
        val w = f - i0.toDouble()
        return doubleArrayOf(
            pts[2 * i0] + (pts[2 * i1] - pts[2 * i0]) * w,
            pts[2 * i0 + 1] + (pts[2 * i1 + 1] - pts[2 * i0 + 1]) * w,
        )
    }

    /** Writes the hero's N points (screen ref px) for the blend into [out]. */
    fun heroPoints(lo: Int, hi: Int, sh: Double, out: DoubleArray): DoubleArray {
        val a = SHAPES[lo]
        val b = SHAPES[hi]
        for (i in 0 until 2 * N) out[i] = a[i] + (b[i] - a[i]) * sh
        return out
    }

    /** Main curve colour per screen. */
    val MAIN_COLOUR: Array<Color> = arrayOf(
        VitalsChipMorphTheme.colors.track,
        VitalsChipMorphTheme.colors.moveTrack,
        VitalsChipMorphTheme.colors.heartFlat,
    )

    // The auto-play loop.

    class Blend(
        var lo: Int = 0,
        var hi: Int = 0,
        var u: Double = 0.0,
        var beat: Double = 0.0,
        var press: Double = 0.0,
        var pressChip: Int = -1,
        var target: Int = 0,
    ) {
        fun set(lo: Int, hi: Int, u: Double, beat: Double, press: Double, pressChip: Int, target: Int): Blend {
            this.lo = lo
            this.hi = hi
            this.u = u
            this.beat = beat
            this.press = press
            this.pressChip = pressChip
            this.target = target
            return this
        }
    }

    /** Cubic loop drag whose release velocity is exactly [DRAG_RELEASE_VELOCITY]. */
    fun dragCurve(p: Double): Double {
        val end = DRAG_RELEASE_VELOCITY * (T.dragEnd - T.dragStart)
        val b = end - 2.0 * T.dragTo
        val a = T.dragTo - b
        return a * p * p + b * p * p * p
    }

    /** The loop: loop time t (0 ≤ t < LOOP) → blend. */
    fun loopBlend(t: Double, out: Blend): Blend {
        val press = bump(t, T.pressFitness, T.pressEnd)
        if (t < T.toFitness) {
            return out.set(lo = 0, hi = 1, u = 0.0, beat = 0.0, press = press, pressChip = 3, target = 1)
        }
        if (t < T.dragStart) {
            val u = clamp01(spring(t - T.toFitness, 0.0, 0.0, SPRINGS.tap.omega, SPRINGS.tap.zeta))
            return out.set(lo = 0, hi = 1, u = u, beat = 0.0, press = press, pressChip = 3, target = 1)
        }
        if (t < T.dragEnd) {
            val u = dragCurve(seg(t, T.dragStart, T.dragEnd))
            return out.set(lo = 1, hi = 2, u = u, beat = 0.0, press = 0.0, pressChip = -1, target = 2)
        }
        if (t < T.returnStart) {
            val u = clamp01(
                spring(t - T.dragEnd, T.dragTo, DRAG_RELEASE_VELOCITY, SPRINGS.release.omega, SPRINGS.release.zeta),
            )
            val beat = if (t >= T.firstBeat && t < T.beatsEnd) {
                pulse(((t - T.firstBeat) / BEAT) % 1.0)
            } else {
                0.0
            }
            return out.set(lo = 1, hi = 2, u = u, beat = beat, press = 0.0, pressChip = -1, target = 2)
        }
        // Return: pair (0, 2) with u running 1 → 0.
        val back = if (t >= T.restAt) {
            1.0
        } else {
            clamp01(spring(t - T.returnStart, 0.0, 0.0, SPRINGS.idle.omega, SPRINGS.idle.zeta))
        }
        return out.set(lo = 0, hi = 2, u = 1.0 - back, beat = 0.0, press = 0.0, pressChip = -1, target = 0)
    }

    /** Round-two liquid material choreography. */
    object LIQUID {
        const val melt: Double = 0.14
        const val set: Double = 0.3
        /** Shape speed in u/s that produces full edge wobble. */
        const val wobbleSpeed: Double = 1.6
        /** The ripple leaves the heart at the systole peak. */
        const val rippleAt: Double = 0.1 * BEAT
        const val speedSmoothing: Double = 0.25
        const val loudBeats: Int = 3
        const val quietRipple: Double = 0.3
    }

    fun liquidAmount(lo: Int, hi: Int, u: Double): Double {
        if (lo == hi) return 0.0
        return easeInOut(seg(u, 0.0, LIQUID.melt)) * easeInOut(seg(1.0 - u, 0.0, LIQUID.set))
    }

    fun wobbleAmount(speed: Double): Double {
        return clamp01(kotlin.math.abs(speed) / LIQUID.wobbleSpeed)
    }

    class Ripple(var age: Double = 0.0, var amp: Double = 0.0) {
        fun set(age: Double, amp: Double): Ripple {
            this.age = age
            this.amp = amp
            return this
        }
    }

    fun rippleOf(count: Double, out: Ripple): Ripple {
        if (count < 0.0) return out.set(age = 0.0, amp = 0.0)
        val index = floor(count).toInt()
        val age = (count - index) * BEAT - LIQUID.rippleAt
        if (age < 0.0) return out.set(age = 0.0, amp = 0.0)
        return out.set(age = age, amp = if (index < LIQUID.loudBeats) 1.0 else LIQUID.quietRipple)
    }

    fun loopBeatCount(t: Double): Double {
        if (t < T.firstBeat || t >= T.beatsEnd) return -1.0
        return (t - T.firstBeat) / BEAT
    }

    /** Exact pure-loop finite difference used by RN to drive the wobble. */
    fun loopSpeed(t: Double, before: Blend, current: Blend): Double {
        val h = 1.0 / 120.0
        if (t < h) return 0.0
        loopBlend(t - h, before)
        if (before.lo != current.lo || before.hi != current.hi) return 0.0
        return (current.u - before.u) / h
    }

}
