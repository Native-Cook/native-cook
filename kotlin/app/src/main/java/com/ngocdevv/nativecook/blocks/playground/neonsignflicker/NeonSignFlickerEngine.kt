package com.ngocdevv.nativecook.blocks.playground.neonsignflicker

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

// The sign's life: which words are switched on, how bright each tube is, and how the sign swings
// (a port of the React Native block's neonSignSim.ts, step for step).
//
// Everything advances on one fixed tick from one clock, so the flicker, the swing and the
// self-playing script run the same, step for step, on any platform. A tube's brightness follows
// a target that is a pure function of the time since its word was switched (ignition buzz, then a
// warm-up from dim to full), plus a brown-out the script can trigger, plus the stutter of the one
// faulty letter. The "random" in the buzz is a hash of the tube and the time slot, so it is the
// same on every run.

// The simulation tick, in seconds, and the most ticks one slow frame may run (a 50 ms cap).
internal const val NEON_TICK = 1.0 / 60
internal const val NEON_MAX_TICKS = 3

// autoPlay (seconds): the sign powers on word by word, browns out, is pushed to swing, "late" is
// switched off and on, then everything powers down and the sign hangs still again.
internal const val NEON_LOOP = 12.0
private const val SCRIPT_OPEN_ON = 0.5
private const val SCRIPT_GLASS_ON = 1.45
private const val SCRIPT_LATE_ON = 2.15
private const val SCRIPT_SAG = 4.7
private const val SCRIPT_PUSH = 5.7
private const val SCRIPT_LATE_OFF = 8.3
private const val SCRIPT_LATE_BACK = 8.9
private val ScriptAllOff = doubleArrayOf(10.7, 10.85, 11.0)
// From here to the end of the loop the swing is damped harder, so it is still at the seam.
private const val SCRIPT_SETTLE = 9.5
// The push, in rad/s: about 10° of swing.
private const val SCRIPT_PUSH_SPEED = 0.55

// Tubes of a word light one after another, this far apart (s), and go out almost together.
private const val LETTER_STEP = 0.16
private const val LETTER_STEP_OFF = 0.03
// Ignition: the gas buzzes on and off in slots of 1/BUZZ_RATE s for IGNITE s, more often lit as
// it catches; then it warms from WARM_FROM to full with a time constant of WARM_TIME.
private const val IGNITE = 0.42
private const val BUZZ_RATE = 30.0
private const val WARM_FROM = 0.58
private const val WARM_TIME = 0.55
// The faulty letter: every STUTTER_EVERY s it may stutter (chance STUTTER_CHANCE) for
// STUTTER_LENGTH s, dropping out in slots of 1/STUTTER_RATE s; it also burns a little dimmer.
private const val STUTTER_EVERY = 1.6
private const val STUTTER_CHANCE = 0.7
private const val STUTTER_LENGTH = 0.42
private const val STUTTER_RATE = 22.0
private const val FAULTY_DIM = 0.86
// A brown-out lasts SAG_TIME s.
private const val SAG_TIME = 0.75
// The gas answers in a few ms: brightness chases its target at these rates (1/s).
private const val RISE_RATE = 55.0
private const val FALL_RATE = 28.0
// Reduced motion: no buzz or stutter, words fade in over this long (s).
private const val CALM_FADE = 0.45

// The sign hangs on two chains from one hook: a pendulum with a period of PERIOD s, its swing
// damped at DAMPING /s, and much faster while the loop settles.
private const val PERIOD = 2.0
private const val W0 = 2 * PI / PERIOD
private const val DAMPING = 1.0
private const val DAMPING_SETTLE = 2.4

internal const val NEON_WORDS = 3

/** A hash of two integers to [0, 1), with 32-bit wrapping maths like JavaScript's `Math.imul`. */
internal fun neonHash(a: Int, b: Int): Double {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h.toLong() and 0xffffffffL).toDouble() / 4294967296.0
}

