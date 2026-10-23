package com.ngocdevv.nativecook.blocks.playground.dominochain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// A run of dominoes standing on a table, each a rigid tile that tips about its leading bottom edge.
// A port, step for step, of the React Native `dominoSim.ts`.
//
// Everything is measured in table units (dp on the table plane). Tipping forward by φ, a domino
// turns about its front bottom edge under gravity: α = 3g / (2√(T² + H²)) · sin(φ − β₀), where
// β₀ = atan(T/H) is the lean it must pass before it falls by itself. A falling domino's top front edge
// meets the next one's back face when sin(φ − φₙ) = (d·cos φₙ − T) / H. If the next one is standing,
// the hit hands it a share of the falling one's spin and the falling one leans on it from then on.
// Laying a run, standing a fallen run back up and clearing it are animations on the same clock.
// No Android here: it compiles on its own.

// A domino's thickness (along its path), height and width, in table units.
internal const val TILE_T = 7.0
internal const val TILE_H = 40.0
internal const val TILE_W = 20.0
// Distance between the centres of neighbouring dominoes on a path.
internal const val SPACING = 21.0
internal const val MAX_DOMINOES = 96
// A drawn path never keeps more points than this (a full run needs about 700).
internal const val MAX_PATH = 1024

// Gravity, table units/s² (a gentle slow motion of the real thing at this scale).
private const val GRAVITY = 2600.0
// Share of the striking domino's spin the struck one starts with.
private const val TRANSFER = 0.82
// Share of a leaning domino's weight that pushes on the one it leans on.
private const val LEAN_PUSH = 0.7
// The last domino's bounce off the table: share of its spin it keeps, reversed.
private const val BOUNCE = 0.18
// A push that tips a domino over by itself, rad/s.
internal const val PUSH = 3.6

// Popping up: each domino drops in from POP_DROP above over POP_TIME s.
internal const val POP_TIME = 0.34
private const val POP_DROP = 34.0
// Standing back up, in reverse: each domino takes RISE_TIME s, RISE_STAGGER s after the one ahead of it.
private const val RISE_TIME = 0.42
private const val RISE_STAGGER = 0.028
// Clearing a run: each sinks over SINK_TIME s, SINK_STAGGER s after the one before.
private const val SINK_TIME = 0.26
private const val SINK_STAGGER = 0.012

private val BETA = atan(TILE_T / TILE_H)
private val ALPHA = (3 * GRAVITY) / (2 * sqrt(TILE_T * TILE_T + TILE_H * TILE_H))
private const val FLAT = PI / 2

/** Ease out with a little overshoot. */
internal fun dominoEaseOutBack(t: Double): Double {
    val k = min(1.0, max(0.0, t))
    val c = 1.7
    val u = k - 1
    return 1 + (c + 1) * u * u * u + c * u * u
}

/** Height a domino is drawn at (popping in from above), its scale and opacity. */
internal class Pop {
    var lift = 0.0
    var scale = 1.0
    var alpha = 1.0
}

/** One run. A domino's state: 0 standing, 1 falling, 2 leaning on the next one, 3 flat on the table, 4 standing back up. */
internal class DominoRun {
    var count = 0
    val x = DoubleArray(MAX_DOMINOES)
    val z = DoubleArray(MAX_DOMINOES)
    /** Heading along the path (unit vector on the table). */
    val hx = DoubleArray(MAX_DOMINOES)
    val hz = DoubleArray(MAX_DOMINOES)
    /** Distance to the next domino's centre. */
    val gap = DoubleArray(MAX_DOMINOES)
    val tilt = DoubleArray(MAX_DOMINOES)
    val spin = DoubleArray(MAX_DOMINOES)
    val state = IntArray(MAX_DOMINOES)
    /** When it popped up, when it starts to sink (infinity when it stays), and its tilt when the rise began. */
    val born = DoubleArray(MAX_DOMINOES)
    val sink = DoubleArray(MAX_DOMINOES) { Double.POSITIVE_INFINITY }
    private val riseFrom = DoubleArray(MAX_DOMINOES)
    private val riseAt = DoubleArray(MAX_DOMINOES)
    /** The pips on each half of its face, 0…6. */
    val pipsA = IntArray(MAX_DOMINOES)
    val pipsB = IntArray(MAX_DOMINOES)
    /** The run's own clock, s. */
    var time = 0.0
    /** Hits so far (for a click every few). */
    var hits = 0
    /** The last rise was gentle (reduced motion): no stagger, no overshoot. */
    private var gentle = false
    private var seed = 0x2d0e1f

