package com.ngocdevv.nativecook.blocks.media.tapereelphysics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The tape's mechanics, all in closed form so the self-playing loop is exact: the reels' radii and
 * angles are functions of how much tape has been played, the slack tape is a damped spring driven
 * by steps, and the plucked span is a sum of string modes.
 */
internal object TapeReelPhysicsModel {
    // Reels

    /** Seconds of tape on a side, and where the loop rests (12:41). */
    const val TOTAL = 1800.0
    const val HOME = 761.0
    /** Pack radii (design dp): an empty hub's 20, a full reel's 50; r_supply² + r_takeup² stays constant. */
    const val R_MIN = 20.0
    const val R_MAX = 50.0
    /** Dp of tape that pass the head per second of play (sets how fast the hubs turn). */
    const val FEED = 37.0
    private const val AREA = (R_MAX * R_MAX - R_MIN * R_MIN) / TOTAL

    fun takeUp(s: Double) = sqrt(R_MIN * R_MIN + AREA * s)
    fun supply(s: Double) = sqrt(R_MAX * R_MAX - AREA * s)
    /** The hubs' angles (radians, clockwise) after s seconds of tape: ∫ feed ds / r, which is closed form. */
    fun takeUpAngle(s: Double) = 2 * FEED / AREA * (takeUp(s) - R_MIN)
    fun supplyAngle(s: Double) = 2 * FEED / AREA * (R_MAX - supply(s))
    /** Angular speed of a pack of radius r for a tape speed v (seconds of tape per second). */
    fun spin(v: Double, r: Double) = FEED * v / r

    // Transport

    enum class Mode { Stop, Play, FF, Rew, Pause, Seek }

    fun moving(m: Mode) = m == Mode.Play || m == Mode.FF || m == Mode.Rew || m == Mode.Seek

    /** From [start] on, the transport is in [mode] with the tape at [s]; a seek glides to [to] in [duration]. */
    class Segment(val start: Double, val mode: Mode, val s: Double, val to: Double = 0.0, val duration: Double = 0.0)

    fun easeInOut(x: Double): Double {
        val u = x.coerceIn(0.0, 1.0)
        return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    }

