package com.ngocdevv.nativecook.blocks.pickers.neoncontourmood

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** The autoplay state at one loop time (RN `Look`). */
internal class NeonContourMoodLook(rest: Double = NeonContourMoodMotion.REST) {
    var mood = rest
    val echoes = DoubleArray(7) { rest }
    var wake = 0.0
    var press = 0.0
    var breath = 0.0
    var radiate = 0.0
    var time = 0.0
}

internal class NeonContourMoodSpring(val damping: Double, val stiffness: Double, val mass: Double)

/** Every motion value of the block (`ncm-motion.ts`): pure scalar maths, the loop path, the springs and the label choreography. */
internal object NeonContourMoodMotion {
    const val LOOP = 8.0
    const val REST = 1.0

    // loop phases (s)
    const val T_REST_END = 0.6
    const val T_DRAG_END = 1.85
    const val T_HOLD_END = 2.85
    const val T_FLICK_END = 3.85
    const val T_RADIATE_END = 4.7
    const val T_RETURN_END = 6.9

    const val PATH_REST = 1.0
    const val PATH_HOLD = 2.7
    const val PATH_END = 6.0

    const val HOLD_GAP = 0.22
    const val HOLD_RISE = 0.18
    const val HOLD_RING_DELAY = 0.04
    const val HOLD_SLOW = 0.4
    const val HOLD_ON = 1.72
    const val HOLD_OFF = 2.92

    val SNAP = NeonContourMoodSpring(18.0, 220.0, 1.0)
    val GLIDE = NeonContourMoodSpring(22.1, 157.9, 1.0)
    val PRESS = NeonContourMoodSpring(30.5, 322.3, 1.0)
    val TAG_SPRING = NeonContourMoodSpring(20.0, 260.0, 1.0)
    val SQUEEZE = NeonContourMoodSpring(14.0, 200.0, 1.0)

    /** Closed-form spring response: response in s and damping ratio. */
    class Resp(val response: Double, val zeta: Double)
    private fun resp(s: NeonContourMoodSpring): Resp {
        val w = sqrt(s.stiffness)
        return Resp(2 * PI / w, s.damping / (2 * w))
    }
    val RESP_GLIDE = resp(GLIDE)
    val RESP_TAG = resp(TAG_SPRING)

    const val RESUME_AFTER = 2.0
    const val RETURN_DURATION = 0.6
    const val PROJECTION = 0.12
    const val MAX_FLICK = 2
    const val RUBBER = 0.35
    const val ECHO_MS = 96.0
    const val WAKE_TAU = 0.18
    const val SPEED_TAU = 0.05

    const val HYST = 0.62

    const val STRIKE_IN_STEP = 0.026
    const val STRIKE_IN_RISE = 0.03
    const val STRIKE_DIP_AT = 0.05
    const val STRIKE_DIP_DUR = 0.024
    const val STRIKE_DIP_TO = 0.45

    const val GLOW_BLUR_FROM = 2.4
    const val GLOW_BLUR_TO = 6.0
    const val GLOW_ALPHA = 0.35
    const val GLOW_GROW = 0.16

    const val WORD_OUT_DUR = 0.17
    const val WORD_OUT_BLUR = 14.0
    const val WORD_OUT_SCALE = 0.94
    const val WORD_IN_START = 0.08
    const val WORD_IN_DUR = 0.2
    const val WORD_IN_SCALE = 1.05
    const val WORD_TUBE = 0.22
    const val WORD_STRIKE = 0.2

    const val TAG_OUT_START = 0.02
    const val TAG_OUT_DUR = 0.09
    const val TAG_SLIDE = 2.0
    const val TAG_IN_START = 0.15
    const val TAG_IN_DUR = 0.16
    const val TAG_RISE = 2.0
    const val TAG_SPRING_START = 0.08
    const val TAG_ZOOM = 0.06
    const val REDUCED_DIP = 0.12
    const val LABEL_SETTLE = 1.2

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = if (v < lo) lo else if (v > hi) hi else v
    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun smooth(t: Double): Double {
        val x = if (t < 0) 0.0 else if (t > 1) 1.0 else t
        return x * x * x * (x * (x * 6 - 15) + 10)
    }

    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = 2 * PI / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(max(1e-9, 1 - zeta * zeta))
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    private fun wrap(time: Double) = ((time % LOOP) + LOOP) % LOOP

