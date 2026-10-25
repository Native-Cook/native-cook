package com.ngocdevv.nativecook.blocks.gestures.abacusexpensecounter

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The abacus: geometry, the scripted loop, and the bead physics under the finger (same maths as
 abacusMotion.ts).

 Five rods (Food, Transit, Fun, Home, Other), ten beads each. A bead counts when it sits on the
 right ("logged") side; the rod's amount is count × price and the header total is their sum
 ($386.40 at rest).

 autoPlay (LOOP = 8 s), every bead a pure function of t:
   0.0 – 1.3   rest.
   1.3 – 5.0   six flicks: a fingertip pushes a bead 3 dp (80 ms) and flicks it; it glides right
               with friction (0.3 s), hits the stack and bounces back off it.
               Food ×3, Fun ×2, Other ×1: $386.40 → $469.00.
   5.0 – 6.2   hold on the totals.
   6.0 – 6.25  "Undo today" is pressed.
   6.2 – 7.5   the logged beads glide back (0.6 s, rods 120 ms apart) and bounce off the left stack.
   7.5 – 8.0   rest.

 Touch: beads slide with friction (4/s), collide (restitution 0.5), bounce off the frame (0.35),
 and below 45 dp/s a spring settles each to the nearer of its two slots. A grabbed bead follows
 the finger and pushes the beads in front.
 */

const val LOOP = 8.0
internal const val ABACUS_RODS = 5
internal const val ABACUS_BEADS = 10
internal val AbacusCategories = listOf("Food", "Transit", "Fun", "Home", "Other")
/** Cents per bead, beads logged at rest, and each category's weekly budget in cents. */
internal val AbacusPrices = intArrayOf(1600, 640, 1500, 4500, 460)
internal val AbacusRestCounts = intArrayOf(6, 5, 4, 4, 4)
internal val AbacusBudgets = intArrayOf(16000, 6000, 12000, 20000, 4000)
internal const val ABACUS_WEEK_BUDGET = 52000

internal class AbacusGeo(val w: Float, val h: Float, val boardY: Float, reserveBelow: Float) {
    val frame = 16f
    val boardX = 16f
    val boardW = w - 32f
    val band = min(64f, max(52f, (h - boardY - 2 * frame - reserveBelow) / ABACUS_RODS))
    val innerL = boardX + frame + 4
    val innerR = boardX + boardW - frame - 4
    val bw = min(26f, (innerR - innerL - 66) / ABACUS_BEADS)
    val bh = if (band >= 60) 30f else 27f
    /** Rod y (bead centres). */
    val rodY = FloatArray(ABACUS_RODS) { boardY + frame + it * band + band - bh / 2 - 6 }
    val boardH = 2 * frame + ABACUS_RODS * band
    /** Bead travel between its two slots. */
    val travel = innerR - innerL - ABACUS_BEADS * bw

    fun slotL(j: Int) = (innerL + j * bw + bw / 2).toDouble()
    fun slotR(j: Int) = (innerR - (ABACUS_BEADS - 1 - j) * bw - bw / 2).toDouble()
}

internal fun abacusSeg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)

internal fun abacusEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

private fun easeOut(x: Double) = 1 - (1 - x).pow(3)

/** Rest positions of every bead, rod by rod. */
internal fun abacusRestBeads(g: AbacusGeo, out: DoubleArray): DoubleArray {
    for (r in 0 until ABACUS_RODS) {
        val first = ABACUS_BEADS - AbacusRestCounts[r]
        for (j in 0 until ABACUS_BEADS) out[r * ABACUS_BEADS + j] = if (j >= first) g.slotR(j) else g.slotL(j)
    }
    return out
}

/** [rod, bead, flick time, undo time] */
private val Flicks = arrayOf(
    doubleArrayOf(0.0, 3.0, 1.4, 6.2),
    doubleArrayOf(0.0, 2.0, 1.95, 6.2),
    doubleArrayOf(0.0, 1.0, 2.5, 6.2),
    doubleArrayOf(2.0, 5.0, 3.15, 6.32),
    doubleArrayOf(2.0, 4.0, 3.7, 6.32),
    doubleArrayOf(4.0, 5.0, 4.35, 6.44),
)
private const val PUSH = 3.0
private const val PUSH_FOR = 0.08
private const val GLIDE_FOR = 0.3
private const val GLIDE_K = 2.2
private const val UNDO_FOR = 0.6

