package com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Popcorn Pop Button — the motion, ported from popcornMotion.ts. Every frame is a pure function of
 a small "story" (when each kernel popped, when it was tipped into the bowl, when the lid lifted,
 when the label rolled), so the self-playing loop and the finger share one drawing: the loop fills
 the story from the timeline, a finger fills it from the simulation in the component.

 autoPlay (LOOP = 8 s):
   0.0 – 1.2   rest: 22 kernels in the copper pan, glass lid on, "Start the movie".
   1.2 – 2.6   a press (scale 0.97 in 0.1 s): the pan heats (glow, thermometer arc, kernels jitter).
   2.6 – 4.6   popping: one by one, slow, then fast, then slow (rank q -> 2.6 + 2 s * (q + 0.9 sin 2πq / 2π)).
   4.6 – 5.0   the heap lifts the lid; it rides up on the popcorn (ease-out back).
   5.0 – 5.35  the label rolls to "Enjoy the show"; the press lets go at 4.8.
   6.4 – 8.0   the pan tips into the bowl on the movie card (staggered arcs), kernels drop back in,
               the lid settles, the label rolls back. 8.0 is the same frame as 0.0.
*/

internal const val LOOP = 8.0
internal const val POPCORN_N = 22
/** Corns needed before a release counts (and before the lid lifts under a finger). */
internal const val POPCORN_ENOUGH = 15
internal const val POPCORN_NEVER = 1e9

/** Geometry the motion needs (dp). */
internal class PopcornGeo(
    val w: Double,
    val h: Double,
    val bx: Double,
    val by: Double,
    val D: Double,
    val px: Double,
    val py: Double,
    val rx: Double,
    val ry: Double,
    val wall: Double,
    val dome: Double,
    val bowlX: Double,
    val bowlY: Double,
)

/** Everything a frame is drawn from. Times are on one clock (loop time, or the frame clock). */
internal open class PopcornStory {
    var now = 0.0
    val popAt = DoubleArray(POPCORN_N) { POPCORN_NEVER }
    val goneAt = DoubleArray(POPCORN_N) { POPCORN_NEVER }
    var emptyFor = 1.6
    var lidAt = POPCORN_NEVER
    var lidBack = POPCORN_NEVER
    var labelAt = POPCORN_NEVER
    var labelOff = POPCORN_NEVER
    var heat = 0.0
    var press = 0.0
}

/** A frame's pose; the arrays are reused from frame to frame, with a count of values each. */
internal class PopcornPose {
    var heat = 0.0
    var press = 0.0
    var shakeX = 0.0
    var shakeY = 0.0
    /** Per kernel: x, y, rotation (deg), alpha, variant. */
    val kernels = DoubleArray(POPCORN_N * 5)
    var kernelCount = 0
    /** Per corn in the pan, back to front: x, y, rotation, sx, sy, variant. */
    val corns = DoubleArray(POPCORN_N * 6)
    var cornCount = 0
    /** Corns on their way to the bowl: x, y, rotation, scale, variant. */
    val flying = DoubleArray(POPCORN_N * 5)
    var flyingCount = 0
    /** Hull flecks: x, y, alpha. */
    val flecks = DoubleArray(POPCORN_N * 9)
    var fleckCount = 0
    var lidX = 0.0
    var lidY = 0.0
    var lidRot = 0.0
    var label = 0.0
    var bowl = 0.0
    // Scratch: corns before sorting, keyed by rank.
    internal val raw = DoubleArray(POPCORN_N * 6)
    internal val has = BooleanArray(POPCORN_N)
}