    /** Mood of the loop's finger at loop time [time], for a picker with scale = (n - 1) / 6. */
    fun moodAt(time: Double, scale: Double = 1.0): Double {
        val t = wrap(time)
        var m = PATH_REST
        if (t >= T_REST_END && t < T_RETURN_END) {
            m = if (t < T_DRAG_END) mix(PATH_REST, PATH_HOLD, smooth((t - T_REST_END) / (T_DRAG_END - T_REST_END)))
            else if (t < T_HOLD_END) PATH_HOLD
            else if (t < T_FLICK_END) mix(PATH_HOLD, PATH_END, smooth((t - T_HOLD_END) / (T_FLICK_END - T_HOLD_END)))
            else if (t < T_RADIATE_END) PATH_END
            else mix(PATH_END, PATH_REST, smooth((t - T_RADIATE_END) / (T_RETURN_END - T_RADIATE_END)))
        }
        return m * scale
    }

    fun holdProfile(from: Double, target: Double, tau: Double): Double {
        if (tau <= 0) return from
        if (target > from) return from + (target - from) * smooth(tau / HOLD_RISE)
        val v = from + (target - from) * springAt(tau, RESP_GLIDE.response, RESP_GLIDE.zeta)
        return if (v < 0) 0.0 else if (v > 1) 1.0 else v
    }

    fun holdKAt(t: Double): Double {
        if (t < HOLD_ON) return 0.0
        if (t < HOLD_OFF) return smooth((t - HOLD_ON) / HOLD_RISE)
        return holdProfile(1.0, 0.0, t - HOLD_OFF)
    }

    /** Writes the autoplay state at loop time [time] into [out]: a pure function of time. */
    fun fillTimeline(out: NeonContourMoodLook, time: Double, echoMs: Double, n: Int) {
        val t = wrap(time)
        val scale = (n - 1) / 6.0
        val last = (n - 1).toDouble()
        val core = moodAt(t, scale)
        val lo = max(0.0, core - 1.6)
        val hi = min(last, core + 1.6)
        for (k in 0 until 7) {
            val e = moodAt(max(0.0, t - (k * echoMs) / 1000), scale)
            val echo = if (e < lo) lo else if (e > hi) hi else e
            val nested = clamp(core - k * HOLD_GAP, 0.0, last)
            out.echoes[k] = echo + (nested - echo) * holdKAt(t - k * HOLD_RING_DELAY)
        }
        val speed = abs(core - moodAt(max(0.0, t - 1.0 / 120), scale)) * 120
        val active = smooth((t - T_REST_END) / 0.12) * (1 - smooth((t - (T_RETURN_END - 0.2)) / 0.2))
        out.mood = out.echoes[0]
        out.wake = clamp(speed / 10)
        out.press = active
        out.breath = sin((4 * PI * t) / LOOP) * 1.5 * active
        out.radiate = smooth((t - T_FLICK_END) / 0.2) * (1 - smooth((t - (T_RADIATE_END - 0.2)) / 0.2))
        out.time = t
    }

    fun rubberMood(v: Double, last: Double = 6.0): Double {
        if (v < 0) return (-RUBBER * -v) / (1 - v)
        if (v > last) return last + (RUBBER * (v - last)) / (1 + v - last)
        return v
    }

    fun projectedStop(value: Double, velocity: Double, last: Double = 6.0): Double {
        val nearest = round(clamp(value, 0.0, last))
        val target = clamp(value + velocity * PROJECTION, max(0.0, nearest - MAX_FLICK), min(last, nearest + MAX_FLICK))
        return round(target)
    }

    fun squeezeFor(dy: Double, limit: Double) = limit * tanh(dy / 6 / limit)

    /** Haptic weight by mood: the ends are Medium (2), the middle Soft (0), the rest Light (1). */
    fun hapticWeight(index: Int, n: Int): Int {
        if (index == 0 || index == n - 1) return 2
        if (index.toDouble() == (n - 1) / 2.0) return 0
        return 1
    }

    // ---- labels: one integer, one word, one tag ------------------------------------------------------------------------------

    const val MAXL = 10
    const val L_FROM = 0
    const val L_TO = 1
    const val L_STRIKE = 2
    const val L_WIDTH = 3
    const val L_TAG_IN = 4
    const val L_TAG_IN_Y = 5
    const val L_TAG_OUT = 6
    const val L_TAG_OUT_Y = 7
    const val L_IN_SCALE = 8
    const val L_OUT_SCALE = 9
    const val L_IN_BASE = 10
    const val L_OUT_BASE = 10 + 3 * MAXL
    const val L_SIZE = 10 + 6 * MAXL
    const val MAXEV = 10

    fun newLab() = DoubleArray(L_SIZE)
    fun newEvents() = DoubleArray(MAXEV * 3)

