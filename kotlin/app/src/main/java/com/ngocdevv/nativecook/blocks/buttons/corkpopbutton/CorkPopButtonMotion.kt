package com.ngocdevv.nativecook.blocks.buttons.corkpopbutton

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Cork Pop Button — the motion, ported from corkMotion.ts. A frame is a pure function of a few
 clocks: the cork's state before the pop (inch, rock, how loose the cage is), the time since the
 pop, and the time since the bottle swap began. The loop fills them from the timeline; a finger
 fills them from the component's simulation, so both share one drawing.

 autoPlay (LOOP = 9 s):
   0.0 – 1.2   rest: the corked bottle, "Twist the cork up".
   1.2 – 3.2   three twists (rock ±5°, cage key spins 3 turns), each inching the cork up 4.5 dp
               (ease-out, 0.25 s); strain shake 2.9 – 3.2.
   3.2         pop: cork and cage fly up with a spin (1700 dp/s, g 1400), a CO2 puff (1.1 s).
   3.2 – 4.6   foam gushes from the lip (metaball shader, 16 balls) and runs down the neck; the
               order card lifts (ease-out back, 0.4 s) with a gold sheen; the line rolls to "Cheers".
   4.4 – 8.7   gold bubbles rise across the screen and pop (each 1.5 – 2.05 s).
   4.6 – 6.2   the foam settles to a small crown.
   6.4 – 8.0   the swap: the open bottle sinks (0.7 s), a corked one rises (0.8 s, ease-out back);
               the card settles, the line rolls back. 8.0 – 9.0 rest; 9.0 is the same frame as 0.0.
*/

internal const val LOOP = 9.0
internal const val CORK_POP_AT = 3.2
internal const val CORK_SWAP_AT = 6.4
internal const val CORK_SWAP_FOR = 1.6
internal const val CORK_BUBBLES = 56
internal const val CORK_BALLS = 16
/** Bubbles are gone this long after the pop. */
internal const val CORK_BUBBLES_DONE = 5.7

internal class CorkGeo(
    val w: Double,
    val h: Double,
    val cx: Double,
    /** Top of the glass lip, the neck's half width there, the neck's length, the body's half width. */
    val lipY: Double,
    val neck: Double,
    val neckLen: Double,
    val body: Double,
    /** The cork's mushroom: half width, height (above the lip). */
    val corkW: Double,
    val corkH: Double,
)

/** What a frame needs, before and after the pop. -1 for a clock that has not started. */
internal class CorkClocks {
    var inch = 0.0
    var rock = 0.0
    var loose = 0.0
    var shake = 0.0
    var finger = 0.0
    var popE = -1.0
    var swapE = -1.0
    var swapFor = CORK_SWAP_FOR
}

/** A frame's pose; arrays reused, with counts. */
internal class CorkPose {
    /** The bottle on screen (0 = the open one, 1 = the fresh one) and its drop (dp). */
    var fresh = 0
    var drop = 0.0
    var corked = true
    var inch = 0.0
    var rock = 0.0
    var loose = 0.0
    var finger = 0.0
    /** Flying cork: x, y, rotation. */
    var hasCork = false
    val cork = DoubleArray(3)
    /** Foam balls x, y, r (16) on the screen, and the foam's bounds (x, y, w, h; w = 0 for none). */
    val balls = FloatArray(CORK_BALLS * 3)
    val bounds = DoubleArray(4)
    /** CO2 puff: x, y, r, alpha per wisp. */
    val fog = DoubleArray(7 * 4)
    var fogCount = 0
    /** Bubbles: x, y, r, alpha, ring (0 = whole, >0 = popping). */
    val bubbles = DoubleArray(CORK_BUBBLES * 5)
    var bubbleCount = 0
    var lift = 0.0
    var sheen = 0.0
    var label = 0.0
}