    /** A small seeded random in [0, 1): xorshift32 on the run's seed. */
    private fun random(): Double {
        var s = seed
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        seed = s
        return (s.toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }

    /** Sinks every domino into the table, first to last; returns when the last one is gone. */
    fun sinkAll(): Double {
        var end = time
        for (i in 0 until count) {
            val at = time + i * SINK_STAGGER
            if (sink[i] > at) sink[i] = at
            end = max(end, sink[i] + SINK_TIME)
        }
        return end
    }

    /** Stands a domino up at (x, z) facing (hx, hz), popping up `delay` s from now. */
    fun add(x: Double, z: Double, hx: Double, hz: Double, delay: Double): Boolean {
        if (count >= MAX_DOMINOES) return false
        val i = count
        this.x[i] = x
        this.z[i] = z
        this.hx[i] = hx
        this.hz[i] = hz
        tilt[i] = 0.0
        spin[i] = 0.0
        state[i] = 0
        born[i] = time + delay
        sink[i] = Double.POSITIVE_INFINITY
        gap[i] = Double.POSITIVE_INFINITY
        pipsA[i] = floor(random() * 7).toInt()
        pipsB[i] = floor(random() * 7).toInt()
        if (i > 0) gap[i - 1] = hypot(x - this.x[i - 1], z - this.z[i - 1])
        count = i + 1
        return true
    }

    /** Re-aims a domino's heading (used while a path is drawn, once the next point is known). */
    fun aim(i: Int, hx: Double, hz: Double) {
        this.hx[i] = hx
        this.hz[i] = hz
    }

    /** Tips domino `i` forward with spin `w`, if it is standing. */
    fun push(i: Int, w: Double): Boolean {
        if (i < 0 || i >= count || state[i] != 0) return false
        state[i] = 1
        spin[i] = w
        return true
    }

    /** Whether any domino is down or on its way down. */
    val anyFallen: Boolean
        get() {
            for (i in 0 until count) if (state[i] != 0) return true
            return false
        }

    /** Whether something is still moving (falling, rising, popping up or sinking). */
    val busy: Boolean
        get() {
            for (i in 0 until count) {
                val s = state[i]
                if (s == 1 || s == 4) return true
                if (time < born[i] + POP_TIME) return true
                if (sink[i] != Double.POSITIVE_INFINITY) return true
            }
            return false
        }

    /** Stands every fallen domino back up, in reverse: the last one first (all at once when `gentle`). */
    fun raise(gentle: Boolean) {
        var k = 0
        this.gentle = gentle
        for (i in count - 1 downTo 0) {
            if (state[i] == 0) continue
            state[i] = 4
            riseFrom[i] = tilt[i]
            riseAt[i] = time + (if (gentle) 0.0 else k * RISE_STAGGER)
            spin[i] = 0.0
            k += 1
        }
    }

    /** The tilt at which domino i, leaning forward, touches the next one tilted by `next`. */
    private fun leanOn(i: Int, next: Double): Double {
        val s = (gap[i] * cos(next) - TILE_T) / TILE_H
        if (s >= 1) return FLAT + 1
        return next + asin(max(-1.0, s))
    }

    /** One fixed step of the run. */
    fun step(dt: Double) {
        time += dt
        // Back to front, so each domino sees where the one ahead of it is this step.
        for (i in count - 1 downTo 0) {
            val st = state[i]
            if (st == 1) {
                // Its own weight, plus the weight of the one leaning on it from behind, which pushes it on.
                var torque = sin(tilt[i] - BETA)
                if (i > 0 && state[i - 1] == 2) torque += LEAN_PUSH * max(0.0, sin(tilt[i - 1] - BETA))
                var w = spin[i] + ALPHA * torque * dt
                var a = tilt[i] + w * dt
                // Not pushed hard enough: it rocks back onto its base.
                if (a <= 0) {
                    a = 0.0
                    w = 0.0
                    state[i] = 0
                }
                val hasNext = i + 1 < count && sink[i + 1] == Double.POSITIVE_INFINITY
                val contact = if (hasNext) leanOn(i, tilt[i + 1]) else FLAT + 1
                if (hasNext && contact <= FLAT && a >= contact) {
                    a = contact
                    state[i] = 2
                    hits += 1
                    if (state[i + 1] == 0) {
                        state[i + 1] = 1
                        spin[i + 1] = max(w * TRANSFER, 1.0)
                    }
                    w = 0.0
                } else if (a >= FLAT) {
                    a = FLAT
                    w = -w * BOUNCE
                    if (abs(w) < 0.3) {
                        w = 0.0
                        state[i] = 3
                    }
                    hits += 1
                }
                tilt[i] = a
                spin[i] = w
            } else if (st == 2) {
                // Nothing left to lean on: it falls the rest of the way.
                if (i + 1 < count) tilt[i] = min(FLAT, leanOn(i, tilt[i + 1])) else state[i] = 1
            } else if (st == 4) {
                val p = (time - riseAt[i]) / RISE_TIME
                if (p >= 1) {
                    tilt[i] = 0.0
                    state[i] = 0
                } else if (p > 0) {
                    tilt[i] = riseFrom[i] * (1 - (if (gentle) p * (2 - p) else dominoEaseOutBack(p)))
                }
            }
        }
        // Drop the dominoes that have sunk away for good.
        if (count > 0 && sink[0] != Double.POSITIVE_INFINITY && time >= sink[count - 1] + SINK_TIME) count = 0
    }

    /** Fills [out] with domino i's drop-in height, scale and opacity. */
    fun pop(i: Int, out: Pop) {
        val p = (time - born[i]) / POP_TIME
        var lift = 0.0
        var scale = 1.0
        var alpha = 1.0
        if (p < 1) {
            val k = max(0.0, p)
            val e = dominoEaseOutBack(k)
            lift = POP_DROP * (1 - k) * (1 - k)
            scale = 0.7 + 0.3 * e
            alpha = min(1.0, k * 3)
        }
        val q = (time - sink[i]) / SINK_TIME
        if (q > 0) {
            val k = min(1.0, q)
            scale *= 1 - 0.3 * k
            alpha *= 1 - k
            lift -= 6 * k
        }
        out.lift = lift
        out.scale = scale
        out.alpha = alpha
    }
}

// ─── Paths ───────────────────────────────────────────────────────────────────────────────────────

/** A growable polyline on the table. */
internal class Polyline {
    var xs = DoubleArray(64)
    var zs = DoubleArray(64)
    var size = 0