    fun settleLabel(idx: Int, m: Double, last: Int): Int {
        var i = idx
        while (m > i + HYST && i < last) i++
        while (m < i - HYST && i > 0) i--
        return i
    }

    /** Switch times of the autoplay loop as flat (t, from, to) triples, found by running the hysteresis over the loop. */
    fun buildCrossings(n: Int): DoubleArray {
        val scale = (n - 1) / 6.0
        val last = n - 1
        var idx = round(PATH_REST * scale).toInt()
        val out = ArrayList<Double>()
        val steps = (LOOP * 2000).toInt()
        for (pass in 0 until 2) {
            for (i in 0 until steps) {
                val t = i / 2000.0
                val prev = idx
                idx = settleLabel(idx, moodAt(t, scale), last)
                if (pass == 1 && idx != prev) {
                    out.add(t); out.add(prev.toDouble()); out.add(idx.toDouble())
                }
            }
        }
        return out.toDoubleArray()
    }

    fun strikeIn(u: Double): Double {
        if (u <= 0) return 0.0
        if (u < STRIKE_IN_RISE) return smooth(u / STRIKE_IN_RISE)
        val d = (u - STRIKE_DIP_AT) / STRIKE_DIP_DUR
        if (d <= 0 || d >= 1) return 1.0
        val edge = if (d < 0.25) d / 0.25 else if (d > 0.75) (1 - d) / 0.25 else 1.0
        return 1 - (1 - STRIKE_DIP_TO) * edge
    }

    private fun inLevel(d: Double, j: Int): Double {
        val tube = WORD_TUBE * smooth((d - WORD_IN_START) / WORD_IN_DUR)
        return tube + (1 - tube) * strikeIn(d - WORD_STRIKE - j * STRIKE_IN_STEP)
    }

    private fun writeLetters(out: DoubleArray, base: Int, idx: Int, lens: IntArray) {
        val len = lens[idx]
        for (j in 0 until MAXL) {
            val o = base + 3 * j
            out[o] = if (j < len) 1.0 else 0.0
            out[o + 1] = if (j < len) GLOW_ALPHA else 0.0
            out[o + 2] = GLOW_BLUR_TO
        }
    }

    /** Everything lit and still. */
    fun labelSettled(out: DoubleArray, idx: Int, widths: DoubleArray, lens: IntArray) {
        out[L_FROM] = idx.toDouble()
        out[L_TO] = idx.toDouble()
        out[L_STRIKE] = 99.0
        out[L_WIDTH] = widths[idx]
        out[L_TAG_IN] = 1.0
        out[L_TAG_IN_Y] = 0.0
        out[L_TAG_OUT] = 0.0
        out[L_TAG_OUT_Y] = 0.0
        out[L_IN_SCALE] = 1.0
        out[L_OUT_SCALE] = 1.0
        writeLetters(out, L_IN_BASE, idx, lens)
        for (j in 0 until MAXL) {
            val o = L_OUT_BASE + 3 * j
            out[o] = 0.0
            out[o + 1] = 0.0
            out[o + 2] = GLOW_BLUR_TO
        }
    }