internal object CorkMotion {
    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOutCubic(x: Double): Double {
        val k = 1 - clamp01(x)
        return 1 - k * k * k
    }
    fun easeInOutCubic(x: Double): Double {
        val k = clamp01(x)
        return if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2
    }
    fun easeOutBack(x: Double, c: Double = 1.7): Double {
        val k = clamp01(x) - 1
        return 1 + (c + 1) * k * k * k + c * k * k
    }
    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }
    fun hash(i: Int) = hash(i.toDouble())

    private val STARTS = doubleArrayOf(1.35, 1.95, 2.55)

    /** The loop's clocks at loop time t, written into [c]. */
    fun autoClocks(t: Double, c: CorkClocks): CorkClocks {
        c.swapFor = CORK_SWAP_FOR
        if (t >= CORK_POP_AT) {
            c.inch = 0.0
            c.rock = 0.0
            c.loose = 0.0
            c.shake = 0.0
            c.finger = 0.0
            c.popE = t - CORK_POP_AT
            c.swapE = if (t >= CORK_SWAP_AT) t - CORK_SWAP_AT else -1.0
            return c
        }
        var inch = 0.0
        var rock = 0.0
        for (k in 0 until 3) {
            inch += 4.5 * easeOutCubic(seg(t, STARTS[k] + 0.3, STARTS[k] + 0.55))
            // Each twist: a turn one way (anticipation), then the other, while the cork rises.
            val u = seg(t, STARTS[k], STARTS[k] + 0.55)
            rock += 5 * sin(PI * 2 * u) * (if (u > 0 && u < 1) 1 else 0)
        }
        c.inch = inch
        c.rock = rock
        c.loose = easeInOutCubic(seg(t, 1.3, 2.6))
        c.shake = seg(t, 2.9, 3.05) * 0.8 * sin(2 * PI * 41 * t)
        c.finger = easeOutCubic(seg(t, 1.1, 1.3)) * (1 - seg(t, 3.12, 3.2))
        c.popE = -1.0
        c.swapE = -1.0
        return c
    }

    /** Where foam ball j is e seconds after the pop, relative to the lip's centre (dx, dy, r). */
    private fun foamBall(g: CorkGeo, j: Int, e: Double, out: DoubleArray) {
        val settle = 1 - 0.82 * easeInOutCubic(seg(e, 1.4, 3.0))
        if (j == 0) {
            // The crown sitting on the lip.
            out[0] = 0.0
            out[1] = -g.neck * 0.25
            out[2] = g.neck * (0.2 + 0.9 * easeOutBack(seg(e, 0.0, 0.22))) * (0.55 + 0.45 * settle) * (if (e < 0) 0 else 1)
            return
        }
        if (j <= 9) {
            // Gushers: thrown up at different speeds so they stack into one column, then slow,
            // fall back and run down the neck's sides.
            val start = 0.025 * j
            val tau = max(0.0, e - start)
            val v = 230 + 52.0 * j + 40 * hash(j + 3)
            val grav = 900.0
            val tPeak = v / grav
            val side = if (j % 2 == 0) 1 else -1
            val fall = tPeak * 1.75
            val dy: Double
            val dx: Double
            if (tau < fall) {
                dy = -(v * tau - 0.5 * grav * tau * tau)
                dx = side * (4 + 16 * hash(j + 9)) * tau * 2
            } else {
                // Down the neck, slowing like cream.
                val k = 1 - exp(-(tau - fall) / 0.7)
                val y0 = -(v * fall - 0.5 * grav * fall * fall)
                dy = y0 + k * (g.neckLen * (0.2 + 0.4 * hash(j + 5)) - y0)
                dx = side * (4 + 16 * hash(j + 9)) * fall * 2 * (1 - k) + side * (g.neck + 3) * k
            }
            out[0] = dx
            out[1] = dy
            val grow = easeOutCubic(tau / 0.2)
            val late = if (j > 6) 0.8 else 1.0
            out[2] = (if (e < start) 0 else 1) * g.neck * (0.52 + 0.3 * hash(j + 1)) * late * (0.45 + 0.55 * grow) * settle
            return
        }
        // Drips over the lip, running down both sides.
        val start = 0.3 + 0.1 * (j - 10)
        val tau = e - start
        val side = if (j % 2 == 0) 1 else -1
        val k = 1 - exp(-max(0.0, tau) / 0.9)
        out[0] = side * (g.neck + 2)
        out[1] = -2 + k * g.neckLen * (0.3 + 0.35 * hash(j + 21))
        out[2] = if (tau < 0) 0.0 else g.neck * (0.3 + 0.12 * hash(j + 2)) * easeOutCubic(tau / 0.3) * settle
    }

    private val tmp = DoubleArray(3)

    fun makePose(c: CorkClocks, g: CorkGeo, p: CorkPose): CorkPose {
        val hh = g.h - g.lipY + g.corkH + 40
        var fresh = 0
        var drop = 0.0
        var corked = c.popE < 0
        if (c.swapE >= 0) {
            val sink = c.swapFor * 0.44
            if (c.swapE < sink) {
                drop = hh * easeInOutCubic(c.swapE / sink)
            } else {
                fresh = 1
                corked = true
                drop = hh * (1 - easeOutBack((c.swapE - sink) / (c.swapFor * 0.5), 1.2))
            }
        }
        val lipX = g.cx
        val lipY = g.lipY + drop
        p.hasCork = false
        p.fogCount = 0
        p.bubbleCount = 0
        p.bounds[0] = 0.0
        p.bounds[1] = 0.0
        p.bounds[2] = 0.0
        p.bounds[3] = 0.0
        val e = c.popE
        if (e >= 0 && e < 0.8) {
            val y = g.lipY - g.corkH * 0.5 - 18 - 1700 * e + 0.5 * 1400 * e * e
            if (y > -80) {
                p.hasCork = true
                p.cork[0] = lipX + 90 * e
                p.cork[1] = y
                p.cork[2] = 820 * e
            }
        }
        if (e >= 0 && fresh == 0) {
            var x0 = 1e9
            var y0 = 1e9
            var x1 = -1e9
            var y1 = -1e9
            for (j in 0 until CORK_BALLS) {
                foamBall(g, j, e, tmp)
                val x = lipX + tmp[0]
                val y = lipY + tmp[1]
                p.balls[j * 3] = x.toFloat()
                p.balls[j * 3 + 1] = y.toFloat()
                p.balls[j * 3 + 2] = tmp[2].toFloat()
                if (tmp[2] > 0.5) {
                    x0 = min(x0, x - tmp[2] * 2.2)
                    y0 = min(y0, y - tmp[2] * 2.2)
                    x1 = max(x1, x + tmp[2] * 2.2)
                    y1 = max(y1, y + tmp[2] * 2.2)
                }
            }
            if (x1 > x0) {
                p.bounds[0] = x0
                p.bounds[1] = y0
                p.bounds[2] = x1 - x0
                p.bounds[3] = y1 - y0
            }
            if (e < 1.1) {
                for (k in 0 until 7) {
                    val u = seg(e, 0.02 * k, 1.1)
                    val a = -PI / 2 + (hash(k + 40) - 0.5) * 2.2
                    val d = g.neck * (0.6 + 2.4 * easeOutCubic(u)) * (0.6 + 0.6 * hash(k + 44))
                    val o = p.fogCount * 4
                    p.fog[o] = lipX + cos(a) * d * 1.3
                    p.fog[o + 1] = lipY - 10 + sin(a) * d - 40 * u
                    p.fog[o + 2] = g.neck * (0.5 + 1.5 * easeOutCubic(u))
                    p.fog[o + 3] = 0.55 * (1 - u) * seg(e, 0.02 * k, 0.02 * k + 0.06)
                    p.fogCount += 1
                }
            }
        }
        if (e >= 0) {
            for (b in 0 until CORK_BUBBLES) {
                val start = 1.0 + 2.6 * hash(b + 70)
                val life = 1.5 + 0.55 * hash(b + 73)
                val tau = e - start
                if (tau < 0 || tau > life) continue
                val u = tau / life
                val r = 4 + 11 * hash(b + 77).pow(1.5)
                val spread = (hash(b + 79) - 0.5) * g.w * 1.05
                val x = lipX + spread * easeOutCubic(u * 1.4) + 9 * sin(2 * PI * (1.2 * tau + hash(b + 81)))
                val y = g.lipY - 30 - (220 + 220 * hash(b + 83)) * tau - 60 * tau * tau
                val ring = seg(tau, life - 0.14, life)
                val o = p.bubbleCount * 5
                p.bubbles[o] = x
                p.bubbles[o + 1] = y
                p.bubbles[o + 2] = r * (0.55 + 0.45 * easeOutCubic(tau / 0.25)) * (1 + 0.6 * ring)
                p.bubbles[o + 3] = 1 - ring
                p.bubbles[o + 4] = ring
                p.bubbleCount += 1
            }
        }
        val sw = c.swapE
        p.lift = (if (e >= 0) easeOutBack(seg(e, 0.0, 0.4)) else 0.0) * (1 - (if (sw >= 0) easeInOutCubic(seg(sw, c.swapFor * 0.12, c.swapFor * 0.5)) else 0.0))
        p.label = (if (e >= 0) easeInOutCubic(seg(e, 0.1, 0.45)) else 0.0) * (1 - (if (sw >= 0) easeInOutCubic(seg(sw, c.swapFor * 0.62, c.swapFor * 0.84)) else 0.0))
        val isFresh = fresh == 1
        p.fresh = fresh
        p.drop = drop
        p.corked = corked
        p.inch = if (isFresh) 0.0 else c.inch + c.shake
        p.rock = if (isFresh) 0.0 else c.rock
        p.loose = if (isFresh) 0.0 else c.loose
        p.finger = if (isFresh) 0.0 else c.finger
        p.sheen = if (e >= 0) seg(e, 0.1, 1.0) else 0.0
        return p
    }
}