/** The simulation state, mutated in place one tick at a time. */
internal class NeonSim(
    /** Per piece: its word and its place in the word. */
    val word: IntArray,
    val order: IntArray,
    /** The piece that stutters. */
    val faulty: Int,
    var looping: Boolean,
    var reduced: Boolean,
) {
    /** Simulation time (s) and the script's clock (s, wraps at the loop). */
    var time = 0.0
    var clock = 0.0
    /** Seconds left before the script resumes after a touch. */
    var hold = 0.0
    /** Per word: switched on (1) or off (0), and the time it was last switched. */
    val on = intArrayOf(0, 0, 0)
    val switchedAt = doubleArrayOf(-100.0, -100.0, -100.0)
    /** Per piece: its brightness 0…1. */
    val level = DoubleArray(word.size)
    /** Time of the last brown-out. */
    var sagAt = -100.0
    /** The swing: angle (rad, clockwise) and its speed (rad/s). */
    var theta = 0.0
    var omega = 0.0
    var dragging = false

    /** Switches a word on or off; does nothing when it already is. Returns whether it changed. */
    fun setWord(w: Int, value: Int): Boolean {
        if (on[w] == value) return false
        on[w] = value
        switchedAt[w] = time
        return true
    }

    /** How bright the stuttering letter is, `age` s after its word lit. */
    private fun stutter(i: Int, age: Double): Double {
        val episode = floor(age / STUTTER_EVERY).toInt()
        if (neonHash(i * 7 + 3, episode) > STUTTER_CHANCE) return 1.0
        val start = 0.15 + 0.9 * neonHash(i * 7 + 5, episode)
        val into = age - episode * STUTTER_EVERY - start
        if (into < 0 || into > STUTTER_LENGTH) return 1.0
        return if (neonHash(i * 13 + 1, floor(age * STUTTER_RATE).toInt()) > 0.45) 0.95 else 0.07
    }

    /** The brown-out's dip, `dt` s after it began: two blinks and a slow recovery. */
    private fun sag(dt: Double): Double {
        if (dt < 0 || dt > SAG_TIME) return 1.0
        if (dt < 0.07) return 0.22
        if (dt < 0.15) return 0.9
        if (dt < 0.24) return 0.12
        return 0.5 + 0.5 * ((dt - 0.24) / (SAG_TIME - 0.24))
    }

    /** What piece `i` is heading for right now. */
    private fun target(i: Int): Double {
        val w = word[i]
        if (on[w] == 0) {
            val age = time - switchedAt[w] - order[i] * LETTER_STEP_OFF
            if (age < 0) return level[i]
            // A last gasp as the current dies.
            if (!reduced && age < 0.06) return 0.12
            if (!reduced && age < 0.11) return 0.5
            return 0.0
        }
        val age = time - switchedAt[w] - order[i] * LETTER_STEP
        if (age < 0) return 0.0
        if (reduced) return min(1.0, age / CALM_FADE)
        var b: Double
        if (age < IGNITE) {
            // Lit more often as the gas catches.
            val slot = floor(age * BUZZ_RATE).toInt()
            val lit = neonHash(i * 31 + 17, slot) > 0.85 - 0.7 * age / IGNITE
            b = if (lit) 0.45 + 0.35 * neonHash(i * 31 + 19, slot) else 0.03
        } else {
            b = WARM_FROM + (1 - WARM_FROM) * (1 - exp(-(age - IGNITE) / WARM_TIME))
        }
        if (i == faulty && age >= IGNITE) b *= FAULTY_DIM * stutter(i, age - IGNITE)
        return b * sag(time - sagAt)
    }

    private fun crossed(before: Double, after: Double, at: Double) = before < at && after >= at

    /** Runs the script from `before` to `after` on the loop's clock. */
    private fun script(before: Double, after: Double) {
        if (crossed(before, after, SCRIPT_OPEN_ON)) setWord(0, 1)
        if (crossed(before, after, SCRIPT_GLASS_ON)) setWord(2, 1)
        if (crossed(before, after, SCRIPT_LATE_ON)) setWord(1, 1)
        if (crossed(before, after, SCRIPT_SAG)) sagAt = time
        if (crossed(before, after, SCRIPT_PUSH) && !reduced) omega += SCRIPT_PUSH_SPEED
        if (crossed(before, after, SCRIPT_LATE_OFF)) setWord(1, 0)
        if (crossed(before, after, SCRIPT_LATE_BACK)) setWord(1, 1)
        if (crossed(before, after, ScriptAllOff[0])) setWord(2, 0)
        if (crossed(before, after, ScriptAllOff[1])) setWord(0, 0)
        if (crossed(before, after, ScriptAllOff[2])) setWord(1, 0)
    }

    /** One tick: the script, every tube's brightness, then the swing. */
    fun step() {
        time += NEON_TICK
        if (hold > 0) {
            hold -= NEON_TICK
            if (hold <= 0) clock = 0.0
        } else if (looping) {
            val before = clock
            var after = before + NEON_TICK
            if (after >= NEON_LOOP) {
                script(before, NEON_LOOP)
                after -= NEON_LOOP
                script(-1.0, after)
            } else {
                script(before, after)
            }
            clock = after
        }

        val rise = 1 - exp(-RISE_RATE * NEON_TICK)
        val fall = 1 - exp(-FALL_RATE * NEON_TICK)
        for (i in level.indices) {
            val goal = target(i)
            val l = level[i]
            level[i] = l + (goal - l) * (if (goal > l) rise else fall)
        }

        if (dragging) return
        if (reduced) {
            theta = 0.0
            omega = 0.0
            return
        }
        val damping = if (looping && hold <= 0 && clock > SCRIPT_SETTLE) DAMPING_SETTLE else DAMPING
        val accel = -W0 * W0 * sin(theta) - damping * omega
        omega += accel * NEON_TICK
        theta += omega * NEON_TICK
    }

    /** Mean brightness of each word's tubes, for the light it throws. */
    fun wordLevels(out: DoubleArray) {
        val count = IntArray(NEON_WORDS)
        for (w in 0 until NEON_WORDS) out[w] = 0.0
        for (i in level.indices) {
            out[word[i]] += level[i]
            count[word[i]] += 1
        }
        for (w in 0 until NEON_WORDS) out[w] = if (count[w] > 0) out[w] / count[w] else 0.0
    }
}
