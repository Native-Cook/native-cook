package com.ngocdevv.nativecook.blocks.playground.pluckstringharp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The harp's physics, ported step for step from the React Native `PluckStringHarpSim.ts`. One
 * plain object is stepped once a frame and the composable draws what it finds.
 *
 * Each string is three standing waves (harmonics 1, 2, 3), each a damped oscillator. A finger
 * that crosses a string catches it and drags it sideways into a triangle; past SLIP the string
 * slips free and the triangle is handed to the harmonics, which ring and die away. Neighbours are
 * driven by each other's fundamental, so a plucked string sets the strings beside it shimmering.
 */

internal const val STRINGS = 8
internal const val MODES = 3
internal val NoteNames = arrayOf("C", "D", "E", "F", "G", "A", "B", "C")
// Semitones above the lowest string: a C major scale.
private val Semitones = doubleArrayOf(0.0, 2.0, 4.0, 5.0, 7.0, 9.0, 11.0, 12.0)

// Visible fundamental of the lowest string (Hz). Real strings move too fast to see; this keeps
// the wave readable at 60 fps while the scale still doubles across the octave.
private const val BASE_HZ = 5.2
// Decay rate of the fundamental (1/s); higher harmonics die faster by HARMONIC_DAMP per step.
private const val DECAY = 1.3
private const val HARMONIC_DAMP = 0.9
// How hard a string's fundamental drives its neighbours (one and two strings away).
private const val SYMPATHY = 0.022
private const val SYMPATHY_FAR = 0.4

// Catching: a finger crossing a string drags it up to SLIP dp sideways before it slips free.
internal const val SLIP = 18.0
// A touch that lands this close to a string catches it straight away.
private const val TOUCH_CATCH = 11.0
// Letting go of a string held less than this far still plucks it this hard.
private const val TAP_PULL = 9.0
// The catch point stays this far from either end of the string (share of its length).
private const val END_GUARD = 0.07

// Glow: brightens with amplitude, then fades over HEAT_TAU seconds after the string quiets.
private const val HEAT_TAU = 0.55
private const val HEAT_GAIN = 1.25

// Note names that float up from each pluck.
internal const val NOTE_POOL = 12
internal const val NOTE_LIFE = 1.7
// The finger's light trail: positions kept for TRAIL_LIFE seconds.
internal const val TRAIL = 18
internal const val TRAIL_LIFE = 0.26

// Clock: one capped step drives both the physics and the autoPlay script.
private const val DT_CAP = 1.0 / 30.0
private const val SUBSTEPS = 8

// autoPlay: an arpeggio up and back, a strum down, a strum up, one low note, then rest.
private const val LOOP = 9.5
// After a touch the loop waits this long, then starts over.
private const val USER_HOLD = 3.0

// Scripted strokes: [start (s), duration (s), from index, to index, from along, to along].
private val Script = arrayOf(
    // Arpeggio: C, E, G, C, G, E, each a short pluck to the right.
    doubleArrayOf(0.5, 0.22, -0.4, 0.75, 0.52, 0.55),
    doubleArrayOf(0.95, 0.22, 1.6, 2.75, 0.5, 0.53),
    doubleArrayOf(1.4, 0.22, 3.6, 4.75, 0.5, 0.53),
    doubleArrayOf(1.85, 0.24, 6.6, 7.8, 0.48, 0.52),
    doubleArrayOf(2.4, 0.22, 3.6, 4.75, 0.55, 0.58),
    doubleArrayOf(2.85, 0.22, 1.6, 2.75, 0.56, 0.59),
    // A strum down across all eight, then back up.
    doubleArrayOf(3.7, 0.42, -0.6, 7.7, 0.42, 0.56),
    doubleArrayOf(4.75, 0.46, 7.7, -0.6, 0.6, 0.46),
    // One low C to ring out the loop.
    doubleArrayOf(5.9, 0.26, -0.45, 0.8, 0.62, 0.64),
)

/** Where the strings are on screen (dp). Strings are vertical: `x`, and their two ends. */
internal class HarpGeometry(val x: DoubleArray, val top: DoubleArray, val bottom: DoubleArray)

/** Angular frequency of a string's fundamental. */
internal fun omega(string: Int) = 2 * PI * BASE_HZ * 2.0.pow(Semitones[string] / 12)

