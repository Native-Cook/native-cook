package com.ngocdevv.nativecook.blocks.inputs.morsekeyinput

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Morse Key Input — timing, the tape and the decoder, ported from morseKeyMotion.ts. Every frame of
 the self-playing loop is a pure function of the loop time t; under a finger a small simulation
 (MorseSim) is stepped once per frame and read through the same pose.

 LOOP = 9 s, unit u = 0.24 s (a dot is one unit, the gap inside a letter one, between letters three):
   0.0 – 1.3   rest (the poster: the key at rest, a blank leader of tape, two empty answer slots)
   1.3 – 2.98  H: · · · · (presses at 1.30, 1.78, 2.26, 2.74)     3.48  H is decoded into slot 1
   3.70 – 4.42 I: · · (presses at 3.70, 4.18)                       4.92  I is decoded into slot 2
   1.1 – 5.1   the tape runs at 34 dp/s (0.2 s to speed up and to stop), printing a mark per press
   5.3 – 6.8   hold: "Correct · +40"
   6.8 – 7.9   the printed tape tears off at the register and falls away; 7.0 – 7.5 the answer
               rolls out and the chip fades
   7.6 – 8.4   a fresh leader feeds out of the register      8.4 – 9.0 rest
*/
internal object MorseMotion {
    const val LOOP = 9.0
    const val UNIT = 0.24
    const val TAPE_SPEED = 34.0
    /** Touch: a press longer than this is a dash; a pause longer than LETTER_GAP ends the letter. */
    const val DASH_AT = 0.2
    const val LETTER_GAP = 0.55
    const val IDLE_BEFORE_BACK = 2.0
    const val BACK_FOR = 0.9
    /** The lever's travel stops on the contact: depth 1. */
    const val KEY_OMEGA = 60.0
    const val KEY_ZETA = 0.5

    private val PRESSES = arrayOf(
        doubleArrayOf(1.3, 1.54),
        doubleArrayOf(1.78, 2.02),
        doubleArrayOf(2.26, 2.5),
        doubleArrayOf(2.74, 2.98),
        doubleArrayOf(3.7, 3.94),
        doubleArrayOf(4.18, 4.42),
    )
    private val DECODE = doubleArrayOf(3.48, 4.92)
    private val ANSWER = arrayOf("H", "I")

    val MORSE = mapOf(
        ".-" to "A", "-..." to "B", "-.-." to "C", "-.." to "D", "." to "E", "..-." to "F", "--." to "G", "...." to "H", ".." to "I",
        ".---" to "J", "-.-" to "K", ".-.." to "L", "--" to "M", "-." to "N", "---" to "O", ".--." to "P", "--.-" to "Q", ".-." to "R",
        "..." to "S", "-" to "T", "..-" to "U", "...-" to "V", ".--" to "W", "-..-" to "X", "-.--" to "Y", "--.." to "Z",
        "-----" to "0", ".----" to "1", "..---" to "2", "...--" to "3", "....-" to "4", "....." to "5", "-...." to "6", "--..." to "7", "---.." to "8", "----." to "9",
    )

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOut(x: Double): Double {
        val u = 1 - clamp01(x)
        return 1 - u * u * u
    }
    fun easeInOut(x: Double): Double {
        val v = clamp01(x)
        return if (v < 0.5) 4 * v * v * v else 1 - (-2 * v + 2).pow(3) / 2
    }
    fun hash(n: Double): Double {
        val s = sin(n * 12.9898 + 4.1414) * 43758.5453
        return s - floor(s)
    }

    /** Unit step response of an underdamped spring, 0 before τ = 0. */
    private fun springStep(tau: Double): Double {
        if (tau <= 0) return 0.0
        val wd = KEY_OMEGA * sqrt(1 - KEY_ZETA * KEY_ZETA)
        val e = exp(-KEY_ZETA * KEY_OMEGA * tau)
        return 1 - e * (cos(wd * tau) + (KEY_ZETA * KEY_OMEGA / wd) * sin(wd * tau))
    }

    /** The tape's run (dp) at loop time t: it speeds up over 0.2 s, runs, and slows over 0.2 s. */
    private fun runAt(t: Double): Double {
        val a = 0.2
        val tau = when {
            t <= 1.1 -> 0.0
            t < 1.1 + a -> (t - 1.1) * (t - 1.1) / (2 * a)
            t < 4.9 -> a / 2 + (t - 1.1 - a)
            t < 4.9 + a -> a / 2 + (4.9 - 1.1 - a) + (t - 4.9) - (t - 4.9) * (t - 4.9) / (2 * a)
            else -> 3.8
        }
        return tau * TAPE_SPEED
    }