internal object PopcornMotion {
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
    fun easeOutBack(x: Double): Double {
        val k = clamp01(x) - 1
        val c = 1.9
        return 1 + (c + 1) * k * k * k + c * k * k
    }
    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }
    fun hash(i: Int) = hash(i.toDouble())

    /** The pop order: rank of each kernel (a seeded shuffle). */
    val RANK: IntArray = run {
        val idx = (0 until POPCORN_N).sortedBy { hash(it * 3.7 + 1) }
        val rank = IntArray(POPCORN_N)
        for (r in 0 until POPCORN_N) rank[idx[r]] = r
        rank
    }

    /** Slow, fast, slow: the share of the popping time at which rank q pops. */
    fun popCurve(q: Double) = q + (0.9 * sin(2 * PI * q)) / (2 * PI)

    val AUTO_POP = DoubleArray(POPCORN_N) { 2.6 + 2 * popCurve(RANK[it] / (POPCORN_N - 1.0)) }

    /** Under a finger: the cooking needed before rank r pops. */
    fun cookNeeded(r: Int) = 0.05 + 1.25 * popCurve(r / (POPCORN_N - 1.0))

    /** Kernel spots on the pan's floor, in pan units (-1..1). */
    val SPOT = DoubleArray(POPCORN_N * 2).also { s ->
        for (i in 0 until POPCORN_N) {
            val r = 0.74 * sqrt((i + 0.5) / POPCORN_N) * (0.88 + 0.2 * hash(i + 61))
            val a = i * 2.39996 + 0.3 + (hash(i + 67) - 0.5) * 0.5
            s[i * 2] = r * cos(a)
            s[i * 2 + 1] = r * sin(a) * 0.92
        }
    }

    /** The corns' places in the heap by rank: 14 on the floor, then 8 piled on top (lift scaled by D). */
    val HEAP = DoubleArray(POPCORN_N * 3).also { h ->
        for (r in 0 until POPCORN_N) {
            if (r < 14) {
                val rr = 0.78 * sqrt((r + 0.5) / 14)
                val a = r * 2.39996 + 0.9
                h[r * 3] = rr * cos(a)
                h[r * 3 + 1] = rr * sin(a)
                h[r * 3 + 2] = 0.004 + (1 - rr) * 0.02
            } else {
                val j = r - 14
                val rr = 0.5 * sqrt((j + 0.5) / 8)
                val a = j * 2.39996 + 2.1
                h[r * 3] = rr * cos(a)
                h[r * 3 + 1] = rr * sin(a) - 0.12
                h[r * 3 + 2] = 0.05 + (1 - rr) * 0.035
            }
        }
    }

    /** Corn draw order (by rank): the floor layer back to front, then the pile back to front. */
    val ORDER: IntArray = (0 until POPCORN_N).sortedBy { (if (it < 14) 0.0 else 1000.0) + HEAP[it * 3 + 1] }.toIntArray()

    /** Kernel at each rank. */
    val BY_RANK = IntArray(POPCORN_N).also { for (i in 0 until POPCORN_N) it[RANK[i]] = i }

    fun autoHeat(t: Double): Double {
        val up = 0.5 - 0.5 * cos(PI * seg(t, 1.2, 2.6))
        return up * (1 - easeInOutCubic(seg(t, 4.6, 5.6)))
    }

    fun autoPress(t: Double) = easeOutCubic(seg(t, 1.2, 1.3)) * (1 - easeOutCubic(seg(t, 4.8, 4.95)))

    /** The loop's story at loop time t, written into [s]. */
    fun autoStory(t: Double, s: PopcornStory): PopcornStory {
        s.now = t
        for (i in 0 until POPCORN_N) {
            s.popAt[i] = AUTO_POP[i]
            s.goneAt[i] = 6.4
        }
        s.emptyFor = 1.6
        s.lidAt = 4.6
        s.lidBack = 6.4
        s.labelAt = 5.0
        s.labelOff = 6.4
        s.heat = autoHeat(t)
        s.press = autoPress(t)
        return s
    }

    /** A corn's resting angle in the heap. */
    private fun heapRot(r: Int) = (hash(r + 101) - 0.5) * 140

    fun makePose(s: PopcornStory, g: PopcornGeo, out: PopcornPose): PopcornPose {
        val now = s.now
        val heat = clamp01(s.heat)
        val dd = s.emptyFor
        val jig = heat * heat * g.D * 0.006
        var bowl = 0.0
        var kc = 0
        var fc = 0
        var lc = 0
        java.util.Arrays.fill(out.has, false)
        val raw = out.raw
        for (i in 0 until POPCORN_N) {
            val r = RANK[i]
            val sx = g.px + SPOT[i * 2] * g.rx
            val sy = g.py + SPOT[i * 2 + 1] * g.ry
            val pop = s.popAt[i]
            val gone = s.goneAt[i]
            val goneCorn = pop <= now && gone <= now && pop < gone
            // The kernel: on the floor until it pops, jittering with the heat; dropped back in after the bowl.
            var showKernel = pop > now
            var dropY = 0.0
            var alpha = 1.0
            if (goneCorn) {
                val e = now - gone - dd * (0.31 + (0.31 * r) / POPCORN_N)
                val dur = dd * 0.22
                if (e >= 0) {
                    showKernel = true
                    val k = clamp01(e / dur)
                    dropY = -g.D * 0.2 * (1 - k * k)
                    if (k >= 1) dropY = -g.D * 0.012 * max(0.0, sin(PI * clamp01((e - dur) / 0.12)))
                    alpha = clamp01(k / 0.3)
                }
            }
            if (showKernel) {
                val k = 40 + floor(hash(i + 9) * 20)
                val ph = hash(i + 4) * 6.283
                val jx = jig * sin((2 * PI * k * now) / LOOP + ph)
                val jy = jig * 0.7 * sin((2 * PI * (k + 7) * now) / LOOP + ph * 1.7)
                val o = kc * 5
                out.kernels[o] = sx + jx
                out.kernels[o + 1] = sy + jy + dropY
                out.kernels[o + 2] = hash(i + 2) * 360 + jx * 8
                out.kernels[o + 3] = alpha
                out.kernels[o + 4] = (i % 4).toDouble()
                kc += 1
            }
            if (pop > now) continue
            val tau = now - pop
            // Hull flecks thrown out as it bursts.
            if (tau < 0.4) {
                for (f in 0 until 3) {
                    val a = -PI / 2 + (hash(i * 7 + f) - 0.5) * 2.6
                    val v = g.D * (0.35 + 0.4 * hash(i * 5 + f + 2))
                    val o = lc * 3
                    out.flecks[o] = sx + cos(a) * v * tau
                    out.flecks[o + 1] = sy + sin(a) * v * tau + g.D * 1.4 * tau * tau
                    out.flecks[o + 2] = 1 - tau / 0.4
                    lc += 1
                }
            }
            val hx = g.px + HEAP[r * 3] * g.rx * 0.86
            val hy = g.py + HEAP[r * 3 + 1] * g.ry * 0.8 - HEAP[r * 3 + 2] * g.D - g.D * 0.012
            if (goneCorn) {
                // Into the bowl: an arc up and over, shrinking, staggered by rank.
                val e = now - gone - dd * ((0.34 * r) / POPCORN_N)
                if (e < 0) {
                    putCorn(out, r, hx, hy, heapRot(r), 1.0, 1.0)
                    continue
                }
                val k = clamp01(e / (dd * 0.28))
                if (k >= 1) {
                    val late = e - dd * 0.28
                    if (late < 0.25) bowl = max(bowl, sin((PI * late) / 0.25))
                    continue
                }
                val u = easeInOutCubic(k)
                val mx = (hx + g.bowlX) / 2
                val my = min(hy, g.bowlY) - g.D * 0.32
                val o = fc * 5
                out.flying[o] = (1 - u) * (1 - u) * hx + 2 * (1 - u) * u * mx + u * u * g.bowlX
                out.flying[o + 1] = (1 - u) * (1 - u) * hy + 2 * (1 - u) * u * my + u * u * g.bowlY
                out.flying[o + 2] = heapRot(r) + 220 * u
                out.flying[o + 3] = 1 - 0.5 * u
                out.flying[o + 4] = (r % 6).toDouble()
                fc += 1
                continue
            }
            // In flight from the kernel's spot to its place in the heap, then a little bounce.
            val tt = 0.4 + 0.14 * hash(i + 31)
            val lidOn = pop < s.lidAt || s.lidBack > s.lidAt
            val hh = g.D * (if (lidOn) 0.07 + 0.04 * hash(i + 17) else 0.16 + 0.08 * hash(i + 17))
            val u = clamp01(tau / tt)
            var x = sx + (hx - sx) * u
            var y = sy + (hy - sy) * u - hh * 4 * u * (1 - u)
            var q = 0.2 * exp(-tau / 0.09) * cos((2 * PI * tau) / 0.16)
            if (tau > tt) {
                val l = tau - tt
                y -= g.D * 0.02 * abs(sin((PI * l) / 0.16)) * exp(-l / 0.11)
                q += 0.14 * exp(-l / 0.06)
                x = hx
            }
            val grow = 0.35 + 0.65 * easeOutBack(tau / 0.14)
            val spin = (if (hash(i + 51) > 0.5) 1 else -1) * (200 + 220 * hash(i + 57))
            val rot = heapRot(r) + spin * (1 - easeOutCubic(u))
            putCorn(out, r, x, y, rot, grow * (1 + q), grow * (1 - q))
        }
        // Back to front by heap order (the floor layer first).
        var cc = 0
        for (o in 0 until POPCORN_N) {
            val want = ORDER[o]
            if (!out.has[want]) continue
            val src = want * 6
            val dst = cc * 6
            for (k in 0 until 5) out.corns[dst + k] = raw[src + k]
            out.corns[dst + 5] = (want % 6).toDouble()
            cc += 1
        }
        // The lid: rattled by pops underneath, then lifted onto the heap, then lowered back.
        var lidX = 0.0
        var lidY = 0.0
        var lidRot = 0.0
        for (i in 0 until POPCORN_N) {
            val tau = now - s.popAt[i] - 0.05
            if (tau >= 0 && tau < 0.12 && s.popAt[i] < s.lidAt) {
                lidY -= g.D * 0.012 * sin((PI * tau) / 0.12)
                lidRot += (hash(i + 77) - 0.5) * 3 * sin((PI * tau) / 0.12)
            }
        }
        val lift = easeOutBack(seg(now, s.lidAt, s.lidAt + 0.45))
        val down = if (s.lidBack >= s.lidAt) easeInOutCubic(seg(now, s.lidBack + dd * 0.55, s.lidBack + dd * 0.83)) else 0.0
        val perched = lift * (1 - down)
        lidX += g.D * 0.05 * perched
        lidY += -g.D * 0.1 * perched
        lidRot += -9 * perched
        if (s.lidBack >= s.lidAt) lidY -= g.D * 0.008 * sin(PI * seg(now, s.lidBack + dd * 0.83, s.lidBack + dd * 0.95))
        val on = easeInOutCubic(seg(now, s.labelAt, s.labelAt + 0.35))
        val off = if (s.labelOff >= s.labelAt) easeInOutCubic(seg(now, s.labelOff + dd * 0.62, s.labelOff + dd * 0.84)) else 0.0
        val shake = heat * heat * g.D * 0.0025
        out.heat = heat
        out.press = clamp01(s.press)
        out.shakeX = shake * sin((2 * PI * 47 * now) / LOOP)
        out.shakeY = shake * 0.6 * sin((2 * PI * 61 * now) / LOOP + 1)
        out.kernelCount = kc
        out.cornCount = cc
        out.flyingCount = fc
        out.fleckCount = lc
        out.lidX = lidX
        out.lidY = lidY
        out.lidRot = lidRot
        out.label = on * (1 - off)
        out.bowl = bowl
        return out
    }

    private fun putCorn(out: PopcornPose, r: Int, x: Double, y: Double, rot: Double, sx: Double, sy: Double) {
        val o = r * 6
        out.raw[o] = x
        out.raw[o + 1] = y
        out.raw[o + 2] = rot
        out.raw[o + 3] = sx
        out.raw[o + 4] = sy
        out.has[r] = true
    }
}