/** A bounce off a stack: |sin| arcs decaying, 0 → peak → 0, positive. */
private fun bounce(tau: Double, size: Double): Double {
    if (tau <= 0 || tau > 0.6) return 0.0
    return size * exp(-tau / 0.07) * abs(sin(PI * tau / 0.11))
}

/** Every bead's x at loop time t. */
internal fun abacusLoopBeads(g: AbacusGeo, t: Double, out: DoubleArray) {
    abacusRestBeads(g, out)
    for (f in Flicks) {
        val r = f[0].toInt()
        val j = f[1].toInt()
        val tf = f[2]
        val tu = f[3]
        val l = g.slotL(j)
        val rr = g.slotR(j)
        var x = l
        if (t >= tf && t < tf + PUSH_FOR) {
            x = l + PUSH * easeOut(abacusSeg(t, tf, tf + PUSH_FOR))
        } else if (t >= tf + PUSH_FOR) {
            val u = abacusSeg(t, tf + PUSH_FOR, tf + PUSH_FOR + GLIDE_FOR)
            val k = (1 - exp(-GLIDE_K * u)) / (1 - exp(-GLIDE_K))
            x = l + PUSH + (rr - l - PUSH) * k
            if (u >= 1) x = rr - bounce(t - (tf + PUSH_FOR + GLIDE_FOR), 4.5)
        }
        if (t >= tu) x = rr - (rr - l) * abacusEaseInOut(abacusSeg(t, tu, tu + UNDO_FOR)) + bounce(t - tu - UNDO_FOR, 3.0)
        out[r * ABACUS_BEADS + j] = x
    }
}

/** The scripted fingertip into [out]: x, y, alpha. */
internal fun abacusLoopFinger(g: AbacusGeo, t: Double, out: DoubleArray) {
    for (f in Flicks) {
        val r = f[0].toInt()
        val j = f[1].toInt()
        val tf = f[2]
        if (t < tf - 0.26 || t > tf + 0.3) continue
        val contactX = g.slotL(j) - g.bw / 2 - 8
        val y = g.rodY[r].toDouble()
        val inn = easeOut(abacusSeg(t, tf - 0.26, tf))
        var x = contactX - 14 * (1 - inn)
        var yy = y - 18 * (1 - inn)
        if (t >= tf) x += PUSH * easeOut(abacusSeg(t, tf, tf + PUSH_FOR))
        // The flick: the finger carries on 30 dp to the right and lifts away.
        val o = easeOut(abacusSeg(t, tf + PUSH_FOR, tf + 0.3))
        x += 30 * o
        yy -= 6 * o
        out[0] = x; out[1] = yy; out[2] = inn * (1 - o)
        return
    }
    out[0] = 0.0; out[1] = 0.0; out[2] = 0.0
}

/** How pressed the "Undo today" chip looks in the loop (0–1). */
internal fun abacusLoopUndoPress(t: Double) = easeOut(abacusSeg(t, 6.0, 6.08)) * (1 - abacusEaseInOut(abacusSeg(t, 6.14, 6.3)))

/** Logged beads on rod r for positions x. */
internal fun abacusCount(g: AbacusGeo, x: DoubleArray, r: Int): Int {
    var n = 0
    for (j in 0 until ABACUS_BEADS) if (x[r * ABACUS_BEADS + j] > (g.slotL(j) + g.slotR(j)) / 2) n++
    return n
}

private const val FRICTION = 4.0
private const val SETTLE_BELOW = 45.0
private const val SETTLE_K = 260.0
private const val RESTITUTION = 0.5
private const val WALL = 0.35
private const val HIT = 60.0

/**
 * One step of the beads under touch. [x], [v] are flat arrays (rod × bead); the held bead is
 * (heldRod, heldBead) moving to heldX, or heldRod = −1. Returns the strongest collision speed (0 = none).
 */