    private fun easeInOutSlope(x: Double): Double {
        val u = x.coerceIn(0.0, 1.0)
        return if (u < 0.5) 12 * u * u else 3 * (-2 * u + 2).pow(2)
    }

    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val u = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return u * u * (3 - 2 * u)
    }

    /**
     * Tape position and speed τ seconds into a segment. Play runs at 1×, wind at 320×, each reaching
     * its speed with a linear ramp (0.3 s, 0.35 s); stop and pause hold the tape.
     */
    fun position(g: Segment, tau: Double): Pair<Double, Double> = when (g.mode) {
        Mode.Stop, Mode.Pause -> g.s to 0.0
        Mode.Seek -> {
            val u = tau / g.duration
            if (u >= 1) g.to to 0.0 else (g.s + (g.to - g.s) * easeInOut(u)) to ((g.to - g.s) * easeInOutSlope(u) / g.duration)
        }
        else -> {
            val speed = if (g.mode == Mode.Play) 1.0 else if (g.mode == Mode.FF) 320.0 else -320.0
            val ramp = if (g.mode == Mode.Play) 0.3 else 0.35
            val d = if (tau < ramp) speed * tau * tau / (2 * ramp) else speed * (tau - ramp / 2)
            val v = if (tau < ramp) speed * tau / ramp else speed
            val s = g.s + d
            if (s <= 0) 0.0 to 0.0 else if (s >= TOTAL) TOTAL to 0.0 else s to v
        }
    }

    // Slack

    /** How far the slack tape bows out when the transport stops (dp), and its spring. */
    const val SLACK = 7.0
    private const val GAMMA = 5.0
    private const val OMEGA = 16.0

    /** A unit step response of the damped spring: 0 at the step, settling at 1 with one overshoot. */
    fun step(tau: Double): Double {
        if (tau <= 0) return 0.0
        return 1 - exp(-GAMMA * tau) * (cos(OMEGA * tau) + GAMMA / OMEGA * sin(OMEGA * tau))
    }

    // The span between the guides

    /** Pulled at x (0…1 of the span) by d dp: a triangle. */
    fun pulled(u: Double, x: Double, d: Double) = if (u < x) d * u / x else d * (1 - u) / (1 - x)

    /** Let go: the triangle's first four string modes, each ringing and dying away (fundamental 5 Hz). */
    fun ringing(u: Double, x: Double, d: Double, tau: Double): Double {
        var y = 0.0
        for (n in 1..4) {
            val a = 2 * d / (n * n * PI * PI * x * (1 - x)) * sin(n * PI * x)
            y += a * sin(n * PI * u) * cos(2 * PI * 5 * n * tau) * exp(-(2.5 + 0.8 * n) * tau)
        }
        return y
    }

    /** The hum of the span while the tape winds fast (a ≤ 1). */
    fun hum(u: Double, a: Double, clock: Double) =
        a * (1.2 * sin(PI * u) * sin(2 * PI * 18 * clock) + 0.6 * sin(3 * PI * u) * sin(2 * PI * 27 * clock + 1))

    /** A pluck: pulled at x by d now, or let go from d at [releasedAt]. */
    data class Pluck(val x: Double, val d: Double, val releasedAt: Double?)

    // The self-playing loop

    const val LOOP = 9.0

    /**
     * 0.6 play · 1.6–1.95 a finger pulls the tape 22 dp at 38 % · 2.1 lets go · 3.2 fast forward ·
     * 4.6 stop · 5.2 rewind back to 12:41 in 2.2 s · 7.4 stop · rest to 9.0.
     */
    val script: List<Segment> = run {
        val g = mutableListOf(Segment(0.0, Mode.Stop, HOME))
        g.add(Segment(0.6, Mode.Play, HOME))
        val atFF = position(g[1], 3.2 - 0.6).first
        g.add(Segment(3.2, Mode.FF, atFF))
        val atStop = position(g[2], 4.6 - 3.2).first
        g.add(Segment(4.6, Mode.Stop, atStop))
        g.add(Segment(5.2, Mode.Seek, atStop, HOME, 2.2))
        g.add(Segment(7.4, Mode.Stop, HOME))
        g
    }
    val scriptSteps = listOf(0.6 to -SLACK, 4.6 to SLACK, 5.2 to -SLACK, 7.4 to SLACK)

    fun scriptPluck(t: Double): Pluck? {
        if (t < 1.6) return null
        if (t < 2.1) return Pluck(0.38, 22 * (1 - (1 - min((t - 1.6) / 0.35, 1.0)).pow(3)), null)
        return Pluck(0.38, 22.0, 2.1)
    }

    // A moment

    class Frame {
        var s = HOME
        var v = 0.0
        var mode = Mode.Stop
        /** The key shown pressed (index into the key row), or -1. */
        var key = -1
        var sag = SLACK
        var pluck: Pluck? = null
        /** The span's hum while winding (0…1) and the meters' level envelope (0…1). */
        var hum = 0.0
        var meters = 0.0
        /** A scripted finger on the tape: x on the span, d below it, and its opacity (alpha 0: none). */
        var fingerX = 0.0
        var fingerD = 0.0
        var fingerAlpha = 0.0
    }

    /** Evaluates a timeline (segments, slack steps on a base) at time t. */
    fun frame(t: Double, g: List<Segment>, sagBase: Double, steps: List<Pair<Double, Double>>, pluck: Pluck?): Frame {
        val f = Frame()
        var i = 0
        g.forEachIndexed { k, seg -> if (seg.start <= t) i = k }
        val seg = g[i]
        val tau = t - seg.start
        val (s, v) = position(seg, tau)
        f.s = s
        f.v = v
        f.mode = if (seg.mode == Mode.Seek) Mode.Rew else seg.mode
        val previous = if (i > 0) g[i - 1].mode else null
        f.key = when (seg.mode) {
            Mode.Play -> 1
            Mode.FF -> 2
            Mode.Rew, Mode.Seek -> 0
            Mode.Pause -> 3
            Mode.Stop -> if (previous != null && previous != Mode.Stop && tau < 0.2) 4 else -1
        }
        f.sag = sagBase
        for ((at, delta) in steps) f.sag += delta * step(t - at)
        f.pluck = pluck
        f.hum = min(1.0, abs(f.v) / 200)
        if (seg.mode == Mode.Play) f.meters = smoothstep(0.0, 0.3, tau)
        else if (previous == Mode.Play) f.meters = 1 - smoothstep(0.0, 0.3, tau)
        return f
    }

    fun auto(t: Double): Frame {
        val f = frame(t, script, SLACK, scriptSteps, scriptPluck(t))
        if (t >= 1.45 && t < 2.35) {
            val p = scriptPluck(t)
            f.fingerX = 0.38
            f.fingerD = if (p?.releasedAt == null) p?.d ?: 0.0 else 22.0
            f.fingerAlpha = smoothstep(1.45, 1.6, t) * (1 - smoothstep(2.1, 2.35, t))
        }
        return f
    }

    /** The span's displacement at u (0…1) for a frame, with time [t] on the frame's clock. */
    fun span(u: Double, f: Frame, t: Double, still: Boolean): Double {
        var y = 0.0
        val p = f.pluck
        if (p != null) {
            val r = p.releasedAt
            if (r != null) {
                if (!still) y += ringing(u, p.x, p.d, t - r)
            } else {
                y += pulled(u, p.x, p.d)
            }
        }
        if (!still) y += hum(u, f.hum, t)
        return y
    }

    /** The meters: a level 0…1 per channel, from a fixed mix of sines. */
    fun level(t: Double, channel: Int): Double {
        val p = channel * 0.9
        return 0.55 + 0.2 * sin(2 * PI * 1.7 * t + p) + 0.12 * sin(2 * PI * 4.3 * t + 1.1 + p) + 0.08 * sin(2 * PI * 7.9 * t + 2.3 + 2 * p)
    }

    fun clock(s: Double): String {
        val n = floor(s).toInt()
        return "%02d:%02d".format(n / 60, n % 60)
    }
}
