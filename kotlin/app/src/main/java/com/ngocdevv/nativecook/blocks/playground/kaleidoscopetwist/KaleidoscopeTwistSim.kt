package com.ngocdevv.nativecook.blocks.playground.kaleidoscopetwist

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The object cell of the kaleidoscope: glass beads and shards loose in a round chamber (a port of
// the React Native block's kaleidoscopeSim.ts, step for step).
//
// The chamber turns with the user's hand (or the script), so gravity, seen from inside it, turns
// the other way and the pieces tumble. Each piece is a circle for the physics: gravity, a little
// air drag, then pairwise contacts (overlap pushed apart, an impulse with some bounce and friction)
// and the chamber's round wall, over SUBSTEPS fixed substeps per tick. Shards also spin as they
// roll. Everything is plain arithmetic with a seeded random, so it runs the same, step for step, on
// any platform. The same clock drives the self-playing script: the turn, two shakes, the number of
// mirror segments and the iris that closes over the seam of the loop.

// One tick is 1/60 s; a slow frame runs at most MAX_TICKS of them (a 50 ms cap).
internal const val KAL_TICK = 1.0 / 60
internal const val KAL_MAX_TICKS = 3
private const val SUBSTEPS = 4
private const val PASSES = 2

// The chamber's radius, in cell units (drawn about 1:1 with dp).
internal const val CELL_R = 120.0
// Gravity (dp/s²), air drag (1/s), bounce between pieces and on the wall, and friction. The wall
// grips hard, so the pile is carried up the side as the chamber turns and then tumbles down.
private const val GRAVITY = 820.0
private const val AIR = 0.7
private const val BOUNCE = 0.28
private const val WALL_BOUNCE = 0.22
private const val FRICTION = 0.3
private const val WALL_GRIP = 0.3
// Spin follows rolling at this rate (1/s).
private const val ROLL_RATE = 14.0

// Pieces at the start, and the most a hand can add (the oldest added one goes first).
private const val START_PIECES = 52
internal const val MAX_PIECES = 64
// A tapped bead grows in over this long (s).
private const val GROW_TIME = 0.35

// Kinds of piece: a round glass bead, a flat shard, a tiny seed bead.
internal const val BEAD = 0
internal const val SHARD = 1
internal const val SEED = 2
// Jewel tones the pieces are made in.
internal const val TONES = 8

// The turn: free spin slows by this rate (1/s); under the script a spring (stiffness, damping)
// follows the script's angle.
private const val SPIN_DRAG = 1.6
private const val TURN_STIFFNESS = 26.0
private const val TURN_DAMPING = 10.2
// Segment count: a spring towards the chosen even count.
private const val SEG_STIFFNESS = 90.0
private const val SEG_DAMPING = 15.0
internal const val SEG_MIN = 6.0
internal const val SEG_MAX = 12.0
// The iris opens at this rate (1/s) when nothing scripts it.
private const val IRIS_RATE = 3.2

// autoPlay (seconds): the iris opens, the chamber turns 200°, is shaken, turns on to 420° with
// the segments going 8 → 12 → 6 → 8, is shaken again, and the iris closes while it all resets.
internal const val KAL_LOOP = 14.0
private const val IRIS_OPEN = 0.9
private const val IRIS_CLOSE_FROM = 12.9
private const val IRIS_CLOSE_TO = 13.5
private const val RESET_AT = 13.7
// After a touch the script picks up here, past the iris opening.
private const val RESUME_AT = 1.0
private val TurnKeys = arrayOf(
    doubleArrayOf(0.0, 0.0), doubleArrayOf(1.0, 0.0), doubleArrayOf(4.2, 200.0),
    doubleArrayOf(6.2, 200.0), doubleArrayOf(9.6, 420.0), doubleArrayOf(14.0, 420.0),
)
private val Shakes = doubleArrayOf(4.6, 10.3)
private const val SHAKE_SPEED = 300.0
private val SegKeys = arrayOf(doubleArrayOf(5.2, 12.0), doubleArrayOf(8.4, 6.0), doubleArrayOf(11.2, 8.0))
internal const val SEG_START = 8.0

/** Rounds like JavaScript's `Math.round` (halves up). */
internal fun jsRound(v: Double) = floor(v + 0.5)

private fun smooth(t: Double): Double {
    val u = max(0.0, min(1.0, t))
    return u * u * (3 - 2 * u)
}

/** The script's angle for the chamber at `t` (rad): eased between keys. */
private fun scriptAngle(t: Double): Double {
    // Past the reset the chamber is back at its start, hidden by the closed iris.
    if (t >= RESET_AT) return 0.0
    for (k in 1 until TurnKeys.size) {
        val t1 = TurnKeys[k][0]
        val a1 = TurnKeys[k][1]
        if (t <= t1) {
            val t0 = TurnKeys[k - 1][0]
            val a0 = TurnKeys[k - 1][1]
            return (a0 + (a1 - a0) * smooth((t - t0) / (t1 - t0))) * PI / 180
        }
    }
    return TurnKeys[TurnKeys.size - 1][1] * PI / 180
}

/** The script's iris at `t`: opens at the start of the loop, closes at its end. */
private fun scriptIris(t: Double): Double {
    if (t < IRIS_OPEN) return 1 - (1 - t / IRIS_OPEN).pow(3)
    if (t < IRIS_CLOSE_FROM) return 1.0
    return 1 - smooth((t - IRIS_CLOSE_FROM) / (IRIS_CLOSE_TO - IRIS_CLOSE_FROM))
}

private fun crossed(before: Double, after: Double, at: Double) = before < at && after >= at

/** The chamber and everything in it, mutated in place one tick at a time. Arrays hold MAX_PIECES. */
internal class KaleidoSim(seed: Int, var looping: Boolean, var reduced: Boolean) {
    var count = 0
    val x = DoubleArray(MAX_PIECES)
    val y = DoubleArray(MAX_PIECES)
    val vx = DoubleArray(MAX_PIECES)
    val vy = DoubleArray(MAX_PIECES)
    val r = DoubleArray(MAX_PIECES)
    val ang = DoubleArray(MAX_PIECES)
    val spin = DoubleArray(MAX_PIECES)
    val kind = IntArray(MAX_PIECES)
    val tone = IntArray(MAX_PIECES)
    /** Shape seed of each piece (a shard's outline), and when it was added (s). */
    val shape = IntArray(MAX_PIECES)
    val born = DoubleArray(MAX_PIECES)
    // The settled start, restored at the loop's seam.
    private val restX = DoubleArray(MAX_PIECES)
    private val restY = DoubleArray(MAX_PIECES)
    private val restR = DoubleArray(MAX_PIECES)
    private val restAng = DoubleArray(MAX_PIECES)
    private val restKind = IntArray(MAX_PIECES)
    private val restTone = IntArray(MAX_PIECES)
    private val restShape = IntArray(MAX_PIECES)
    var restCount = 0
        private set
    var seed: Int = if (seed == 0) 1 else seed
    var time = 0.0
    var clock = 0.0
    var hold = 0.0
    /** The chamber's turn (rad, clockwise) and its speed. */
    var angle = 0.0
    var angVel = 0.0
    /** The script turns the chamber relative to where it was when the script (re)started. */
    var turnBase = 0.0
    var dragging = false
    /** Segment count, continuous while it morphs, its speed and the even count it settles on. */
    var seg = SEG_START
    var segVel = 0.0
    var segTarget = SEG_START
    var pinching = false
    /** 0 = iris closed, 1 = open. */
    var iris = 0.0
    /** Next slot an added piece replaces once the cell is full. */
    var nextSlot = 0

    init {
        // Scattered through the chamber, then left to fall and settle for three seconds.
        for (i in 0 until START_PIECES) {
            val k = if (i % 4 == 3) SHARD else if (i % 5 == 1) SEED else if (i % 7 == 2) SHARD else BEAD
            val a = random() * PI * 2
            val d = sqrt(random()) * (CELL_R - 22)
            pushPiece(cos(a) * d, sin(a) * d, k)
        }
        for (k in 0 until 180) physics()
        for (i in 0 until count) {
            vx[i] = 0.0
            vy[i] = 0.0
            spin[i] = 0.0
        }
        x.copyInto(restX)
        y.copyInto(restY)
        r.copyInto(restR)
        ang.copyInto(restAng)
        kind.copyInto(restKind)
        tone.copyInto(restTone)
        shape.copyInto(restShape)
        restCount = count
        nextSlot = count
    }

    /** A small, fast, seeded random in [0, 1): xorshift32 on the state's seed. */
    fun random(): Double {
        var v = seed
        v = v xor (v shl 13)
        v = v xor (v ushr 17)
        v = v xor (v shl 5)
        seed = v
        return (v.toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }

    /** Puts every piece back where it settled at the start, still. */
    fun restore() {
        count = restCount
        restX.copyInto(x)
        restY.copyInto(y)
        restR.copyInto(r)
        restAng.copyInto(ang)
        restKind.copyInto(kind)
        restTone.copyInto(tone)
        restShape.copyInto(shape)
        vx.fill(0.0)
        vy.fill(0.0)
        spin.fill(0.0)
        born.fill(-10.0)
        angle = 0.0
        angVel = 0.0
        turnBase = 0.0
        seg = SEG_START
        segVel = 0.0
        segTarget = SEG_START
        nextSlot = restCount
    }

    private fun pushPiece(px: Double, py: Double, k: Int) {
        val radius = if (k == BEAD) 8 + random() * 7 else if (k == SHARD) 10 + random() * 7 else 3.6 + random() * 1.6
        val i = count
        x[i] = px
        y[i] = py
        vx[i] = 0.0
        vy[i] = 0.0
        r[i] = radius
        ang[i] = random() * PI * 2
        spin[i] = 0.0
        kind[i] = k
        tone[i] = floor(random() * TONES).toInt()
        shape[i] = floor(random() * 100000).toInt()
        born[i] = -10.0
        count += 1
    }

    /** One tick of the pieces: SUBSTEPS of gravity, drag, contacts and the wall. */
    private fun physics() {
        val h = KAL_TICK / SUBSTEPS
        val gx = GRAVITY * sin(angle)
        val gy = GRAVITY * cos(angle)
        val drag = 1 - AIR * h
        val n = count
        for (s in 0 until SUBSTEPS) {
            for (i in 0 until n) {
                vx[i] = (vx[i] + gx * h) * drag
                vy[i] = (vy[i] + gy * h) * drag
                x[i] += vx[i] * h
                y[i] += vy[i] * h
            }
            for (pass in 0 until PASSES) {
                for (i in 0 until n) {
                    for (j in i + 1 until n) {
                        val dx = x[j] - x[i]
                        val dy = y[j] - y[i]
                        val rr = r[i] + r[j]
                        val d2 = dx * dx + dy * dy
                        if (d2 >= rr * rr || d2 < 1e-6) continue
                        val d = sqrt(d2)
                        val nx = dx / d
                        val ny = dy / d
                        // Mass goes with area.
                        val mi = r[i] * r[i]
                        val mj = r[j] * r[j]
                        val wi = mj / (mi + mj)
                        val wj = mi / (mi + mj)
                        val overlap = rr - d
                        x[i] -= nx * overlap * wi
                        y[i] -= ny * overlap * wi
                        x[j] += nx * overlap * wj
                        y[j] += ny * overlap * wj
                        if (pass > 0) continue
                        val rvx = vx[j] - vx[i]
                        val rvy = vy[j] - vy[i]
                        val vn = rvx * nx + rvy * ny
                        if (vn >= 0) continue
                        val imp = -(1 + BOUNCE) * vn
                        vx[i] -= nx * imp * wi
                        vy[i] -= ny * imp * wi
                        vx[j] += nx * imp * wj
                        vy[j] += ny * imp * wj
                        // Friction along the contact, at most FRICTION of the push.
                        val tx = -ny
                        val ty = nx
                        val vt = rvx * tx + rvy * ty
                        val f = max(-FRICTION * imp, min(FRICTION * imp, vt * 0.5))
                        vx[i] += tx * f * wi
                        vy[i] += ty * f * wi
                        vx[j] -= tx * f * wj
                        vy[j] -= ty * f * wj
                    }
                }
                for (i in 0 until n) {
                    val d = hypot(x[i], y[i])
                    val lim = CELL_R - r[i]
                    if (d <= lim || d < 1e-6) continue
                    val nx = x[i] / d
                    val ny = y[i] / d
                    x[i] = nx * lim
                    y[i] = ny * lim
                    val vn = vx[i] * nx + vy[i] * ny
                    if (vn > 0) {
                        vx[i] -= (1 + WALL_BOUNCE) * vn * nx
                        vy[i] -= (1 + WALL_BOUNCE) * vn * ny
                    }
                    // Rolling along the wall: the spin follows the speed along it.
                    val vt = -vx[i] * ny + vy[i] * nx
                    vx[i] -= -ny * vt * WALL_GRIP
                    vy[i] -= nx * vt * WALL_GRIP
                    spin[i] += (vt / r[i] - spin[i]) * min(1.0, ROLL_RATE * h)
                }
            }
            for (i in 0 until n) ang[i] += spin[i] * h
        }
    }

    /** Drops a new piece at (px, py) in the chamber's frame; once full, it replaces the oldest added. */
    fun addPiece(px: Double, py: Double) {
        val d = hypot(px, py)
        val lim = CELL_R - 20
        val qx = if (d > lim) px / d * lim else px
        val qy = if (d > lim) py / d * lim else py
        val k = if (random() > 0.6) SHARD else BEAD
        if (count < MAX_PIECES) {
            pushPiece(qx, qy, k)
            born[count - 1] = time
            return
        }
        val i = nextSlot
        nextSlot = if (i + 1 >= MAX_PIECES) restCount else i + 1
        x[i] = qx
        y[i] = qy
        vx[i] = 0.0
        vy[i] = 0.0
        kind[i] = k
        r[i] = if (k == BEAD) 8 + random() * 7 else 10 + random() * 7
        tone[i] = floor(random() * TONES).toInt()
        shape[i] = floor(random() * 100000).toInt()
        born[i] = time
    }

    /** Gives every piece a random kick, mostly against gravity. */
    fun shake(speed: Double) {
        val gx = sin(angle)
        val gy = cos(angle)
        for (i in 0 until count) {
            val a = random() * PI * 2
            val s = speed * (0.4 + 0.6 * random())
            vx[i] += cos(a) * s * 0.6 - gx * s
            vy[i] += sin(a) * s * 0.6 - gy * s
            spin[i] += (random() - 0.5) * 18
        }
    }

    /** Size of piece `i` now: added pieces grow in. */
    fun grownRadius(i: Int): Double {
        val age = (time - born[i]) / GROW_TIME
        if (age >= 1) return r[i]
        val t = max(0.0, age)
        // An ease-out with a small overshoot.
        val s = 1 + 2.2 * (t - 1).pow(3) + 1.2 * (t - 1).pow(2)
        return r[i] * (0.55 + 0.45 * s)
    }

    /** Sets the segment count the spring heads for (an even number in range). */
    fun setSegments(value: Double) {
        segTarget = max(SEG_MIN, min(SEG_MAX, jsRound(value / 2) * 2))
    }

    /** One tick: the script, the chamber's turn, the segment spring, the iris, then the pieces. */
    fun step() {
        time += KAL_TICK
        var scripted = false
        if (hold > 0) {
            // A hand still on the glass keeps the script paused; the hold counts down once it lets go.
            if (!dragging && !pinching) hold -= KAL_TICK
            if (hold <= 0) {
                clock = RESUME_AT
                turnBase = angle - scriptAngle(RESUME_AT)
            }
        } else if (looping) {
            scripted = true
            val before = clock
            var after = before + KAL_TICK
            if (after >= KAL_LOOP) after -= KAL_LOOP
            for (at in Shakes) if (crossed(before, after, at) && !reduced) shake(SHAKE_SPEED)
            for (key in SegKeys) if (crossed(before, after, key[0])) setSegments(key[1])
            if (crossed(before, after, RESET_AT)) restore()
            clock = after
        }

        // The turn.
        if (!dragging) {
            if (scripted) {
                // The script's angle is measured from where it (re)started.
                val goal = turnBase + scriptAngle(clock)
                val acc = TURN_STIFFNESS * (goal - angle) - TURN_DAMPING * angVel
                angVel += acc * KAL_TICK
            } else {
                angVel *= exp(-SPIN_DRAG * KAL_TICK)
            }
            angle += angVel * KAL_TICK
        }

        // The segments.
        if (!pinching) {
            if (reduced) {
                seg = segTarget
                segVel = 0.0
            } else {
                val acc = SEG_STIFFNESS * (segTarget - seg) - SEG_DAMPING * segVel
                segVel += acc * KAL_TICK
                seg += segVel * KAL_TICK
            }
        }

        // The iris.
        iris = if (scripted) scriptIris(clock) else iris + (1 - iris) * (1 - exp(-IRIS_RATE * KAL_TICK))

        physics()
    }
}