    fun clear() {
        size = 0
    }

    fun add(x: Double, z: Double) {
        if (size >= MAX_PATH) return
        if (size == xs.size) {
            xs = xs.copyOf(size * 2)
            zs = zs.copyOf(size * 2)
        }
        xs[size] = x
        zs[size] = z
        size += 1
    }

    /** Length of the polyline. */
    fun length(): Double {
        var total = 0.0
        for (k in 1 until size) total += hypot(xs[k] - xs[k - 1], zs[k] - zs[k - 1])
        return total
    }

    /** Where along the polyline arc length `s` falls: the point and the heading there, into [out] (x, z, hx, hz). */
    fun pointAt(s: Double, out: DoubleArray) {
        if (size == 0) {
            out[0] = 0.0
            out[1] = 0.0
            out[2] = 1.0
            out[3] = 0.0
            return
        }
        var left = s
        for (k in 1 until size) {
            val dx = xs[k] - xs[k - 1]
            val dz = zs[k] - zs[k - 1]
            val len = hypot(dx, dz)
            if (left <= len || k == size - 1) {
                val u = if (len > 0) min(1.0, left / len) else 0.0
                out[0] = xs[k - 1] + dx * u
                out[1] = zs[k - 1] + dz * u
                out[2] = if (len > 0) dx / len else 1.0
                out[3] = if (len > 0) dz / len else 0.0
                return
            }
            left -= len
        }
        out[0] = xs[0]
        out[1] = zs[0]
        out[2] = 1.0
        out[3] = 0.0
    }

    companion object {
        /** Points of a spiral on the table, from its outer end inwards (table units). */
        fun spiral(cx: Double, cz: Double, outer: Double, inner: Double, turns: Double, start: Double): Polyline {
            val line = Polyline()
            val steps = 360
            for (k in 0..steps) {
                val t = k.toDouble() / steps
                val r = outer + (inner - outer) * t
                val a = start + t * turns * PI * 2
                line.add(cx + cos(a) * r, cz + sin(a) * r)
            }
            return line
        }
    }
}