private fun clamp(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v

private fun easeInOut(t: Double) = if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

/** The harp's state: the same fields as the RN `HarpSim`, mutated in place once a frame. */
internal class HarpSim(var looping: Boolean, var still: Boolean) {
    var time = 0.0
    var clock = 0.0
    var hold = 0.0
    // The finger (real or scripted) and where it was last frame.
    var down = false
    var scripted = false
    var justDown = false
    var fx = 0.0
    var fy = 0.0
    var px = 0.0
    var py = 0.0
    // Harmonic displacement (dp) and speed per string: index string * MODES + mode.
    val q = DoubleArray(STRINGS * MODES)
    val qv = DoubleArray(STRINGS * MODES)
    val heat = DoubleArray(STRINGS)
    // A caught string: side it is pulled to (+1 right, -1 left, 0 free), where, and how far.
    val held = DoubleArray(STRINGS)
    val heldAt = DoubleArray(STRINGS) { 0.5 }
    val pull = DoubleArray(STRINGS)
    // Floating note names.
    val noteString = IntArray(NOTE_POOL)
    val noteX = DoubleArray(NOTE_POOL)
    val noteY = DoubleArray(NOTE_POOL)
    val noteAge = DoubleArray(NOTE_POOL) { -1.0 }
    var nextNote = 0
    // Finger trail, a ring of positions and when they were seen.
    val trailX = DoubleArray(TRAIL)
    val trailY = DoubleArray(TRAIL)
    val trailT = DoubleArray(TRAIL) { -10.0 }
    var trailHead = 0
    // Counts plucks made by a real finger; the composable sends a haptic when it grows.
    var userPlucks = 0

    /**
     * Displacement of string [i] at [s] (0 at the top, 1 at the bottom), in dp. A caught string is
     * a sharp triangle through the finger; a free one is the sum of its harmonics.
     */
    fun displacement(i: Int, s: Double): Double {
        if (held[i] != 0.0) {
            val at = heldAt[i]
            return if (s <= at) (pull[i] * s) / at else (pull[i] * (1 - s)) / (1 - at)
        }
        var d = 0.0
        for (k in 0 until MODES) d += q[i * MODES + k] * sin((k + 1) * PI * s)
        return if (still) 0.0 else d
    }

    /** How far string [i] swings at most at [s]: the outline of its vibration, in dp. */
    fun envelope(i: Int, s: Double): Double {
        if (held[i] != 0.0 || still) return 0.0
        val w = omega(i)
        var e = 0.0
        for (k in 0 until MODES) {
            val j = i * MODES + k
            val wk = w * (k + 1)
            val a = sqrt(q[j] * q[j] + (qv[j] / wk) * (qv[j] / wk))
            e += a * abs(sin((k + 1) * PI * s))
        }
        return e
    }

    /**
     * Hands a triangle of height [pull] peaking at `at` to the harmonics. The plucked-string series
     * b_k = 2·pull·sin(kπ·at) / (k²π²·at·(1−at)) is cut at three terms, then scaled so the curve
     * still passes through the finger, so nothing jumps on release.
     */
    private fun release(g: HarpGeometry, i: Int, pull: Double, sounded: Boolean, user: Boolean) {
        val at = heldAt[i]
        held[i] = 0.0
        this.pull[i] = 0.0
        var sum = 0.0
        val b = DoubleArray(MODES)
        for (k in 0 until MODES) {
            val n = (k + 1).toDouble()
            b[k] = (2 * sin(n * PI * at)) / (n * n * PI * PI * at * (1 - at))
            sum += b[k] * sin(n * PI * at)
        }
        val scale = if (abs(sum) > 1e-4) pull / sum else 0.0
        for (k in 0 until MODES) {
            q[i * MODES + k] = b[k] * scale
            qv[i * MODES + k] = 0.0
        }
        if (!sounded) return
        heat[i] = max(heat[i], min(1.0, (abs(pull) / SLIP) * HEAT_GAIN))
        // A note name rises from where the string was plucked.
        val n = nextNote
        nextNote = (n + 1) % NOTE_POOL
        noteString[n] = i
        noteX[n] = g.x[i]
        noteY[n] = g.top[i] + (g.bottom[i] - g.top[i]) * at
        noteAge[n] = 0.0
        if (user) userPlucks += 1
    }

    /**
     * Where the finger is along string [i], as a share of its length, kept off the ends. A string
     * with no length yet (before layout, or after a relayout mid-catch) is caught in the middle.
     */
    private fun catchAt(g: HarpGeometry, i: Int): Double {
        val length = g.bottom[i] - g.top[i]
        if (!(length > 1.0)) return 0.5
        val at = (fy - g.top[i]) / length
        return if (at.isFinite()) clamp(at, END_GUARD, 1 - END_GUARD) else 0.5
    }

    /** Catches string [i] at the finger, pulled to [side]. */
    private fun grab(g: HarpGeometry, i: Int, side: Double) {
        held[i] = side
        heldAt[i] = catchAt(g, i)
        pull[i] = fx - g.x[i]
        for (k in 0 until MODES) {
            q[i * MODES + k] = 0.0
            qv[i * MODES + k] = 0.0
        }
    }

    /** Catches, drags and releases strings as the finger moves across them. */
    private fun touchStrings(g: HarpGeometry) {
        val user = !scripted
        for (i in 0 until STRINGS) {
            val x = g.x[i]
            if (held[i] != 0.0) {
                val side = held[i]
                if (!down) {
                    // Let go: a string barely touched still sounds.
                    val p = if (abs(pull[i]) < TAP_PULL) side * TAP_PULL else pull[i]
                    release(g, i, p, true, user)
                    continue
                }
                val p = fx - x
                if (p * side < -2) {
                    // The finger went back across the string: it is let go without a sound.
                    release(g, i, 0.0, false, user)
                } else if (abs(p) >= SLIP) {
                    release(g, i, side * SLIP, true, user)
                } else {
                    pull[i] = p
                    heldAt[i] = catchAt(g, i)
                }
                continue
            }
            if (!down) continue
            // Is the finger on this string's length (a little past either end counts)?
            val within = fy > g.top[i] + 6 && fy < g.bottom[i] - 6
            if (!within) continue
            if (justDown) {
                if (abs(fx - x) < TOUCH_CATCH) grab(g, i, if (fx >= x) 1.0 else -1.0)
                continue
            }
            val before = px - x
            val now = fx - x
            if ((before < 0 && now >= 0) || (before > 0 && now <= 0)) {
                grab(g, i, if (now >= before) 1.0 else -1.0)
                // A fast finger has already passed the slip point.
                if (abs(pull[i]) >= SLIP) release(g, i, held[i] * SLIP, true, user)
            }
        }
        justDown = false
    }

    /** Integrates every free string's harmonics over [dt], with fixed substeps. */
    private fun ring(dt: Double) {
        val h = dt / SUBSTEPS
        repeat(SUBSTEPS) {
            for (i in 0 until STRINGS) {
                if (held[i] != 0.0) continue
                val w = omega(i)
                // Sympathy: the neighbours' fundamentals pull on this one's.
                var drive = 0.0
                for (d in -2..2) {
                    val j = i + d
                    if (d == 0 || j < 0 || j >= STRINGS) continue
                    drive += q[j * MODES] * (if (abs(d) == 1) 1.0 else SYMPATHY_FAR)
                }
                for (k in 0 until MODES) {
                    val n = i * MODES + k
                    val wk = w * (k + 1)
                    val gamma = DECAY * (1 + HARMONIC_DAMP * k)
                    val target = if (k == 0) SYMPATHY * drive else 0.0
                    val accel = -wk * wk * (q[n] - target) - 2 * gamma * qv[n]
                    // Semi-implicit Euler: stable for these frequencies at this step.
                    qv[n] += accel * h
                    q[n] += qv[n] * h
                }
            }
        }
    }

    /** Puts the scripted finger where the script says at `clock`, or lifts it. */
    private fun playScript(g: HarpGeometry) {
        for (stroke in Script) {
            val t = (clock - stroke[0]) / stroke[1]
            if (t < 0 || t > 1) continue
            val e = easeInOut(t)
            // A point on the path: x in string spacings from the first string, y as a share of the
            // string's length.
            val index = stroke[2] + (stroke[3] - stroke[2]) * e
            val along = stroke[4] + (stroke[5] - stroke[4]) * e
            val spacing = (g.x[STRINGS - 1] - g.x[0]) / (STRINGS - 1)
            val k = clamp(floor(index), 0.0, (STRINGS - 2).toDouble()).toInt()
            val f = clamp(index - k, 0.0, 1.0)
            val top = g.top[k] + (g.top[k + 1] - g.top[k]) * f
            val bottom = g.bottom[k] + (g.bottom[k + 1] - g.bottom[k]) * f
            val x = g.x[0] + index * spacing
            val y = top + (bottom - top) * along
            if (!down) {
                down = true
                justDown = true
                scripted = true
                px = x
                py = y
            }
            fx = x
            fy = y
            return
        }
        if (down && scripted) down = false
    }

    /** A real finger touched down, moved or lifted. */
    fun fingerDown(x: Double, y: Double) {
        down = true
        justDown = true
        scripted = false
        fx = x
        fy = y
        px = x
        py = y
        hold = USER_HOLD
    }

    fun fingerMove(x: Double, y: Double) {
        if (!down || scripted) return
        fx = x
        fy = y
        hold = USER_HOLD
    }

    fun fingerUp() {
        if (scripted) return
        down = false
        hold = USER_HOLD
    }

    /** One frame: the script, the strings under the finger, the waves, the glows, the notes. */
    fun step(g: HarpGeometry, frameDt: Double) {
        val dt = min(max(frameDt, 0.0), DT_CAP)
        if (dt <= 0) return
        time += dt

        if (hold > 0) {
            // A real touch pauses the loop; it starts over once the hand has been away a while.
            if (!down) hold -= dt
            if (hold <= 0) clock = 0.0
        } else if (looping) {
            clock += dt
            if (clock >= LOOP) clock -= LOOP
            playScript(g)
        }

        touchStrings(g)
        ring(dt)

        for (i in 0 until STRINGS) {
            val level = if (held[i] != 0.0) abs(pull[i]) / SLIP else envelope(i, 0.5) / SLIP
            val fade = heat[i] * exp(-dt / HEAT_TAU)
            heat[i] = max(fade, min(1.0, level * HEAT_GAIN))
        }

        for (n in 0 until NOTE_POOL) {
            if (noteAge[n] < 0) continue
            noteAge[n] += dt
            if (noteAge[n] > NOTE_LIFE) noteAge[n] = -1.0
        }

        if (down) {
            val h = (trailHead + 1) % TRAIL
            trailHead = h
            trailX[h] = fx
            trailY[h] = fy
            trailT[h] = time
        }
        px = fx
        py = fy
    }
}