    private val allMarks: DoubleArray by lazy {
        DoubleArray(PRESSES.size * 2) { i -> if (i % 2 == 0) runAt(PRESSES[i / 2][0] + 0.02) else runAt(PRESSES[i / 2][1]) }
    }

    /** The pose of the self-playing loop at loop time t; [leader] is the resting tape length. */
    fun loopPose(t: Double, leader: Double): MorsePose {
        val p = MorsePose()
        // The lever: superposed spring steps, stopped by the contact at depth 1.
        var depth = 0.0
        var downAt = -99.0
        var held = false
        for (pr in PRESSES) {
            depth += springStep(t - pr[0]) - springStep(t - pr[1])
            if (t >= pr[0]) {
                downAt = pr[0]
                held = t < pr[1]
            }
        }
        depth = max(0.0, min(1.0, depth))
        val run = runAt(t)
        val sb = StringBuilder()
        for (pr in PRESSES) {
            val d = pr[0]
            if (t < d) break
            if (if (d < DECODE[0]) t < DECODE[0] else t < DECODE[1]) sb.append('.')
        }
        for (i in 0 until 2) {
            val sl = p.slots[i]
            sl.out = easeInOut(seg(t, 7.0 + i * 0.07, 7.4 + i * 0.07))
            sl.ch = if (t >= DECODE[i] && t < 7.6) ANSWER[i] else ""
            sl.into = easeOut(seg(t, DECODE[i], DECODE[i] + 0.3))
        }
        val tear = t >= 6.8
        if (tear) {
            p.tape.len = leader * easeOut(seg(t, 7.6, 8.4))
            p.tape.run = 0.0
            p.tape.setMarks(DoubleArray(0), 0)
        } else {
            p.tape.len = leader + run
            p.tape.run = run
            var n = 0
            val marks = DoubleArray(PRESSES.size * 2)
            for (pr in PRESSES) {
                if (t < pr[0] + 0.02) break
                marks[n++] = runAt(pr[0] + 0.02)
                marks[n++] = runAt(min(t, pr[1]))
            }
            p.tape.setMarks(marks, n)
        }
        p.torn.len = leader + runAt(LOOP)
        p.torn.run = runAt(LOOP)
        if (tear) p.torn.setMarks(allMarks, allMarks.size) else p.torn.setMarks(DoubleArray(0), 0)
        p.fall = if (tear) seg(t, 6.8, 7.9) else 0.0
        p.depth = depth
        p.sparkAge = t - downAt - 0.02
        p.hum = if (held) 1.0 else 0.0
        p.pen = if (depth > 0.85) 1.0 else 0.0
        p.buffer = sb.toString()
        p.bufferAlpha = if (p.buffer.isNotEmpty()) 1.0 else 0.0
        p.chip = easeOut(seg(t, 5.3, 5.6)) * (1 - easeInOut(seg(t, 6.8, 7.1)))
        p.correct = true
        return p
    }

    /** The pose under (or after) a finger at clock time [now]. */
    fun simPose(s: MorseSim, now: Double, leader: Double): MorsePose {
        val p = MorsePose()
        val back = if (s.backAt >= 0) now - s.backAt else -1.0
        for (i in 0 until 2) {
            val at = s.slotAt.getOrElse(i) { -1.0 }
            val sl = p.slots[i]
            sl.ch = s.decoded.getOrElse(i) { "" }
            sl.into = if (at < 0) 0.0 else easeOut((now - at) / 0.3)
            sl.out = if (back < 0) 0.0 else easeInOut(seg(back, 0.05 + i * 0.07, 0.45 + i * 0.07))
        }
        p.chip = if (s.chipAt < 0) 0.0 else easeOut((now - s.chipAt) / 0.3) * (if (back < 0) 1.0 else 1 - easeInOut(back / 0.3))
        if (back >= 0) {
            p.tape.len = leader * easeOut(seg(back, 0.25, BACK_FOR))
            p.tape.run = 0.0
            p.tape.setMarks(DoubleArray(0), 0)
        } else {
            p.tape.len = s.base + s.run
            p.tape.run = s.run
            p.tape.setMarks(s.marks, s.markCount)
        }
        p.depth = s.depth
        p.sparkAge = now - s.downAt - 0.02
        p.hum = if (s.held) 1.0 else 0.0
        p.pen = if (s.depth > 0.85) 1.0 else 0.0
        p.fall = if (back >= 0) seg(back, 0.0, BACK_FOR) else 0.0
        val torn = s.torn
        if (torn != null) {
            p.torn.len = torn.len
            p.torn.run = torn.run
            p.torn.setMarks(torn.marks, torn.count)
        }
        p.buffer = s.buffer
        p.bufferAlpha = if (s.buffer.isNotEmpty() && back < 0) 1.0 else 0.0
        p.correct = s.correct
        return p
    }
}