    /**
     * The label frame at time [now] from the switch list [ev] (flat t, from, to; [count] of them, oldest first). Each switch runs a
     * one-shot: the old word leaves whole, the new one arrives whole as unlit tubes and is struck left to right, the tag swaps and the
     * capsule springs to the new width. A switch during a one-shot restarts it from what is lit at that moment. [snap] is scratch.
     */
    fun computeLabel(out: DoubleArray, ev: DoubleArray, count: Int, now: Double, idle: Int, widths: DoubleArray, lens: IntArray, snap: DoubleArray) {
        if (count <= 0) {
            labelSettled(out, idle, widths, lens)
            return
        }
        var wFrom = widths[ev[1].toInt()]
        var tagSnap = 1.0
        val len0 = lens[ev[1].toInt()]
        for (j in 0 until MAXL) snap[j] = if (j < len0) 1.0 else 0.0
        for (e in 0 until count) {
            val o = e * 3
            val te = ev[o]
            val fromW = ev[o + 1].toInt()
            val toW = ev[o + 2].toInt()
            val isLast = e == count - 1
            val d = (if (isLast) now else ev[o + 3]) - te
            val lenTo = lens[toW]
            val widthK = springAt(d - TAG_SPRING_START, RESP_TAG.response, RESP_TAG.zeta)
            val tagIn = smooth((d - TAG_IN_START) / TAG_IN_DUR)
            if (!isLast) {
                wFrom += (widths[toW] - wFrom) * widthK
                tagSnap = tagIn
                for (j in 0 until MAXL) snap[j] = if (j < lenTo) inLevel(d, j) else 0.0
                continue
            }
            if (d > LABEL_SETTLE) {
                labelSettled(out, toW, widths, lens)
                return
            }
            out[L_FROM] = fromW.toDouble()
            out[L_TO] = toW.toDouble()
            out[L_STRIKE] = d
            out[L_WIDTH] = wFrom + (widths[toW] - wFrom) * widthK
            val tagOut = smooth((d - TAG_OUT_START) / TAG_OUT_DUR)
            out[L_TAG_IN] = tagIn
            out[L_TAG_IN_Y] = TAG_RISE * (1 - tagIn)
            out[L_TAG_OUT] = tagSnap * (1 - tagOut)
            out[L_TAG_OUT_Y] = -TAG_SLIDE * tagOut
            val arrive = smooth((d - WORD_IN_START) / WORD_IN_DUR)
            val leave = smooth(d / WORD_OUT_DUR)
            out[L_IN_SCALE] = mix(WORD_IN_SCALE, 1.0, arrive)
            out[L_OUT_SCALE] = mix(1.0, WORD_OUT_SCALE, leave)
            val lenFrom = lens[fromW]
            for (j in 0 until MAXL) {
                val u = d - WORD_STRIKE - j * STRIKE_IN_STEP
                val lit = if (j < lenTo) inLevel(d, j) else 0.0
                val struck = if (j < lenTo) strikeIn(u) else 0.0
                val grow = smooth(u / GLOW_GROW)
                val a = L_IN_BASE + 3 * j
                out[a] = lit
                out[a + 1] = GLOW_ALPHA * struck * grow
                out[a + 2] = mix(GLOW_BLUR_FROM, GLOW_BLUR_TO, grow)
                val was = if (j < lenFrom) snap[j] else 0.0
                val b = L_OUT_BASE + 3 * j
                out[b] = was * (1 - leave) * (1 - leave)
                out[b + 1] = GLOW_ALPHA * was * (1 - leave) * (1 + leave)
                out[b + 2] = mix(GLOW_BLUR_TO, WORD_OUT_BLUR, leave)
            }
        }
    }

    fun labelFromTable(out: DoubleArray, table: DoubleArray, t: Double, widths: DoubleArray, lens: IntArray, snap: DoubleArray, idle: Int) {
        val total = table.size / 3
        var count = 0
        while (count < total && table[count * 3] <= t) count++
        computeLabel(out, table, count, t, if (total > 0) table[1].toInt() else idle, widths, lens, snap)
    }

    /** Appends a switch to the live list, dropping switches whose successor is more than 2 s old. Returns the new count. */
    fun pushEvent(ev: DoubleArray, count: Int, t: Double, from: Int, to: Int): Int {
        var c = count
        while (c > 1 && t - ev[3] > 2) {
            for (i in 0 until (c - 1) * 3) ev[i] = ev[i + 3]
            c--
        }
        if (c >= MAXEV) {
            for (i in 0 until (c - 1) * 3) ev[i] = ev[i + 3]
            c--
        }
        ev[c * 3] = t
        ev[c * 3 + 1] = from.toDouble()
        ev[c * 3 + 2] = to.toDouble()
        return c + 1
    }

    fun labLetter(lab: DoubleArray, word: Int, j: Int, field: Int, fallback: Double): Double {
        val jj = if (j < MAXL) j else MAXL - 1
        if (lab[L_TO].toInt() == word) return lab[L_IN_BASE + 3 * jj + field]
        if (lab[L_FROM].toInt() == word) return lab[L_OUT_BASE + 3 * jj + field]
        return fallback
    }

    /** Tag value: level (0), vertical offset (1) or scale (2) of tag [word]. */
    fun labTag(lab: DoubleArray, word: Int, field: Int): Double {
        if (lab[L_TO].toInt() == word) return if (field == 0) lab[L_TAG_IN] else if (field == 1) lab[L_TAG_IN_Y] else 1 - TAG_ZOOM * (1 - lab[L_TAG_IN])
        if (lab[L_FROM].toInt() == word) return if (field == 0) lab[L_TAG_OUT] else if (field == 1) lab[L_TAG_OUT_Y] else 1 - TAG_ZOOM * (1 - lab[L_TAG_OUT])
        return if (field == 2) 1.0 else 0.0
    }

    fun labWord(lab: DoubleArray, word: Int): Double {
        if (lab[L_TO].toInt() == word) return lab[L_IN_SCALE]
        if (lab[L_FROM].toInt() == word) return lab[L_OUT_SCALE]
        return 1.0
    }
}