internal fun abacusStepBeads(g: AbacusGeo, x: DoubleArray, v: DoubleArray, heldRod: Int, heldBead: Int, heldX: Double, dt: Double): Double {
    var hit = 0.0
    val sub = max(1, ceil(dt * 240).toInt())
    val h = dt / sub
    val c = 2 * 0.8 * sqrt(SETTLE_K)
    val bw = g.bw.toDouble()
    for (s in 0 until sub) {
        for (r in 0 until ABACUS_RODS) {
            val base = r * ABACUS_BEADS
            val held = if (heldRod == r) heldBead else -1
            for (j in 0 until ABACUS_BEADS) {
                val i = base + j
                if (j == held) {
                    // Spread the finger's move over the frame's substeps, so the bead's speed is real.
                    val nx = x[i] + (heldX - x[i]) / (sub - s)
                    v[i] = ((nx - x[i]) / h).coerceIn(-2500.0, 2500.0)
                    x[i] = nx
                    continue
                }
                v[i] *= exp(-FRICTION * h)
                if (abs(v[i]) < SETTLE_BELOW) {
                    val l = g.slotL(j)
                    val rr = g.slotR(j)
                    val slot = if (abs(x[i] - l) < abs(x[i] - rr)) l else rr
                    v[i] += (SETTLE_K * (slot - x[i]) - c * v[i]) * h
                }
                x[i] += v[i] * h
            }
            repeat(3) {
                for (j in 0 until ABACUS_BEADS - 1) {
                    val a = base + j
                    val b = a + 1
                    val overlap = bw - (x[b] - x[a])
                    if (overlap <= 0) continue
                    if (j == held) {
                        x[b] += overlap
                        if (v[b] < v[a]) v[b] = v[a]
                    } else if (j + 1 == held) {
                        x[a] -= overlap
                        if (v[a] > v[b]) v[a] = v[b]
                    } else {
                        x[a] -= overlap / 2
                        x[b] += overlap / 2
                        val rel = v[a] - v[b]
                        if (rel > 0) {
                            val mean = (v[a] + v[b]) / 2
                            v[a] = mean - RESTITUTION * rel / 2
                            v[b] = mean + RESTITUTION * rel / 2
                            if (rel > HIT && rel > hit) hit = rel
                        }
                    }
                }
            }
            // Each bead stays where the frame leaves room for its neighbours; then the chain is
            // straightened both ways, so nothing overlaps and nothing passes through the frame.
            for (j in 0 until ABACUS_BEADS) {
                val i = base + j
                val lo = g.slotL(j)
                val hi = g.slotR(j)
                if (x[i] < lo) {
                    x[i] = lo
                    if (v[i] < 0) {
                        if (j == 0 && -v[i] > HIT && -v[i] > hit) hit = -v[i]
                        v[i] = if (j == 0) -v[i] * WALL else 0.0
                    }
                } else if (x[i] > hi) {
                    x[i] = hi
                    if (v[i] > 0) {
                        if (j == ABACUS_BEADS - 1 && v[i] > HIT && v[i] > hit) hit = v[i]
                        v[i] = if (j == ABACUS_BEADS - 1) -v[i] * WALL else 0.0
                    }
                }
            }
            for (j in 1 until ABACUS_BEADS) if (x[base + j] - x[base + j - 1] < bw) x[base + j] = x[base + j - 1] + bw
            for (j in ABACUS_BEADS - 2 downTo 0) if (x[base + j + 1] - x[base + j] < bw) x[base + j] = x[base + j + 1] - bw
        }
    }
    return hit
}

/** Whether every bead has come to rest in a slot. */
internal fun abacusSettled(g: AbacusGeo, x: DoubleArray, v: DoubleArray): Boolean {
    for (r in 0 until ABACUS_RODS) for (j in 0 until ABACUS_BEADS) {
        val i = r * ABACUS_BEADS + j
        if (abs(v[i]) > 1) return false
        val d = min(abs(x[i] - g.slotL(j)), abs(x[i] - g.slotR(j)))
        if (d > 0.3) return false
    }
    return true
}