internal class MorseSlot {
    var ch = ""
    /** Rolling in 0 … 1 and out 0 … 1. */
    var into = 0.0
    var out = 0.0
}

/** Tape length out of the register (dp), the tape run so far, and the marks as [from, to] in run coordinates. */
internal class MorseStrip {
    var len = 0.0
    var run = 0.0
    var marks = DoubleArray(0)
    var count = 0

    fun setMarks(m: DoubleArray, n: Int) {
        marks = m
        count = n
    }

    fun copy(): MorseStrip = MorseStrip().also {
        it.len = len
        it.run = run
        it.marks = marks.copyOf(count)
        it.count = count
    }
}

internal class MorsePose {
    var depth = 0.0
    /** Seconds since the contact last closed (sparks), and how hard it hums while held. */
    var sparkAge = 99.0
    var hum = 0.0
    /** Pen on the tape (0 … 1). */
    var pen = 0.0
    val tape = MorseStrip()
    /** The torn strip falling away: progress 0 … 1 (0 = none). */
    var fall = 0.0
    val torn = MorseStrip()
    val slots = arrayOf(MorseSlot(), MorseSlot())
    /** The symbols keyed for the letter in progress, e.g. "..-". */
    var buffer = ""
    var bufferAlpha = 0.0
    /** The result chip: 0 hidden … 1 shown, and whether the answer is right. */
    var chip = 0.0
    var correct = false
}

/** The finger's simulation, stepped once per frame. */
internal class MorseSim(leader: Double) {
    var depth = 0.0
    var vel = 0.0
    var held = false
    var downAt = -99.0
    var upAt = -99.0
    var run = 0.0
    var speed = 0.0
    var marks = DoubleArray(64)
    var markCount = 0
    var buffer = ""
    val decoded = ArrayList<String>()
    val slotAt = ArrayList<Double>()
    var chipAt = -1.0
    var correct = false
    var backAt = -1.0
    var torn: MorseStrip? = null
    /** Tape length when the finger took over (the leader may still be feeding out). */
    var base = leader

    fun strip() = MorseStrip().also {
        it.len = base + run
        it.run = run
        it.marks = marks.copyOf(markCount)
        it.count = markCount
    }

    /** Steps the lever and the tape, and decodes a finished letter. Returns true when a letter landed. */
    fun step(now: Double, dt: Double): Boolean {
        val k = MorseMotion
        val target = if (held) 1.0 else 0.0
        // Semi-implicit spring; the contact stops the lever with a small bounce.
        vel += (-k.KEY_OMEGA * k.KEY_OMEGA * (depth - target) - 2 * k.KEY_ZETA * k.KEY_OMEGA * vel) * dt
        depth += vel * dt
        if (depth > 1) {
            depth = 1.0
            vel = -0.25 * vel
        }
        if (depth < 0) {
            depth = 0.0
            vel = 0.0
        }
        // The tape runs while keying and stops about a second after the last release.
        val busy = backAt < 0 && (held || now - upAt < 1.1)
        val want = if (busy) k.TAPE_SPEED else 0.0
        speed += (want - speed) * (1 - exp(-dt / 0.08))
        run += speed * dt
        if (held && markCount > 0) marks[markCount - 1] = run
        if (!held && buffer.isNotEmpty() && now - upAt > k.LETTER_GAP) {
            val ch = k.MORSE[buffer] ?: "?"
            buffer = ""
            if (decoded.size >= 2) {
                decoded.clear()
                slotAt.clear()
                chipAt = -1.0
            }
            decoded.add(ch)
            slotAt.add(now)
            if (decoded.size == 2) {
                chipAt = now + 0.2
                correct = decoded[0] == "H" && decoded[1] == "I"
            }
            return true
        }
        return false
    }

    fun keyDown(now: Double) {
        held = true
        downAt = now
        if (markCount + 2 > marks.size) marks = marks.copyOf(marks.size * 2)
        marks[markCount++] = run
        marks[markCount++] = run
    }

    fun keyUp(now: Double) {
        if (!held) return
        held = false
        upAt = now
        buffer += if (now - downAt > MorseMotion.DASH_AT) "-" else "."
        if (buffer.length > 6) buffer = buffer.takeLast(6)
    }
}
